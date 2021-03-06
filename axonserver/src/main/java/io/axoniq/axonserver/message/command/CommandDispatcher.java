/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.message.command;

import io.axoniq.axonserver.applicationevents.TopologyEvents;
import io.axoniq.axonserver.exception.ErrorCode;
import io.axoniq.axonserver.exception.ErrorMessageFactory;
import io.axoniq.axonserver.exception.MessagingPlatformException;
import io.axoniq.axonserver.grpc.SerializedCommand;
import io.axoniq.axonserver.grpc.SerializedCommandResponse;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import io.axoniq.axonserver.message.ClientStreamIdentification;
import io.axoniq.axonserver.message.FlowControlQueues;
import io.axoniq.axonserver.metric.BaseMetricName;
import io.axoniq.axonserver.metric.MeterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Responsible for managing command subscriptions and processing commands.
 * Subscriptions are stored in the {@link CommandRegistrationCache}.
 * Running commands are stored in the {@link CommandCache}.
 *
 * @author Marc Gathier
 */
@Component("CommandDispatcher")
public class CommandDispatcher {

    private final CommandRegistrationCache registrations;
    private final CommandCache commandCache;
    private final CommandMetricsRegistry metricRegistry;
    private final Logger logger = LoggerFactory.getLogger(CommandDispatcher.class);
    private final FlowControlQueues<WrappedCommand> commandQueues;
    private final Map<String, MeterFactory.RateMeter> commandRatePerContext = new ConcurrentHashMap<>();

    public CommandDispatcher(CommandRegistrationCache registrations, CommandCache commandCache,
                             CommandMetricsRegistry metricRegistry,
                             MeterFactory meterFactory,
                             @Value("${axoniq.axonserver.command-queue-capacity-per-client:10000}") int queueCapacity) {
        this.registrations = registrations;
        this.commandCache = commandCache;
        this.metricRegistry = metricRegistry;
        commandQueues = new FlowControlQueues<>(Comparator.comparing(WrappedCommand::priority).reversed(),
                                                queueCapacity,
                                                BaseMetricName.AXON_APPLICATION_COMMAND_QUEUE_SIZE,
                                                meterFactory,
                                                ErrorCode.COMMAND_DISPATCH_ERROR);
        metricRegistry.gauge(BaseMetricName.AXON_ACTIVE_COMMANDS, commandCache, ConcurrentHashMap::size);
    }


    public void dispatch(String context, SerializedCommand request,
                         Consumer<SerializedCommandResponse> responseObserver, boolean proxied) {
        if (proxied) {
            String clientStreamId = request.getClientStreamId();
            ClientStreamIdentification clientIdentification = new ClientStreamIdentification(context, clientStreamId);
            CommandHandler<?> handler = registrations.findByClientAndCommand(clientIdentification,
                                                                             request.getCommand());
            dispatchToCommandHandler(request, handler, responseObserver,
                                     ErrorCode.CLIENT_DISCONNECTED,
                                     String.format("Client %s not found while processing: %s"
                                             , clientStreamId, request.getCommand()));
        } else {
            commandRate(context).mark();
            CommandHandler<?> commandHandler = registrations.getHandlerForCommand(context,
                                                                                  request.wrapped(),
                                                                                  request.getRoutingKey());
            dispatchToCommandHandler(request, commandHandler, responseObserver,
                                     ErrorCode.NO_HANDLER_FOR_COMMAND,
                                     "No Handler for command: " + request.getCommand()
            );
        }
    }

    public MeterFactory.RateMeter commandRate(String context) {
        return commandRatePerContext.computeIfAbsent(context,
                                                     c -> metricRegistry
                                                             .rateMeter(c, BaseMetricName.AXON_COMMAND_RATE));
    }

    @EventListener
    public void on(TopologyEvents.CommandHandlerDisconnected event) {
        handleDisconnection(event.clientIdentification(), event.isProxied());
    }

    private void handleDisconnection(ClientStreamIdentification client, boolean proxied) {
        cleanupRegistrations(client);
        if (!proxied) {
            getCommandQueues().move(client.toString(), this::redispatch);
        }
        handlePendingCommands(client);
    }

    private void dispatchToCommandHandler(SerializedCommand command, CommandHandler<?> commandHandler,
                                          Consumer<SerializedCommandResponse> responseObserver,
                                          ErrorCode noHandlerErrorCode, String noHandlerMessage) {
        if (commandHandler == null) {
            logger.warn("No Handler for command: {}", command.getName());
            responseObserver.accept(new SerializedCommandResponse(CommandResponse.newBuilder()
                                                                                 .setMessageIdentifier(command.getMessageIdentifier())
                                                                                 .setRequestIdentifier(command.getMessageIdentifier())
                                                                                 .setErrorCode(noHandlerErrorCode
                                                                                                       .getCode())
                                                                                 .setErrorMessage(ErrorMessageFactory
                                                                                                          .build(noHandlerMessage))
                                                                                 .build()));
            return;
        }

        try {
            logger.debug("Dispatch {} to: {}", command.getName(), commandHandler.getClientStreamIdentification());
            CommandInformation commandInformation = new CommandInformation(command.getName(),
                                                                                    command.wrapped().getClientId(),
                                                                       commandHandler.getClientId(),
                                                                                    responseObserver,
                                                                       commandHandler.getClientStreamIdentification(),
                                                                       commandHandler.getComponentName());
        commandCache.put(command.getMessageIdentifier(), commandInformation);
        WrappedCommand wrappedCommand = new WrappedCommand(commandHandler.getClientStreamIdentification(),
                                                           commandHandler.getClientId(),
                                                           command);
            commandQueues.put(commandHandler.queueName(), wrappedCommand, wrappedCommand.priority());
        } catch (InsufficientCacheCapacityException insufficientCacheCapacityException) {
            responseObserver.accept(new SerializedCommandResponse(CommandResponse.newBuilder()
                                                                                 .setMessageIdentifier(command.getMessageIdentifier())
                                                                                 .setRequestIdentifier(command.getMessageIdentifier())
                                                                                 .setErrorCode(ErrorCode.COMMAND_DISPATCH_ERROR
                                                                                                       .getCode())
                                                                                 .setErrorMessage(ErrorMessageFactory
                                                                                                          .build(insufficientCacheCapacityException
                                                                                                                         .getMessage()))
                                                                                 .build()));
        } catch (MessagingPlatformException mpe) {
            commandCache.remove(command.getMessageIdentifier());
            responseObserver.accept(new SerializedCommandResponse(CommandResponse.newBuilder()
                                                                                 .setMessageIdentifier(command.getMessageIdentifier())
                                                                                 .setRequestIdentifier(command.getMessageIdentifier())
                                                                                 .setErrorCode(mpe.getErrorCode()
                                                                                                  .getCode())
                                                                                 .setErrorMessage(ErrorMessageFactory
                                                                                                          .build(mpe.getMessage()))
                                                                                 .build()));
        }
    }


    public void handleResponse(SerializedCommandResponse commandResponse, boolean proxied) {
        CommandInformation toPublisher = commandCache.remove(commandResponse.getRequestIdentifier());
        if (toPublisher != null) {
            logger.debug("Sending response to: {}", toPublisher);
            if (!proxied) {
                metricRegistry.add(toPublisher.getRequestIdentifier(),
                                   toPublisher.getSourceClientId(),
                                   toPublisher.getTargetClientId(),
                                   toPublisher.getClientStreamIdentification().getContext(),
                                   System.currentTimeMillis() - toPublisher.getTimestamp());
            }
            toPublisher.getResponseConsumer().accept(commandResponse);
        } else {
            logger.info("Could not find command request: {}", commandResponse.getRequestIdentifier());
        }
    }

    private void cleanupRegistrations(ClientStreamIdentification client) {
        registrations.remove(client);
    }

    public FlowControlQueues<WrappedCommand> getCommandQueues() {
        return commandQueues;
    }

    private String redispatch(WrappedCommand command) {
        SerializedCommand request = command.command();
        CommandInformation commandInformation = commandCache.remove(request.getMessageIdentifier());
        if (commandInformation == null) {
            return null;
        }

        CommandHandler<?> client = registrations.getHandlerForCommand(command.client().getContext(), request.wrapped(),
                                                                      request.getRoutingKey());
        if (client == null) {
            commandInformation.getResponseConsumer().accept(new SerializedCommandResponse(CommandResponse.newBuilder()
                                                                                                         .setMessageIdentifier(
                                                                                                                 request.getMessageIdentifier())
                                                                                                         .setRequestIdentifier(
                                                                                                                 request.getMessageIdentifier())
                                                                                                         .setErrorCode(
                                                                                                                 ErrorCode.NO_HANDLER_FOR_COMMAND
                                                                                                                         .getCode())
                                                                                                         .setErrorMessage(
                                                                                                                 ErrorMessageFactory
                                                                                                                         .build("No Handler for command: "
                                                                                                                                        + request
                                                                                                                                 .getName()))
                                                                                                         .build()));
            return null;
        }

        logger.debug("Dispatch {} to: {}", request.getName(), client.getClientStreamIdentification());

        commandCache.put(request.getMessageIdentifier(), new CommandInformation(request.getName(),
                                                                                request.wrapped().getClientId(),
                                                                                client.getClientId(),
                                                                                commandInformation
                                                                                        .getResponseConsumer(),
                                                                                client.getClientStreamIdentification(),
                                                                                client.getComponentName()));
        return client.queueName();
    }

    private void handlePendingCommands(ClientStreamIdentification client) {
        List<String> messageIds = commandCache.entrySet().stream().filter(e -> e.getValue().checkClient(client))
                                              .map(Map.Entry::getKey).collect(Collectors.toList());

        messageIds.forEach(m -> {
            CommandInformation ci = commandCache.remove(m);
            if (ci != null) {
                ci.getResponseConsumer().accept(new SerializedCommandResponse(CommandResponse.newBuilder()
                                                                                             .setMessageIdentifier(m)
                                                                                             .setRequestIdentifier(m)
                                                                                             .setErrorMessage(
                                                                                                     ErrorMessageFactory
                                                                                                             .build("Connection lost while executing command on: "
                                                                                                                            + ci
                                                                                                                     .getClientStreamIdentification()))
                                                                                             .setErrorCode(ErrorCode.CONNECTION_TO_HANDLER_LOST
                                                                                                                   .getCode())
                                                                                             .build()));
            }
        });
    }

    public int activeCommandCount() {
        return commandCache.size();
    }
}
