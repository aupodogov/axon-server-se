package io.axoniq.axonhub.message.event;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import io.axoniq.axondb.Event;
import io.axoniq.axondb.grpc.Confirmation;
import io.axoniq.axondb.grpc.EventStoreGrpc;
import io.axoniq.axondb.grpc.GetAggregateEventsRequest;
import io.axoniq.axondb.grpc.GetEventsRequest;
import io.axoniq.axondb.grpc.GetFirstTokenRequest;
import io.axoniq.axondb.grpc.GetLastTokenRequest;
import io.axoniq.axondb.grpc.GetTokenAtRequest;
import io.axoniq.axondb.grpc.QueryEventsRequest;
import io.axoniq.axondb.grpc.QueryEventsResponse;
import io.axoniq.axondb.grpc.ReadHighestSequenceNrRequest;
import io.axoniq.axondb.grpc.ReadHighestSequenceNrResponse;
import io.axoniq.axondb.grpc.TrackingToken;
import io.axoniq.axonhub.ClusterEvents;
import io.axoniq.axonhub.cluster.ClusterMetricTarget;
import io.axoniq.axonhub.connector.EventConnector;
import io.axoniq.axonhub.connector.UnitOfWork;
import io.axoniq.axonhub.exception.ErrorCode;
import io.axoniq.axonhub.exception.MessagingPlatformException;
import io.axoniq.axonhub.grpc.ContextProvider;
import io.axoniq.axonhub.grpc.GrpcExceptionBuilder;
import io.axoniq.axonhub.metric.CompositeMetric;
import io.axoniq.axonhub.metric.Metrics;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.codahale.metrics.MetricRegistry.name;
import static io.grpc.stub.ServerCalls.*;

/**
 * Author: marc
 */
@Component("EventDispatcher")
public class EventDispatcher implements BindableService {

    private static final String EVENTS_METRIC_NAME = name(EventDispatcher.class, "events");
    private static final String SNAPSHOTS_METRIC_NAME = name(EventDispatcher.class, "snapshots");
    public static final String NO_EVENT_STORE_CONFIGURED = "No event store configured";
    public static final String ERROR_ON_CONNECTION_FROM_EVENT_STORE = "Error on connection from event store: {}";
    private final Logger logger = LoggerFactory.getLogger(EventDispatcher.class);
    public static final MethodDescriptor<GetEventsRequest, InputStream> METHOD_LIST_EVENTS =
            EventStoreGrpc.METHOD_LIST_EVENTS.toBuilder(
                    ProtoUtils.marshaller(GetEventsRequest.getDefaultInstance()),
                    InputStreamMarshaller.inputStreamMarshaller())
                                             .build();
    public static final MethodDescriptor<GetAggregateEventsRequest, InputStream> METHOD_LIST_AGGREGATE_EVENTS =
            EventStoreGrpc.METHOD_LIST_AGGREGATE_EVENTS.toBuilder(
                    ProtoUtils.marshaller(GetAggregateEventsRequest.getDefaultInstance()),
                    InputStreamMarshaller.inputStreamMarshaller())
                                              .build();


    private final EventStoreManager eventStoreClient;
    private final List<EventConnector> connectors;
    private final ContextProvider contextProvider;
    private final ClusterMetricTarget clusterMetrics;
    private final Map<String, List<EventTrackerInfo>> trackingEventProcessors = new ConcurrentHashMap<>();
    private final Counter eventsCounter;
    private final Counter snapshotCounter;

    public EventDispatcher(EventStoreManager eventStoreClient, Optional<List<EventConnector>> eventConnectors,
                           ContextProvider contextProvider,
                           MetricRegistry metricRegistry, ClusterMetricTarget clusterMetrics) {
        this.contextProvider = contextProvider;
        this.clusterMetrics = clusterMetrics;
        this.eventStoreClient = eventStoreClient;
        connectors = eventConnectors.orElse(Collections.emptyList());
        eventsCounter = metricRegistry.counter(EVENTS_METRIC_NAME);
        snapshotCounter = metricRegistry.counter(SNAPSHOTS_METRIC_NAME);
    }


    public StreamObserver<Event> appendEvent(StreamObserver<Confirmation> responseObserver) {
        String context = contextProvider.getContext();
        EventStore eventStore = eventStoreClient.getEventStore(context);

        if (eventStore == null) {
            responseObserver.onError(new MessagingPlatformException(ErrorCode.NO_EVENTSTORE,
                                                                    NO_EVENT_STORE_CONFIGURED));
            return null;
        }
        StreamObserver<Event> appendEventConnection = eventStore.createAppendEventConnection(context,
                                                                                                         responseObserver);
        return new StreamObserver<Event>() {
            final List<UnitOfWork> unitsOfWork = connectors.stream().map(EventConnector::createUnitOfWork).collect(
                    Collectors.toList());

            @Override
            public void onNext(Event event) {
                appendEventConnection.onNext(event);
                if( ! unitsOfWork.isEmpty()) {
                    unitsOfWork.forEach(c -> c.publish(new GrpcBackedEvent(event)));
                }
                eventsCounter.inc();
            }

            @Override
            public void onError(Throwable throwable) {
                logger.warn("Error on connection from client: {}", throwable.getMessage());
                unitsOfWork.forEach(UnitOfWork::rollback);
                appendEventConnection.onError(GrpcExceptionBuilder.build(throwable));
            }

            @Override
            public void onCompleted() {
                appendEventConnection.onCompleted();
                unitsOfWork.forEach(UnitOfWork::commit);
            }
        };
    }

    public void appendSnapshot(Event request, StreamObserver<Confirmation> responseObserver) {
        checkConnection(responseObserver).ifPresent(eventStore -> {
            String context = contextProvider.getContext();
            snapshotCounter.inc();
            eventStore.appendSnapshot(context, request).whenComplete((c, t) -> {
                if (t != null) {
                    logger.warn(ERROR_ON_CONNECTION_FROM_EVENT_STORE, t.getMessage());
                    responseObserver.onError(GrpcExceptionBuilder.build(t));
                } else {
                    responseObserver.onNext(c);
                    responseObserver.onCompleted();
                }
            });
        });
    }

    public void listAggregateEvents(GetAggregateEventsRequest request, StreamObserver<InputStream> responseObserver) {
        checkConnection(responseObserver).ifPresent(eventStore -> {
            String context = contextProvider.getContext();
            try {
                eventStore.listAggregateEvents(context, request, responseObserver);
            } catch (RuntimeException t) {
                logger.warn(ERROR_ON_CONNECTION_FROM_EVENT_STORE, t.getMessage(), t);
                responseObserver.onError(GrpcExceptionBuilder.build(t));
            }
        });
    }

    public StreamObserver<GetEventsRequest> listEvents(StreamObserver<InputStream> responseObserver) {
        String context = contextProvider.getContext();
        EventStore eventStore = eventStoreClient.getEventStore(context);
        if (eventStore == null) {
            responseObserver.onError(new MessagingPlatformException(ErrorCode.NO_EVENTSTORE,
                                                                    NO_EVENT_STORE_CONFIGURED));
            return null;
        }

        return new GetEventsRequestStreamObserver(responseObserver, eventStore, context);
    }

    @EventListener
    public void on(ClusterEvents.ApplicationDisconnected applicationDisconnected) {
        List<EventTrackerInfo> eventsStreams = trackingEventProcessors.remove(applicationDisconnected.getClient());
        logger.debug("application disconnected: {}, eventsStreams: {}", applicationDisconnected.getClient(), eventsStreams);

        if( eventsStreams != null) {
            eventsStreams.forEach(streamObserver -> {
                try {
                    streamObserver.responseObserver.onCompleted();
                } catch( Exception ex) {
                    logger.debug("Error while closing tracking event processor connection from {} - {}", applicationDisconnected.getClient(), ex.getMessage());
                }
            });
        }
    }


    public long getNrOfEvents() {
        return eventsCounter.getCount() + new CompositeMetric(new Metrics(EVENTS_METRIC_NAME, clusterMetrics)).size();
    }

    public long getNrOfSnapshots() {
        return snapshotCounter.getCount() + new CompositeMetric(new Metrics(SNAPSHOTS_METRIC_NAME, clusterMetrics)).size();
    }

    public Map<String, Iterable<Long>> eventTrackerStatus() {
        Map<String, Iterable<Long>> trackers = new HashMap<>();
        trackingEventProcessors.forEach((client, infos) -> {
            Set<Long> status = infos.stream().map(EventTrackerInfo::getLastToken).collect(Collectors.toSet());
            trackers.put(client, status);
        });
        return trackers;
    }


    @Override
    public final io.grpc.ServerServiceDefinition bindService() {
        return io.grpc.ServerServiceDefinition.builder(EventStoreGrpc.SERVICE_NAME)
                                              .addMethod(
                                                      EventStoreGrpc.METHOD_APPEND_EVENT,
                                                      asyncClientStreamingCall( this::appendEvent))
                                              .addMethod(
                                                      EventStoreGrpc.METHOD_APPEND_SNAPSHOT,
                                                      asyncUnaryCall(
                                                              this::appendSnapshot))
                                              .addMethod(
                                                      METHOD_LIST_AGGREGATE_EVENTS,
                                                      asyncServerStreamingCall(this::listAggregateEvents))
                                              .addMethod(
                                                      METHOD_LIST_EVENTS,
                                                      asyncBidiStreamingCall(this::listEvents))
                                              .addMethod(
                                                      EventStoreGrpc.METHOD_READ_HIGHEST_SEQUENCE_NR,
                                                      asyncUnaryCall(this::readHighestSequenceNr))
                                              .addMethod(
                                                      EventStoreGrpc.METHOD_GET_FIRST_TOKEN,
                                                      asyncUnaryCall(this::getFirstToken))
                                              .addMethod(
                                                      EventStoreGrpc.METHOD_GET_LAST_TOKEN,
                                                      asyncUnaryCall(this::getLastToken))
                                              .addMethod(
                                                      EventStoreGrpc.METHOD_GET_TOKEN_AT,
                                                      asyncUnaryCall(this::getTokenAt))
                                              .addMethod(
                                                      EventStoreGrpc.METHOD_QUERY_EVENTS,
                                                      asyncBidiStreamingCall(this::queryEvents))
                                              .build();
    }

    private void getFirstToken(GetFirstTokenRequest request, StreamObserver<TrackingToken> responseObserver) {
        checkConnection(responseObserver).ifPresent(client ->
            client.getFirstToken(contextProvider.getContext(), request, new ForwardingStreamObserver<>(responseObserver))
        );
    }

    private Optional<EventStore> checkConnection(StreamObserver<?> responseObserver) {
        EventStore eventStore = eventStoreClient.getEventStore(contextProvider.getContext());
        if (eventStore == null) {
            responseObserver.onError(new MessagingPlatformException(ErrorCode.NO_EVENTSTORE,
                                                                    NO_EVENT_STORE_CONFIGURED));
            return Optional.empty();
        }
        return Optional.of(eventStore);
    }

    private void getLastToken(GetLastTokenRequest request, StreamObserver<TrackingToken> responseObserver) {
        checkConnection(responseObserver).ifPresent(client ->
                                                            client.getLastToken(contextProvider.getContext(), request, new ForwardingStreamObserver<>(responseObserver))
        );
    }

    private void getTokenAt(GetTokenAtRequest request, StreamObserver<TrackingToken> responseObserver) {
        checkConnection(responseObserver).ifPresent(client ->
                                                            client.getTokenAt(contextProvider.getContext(), request, new ForwardingStreamObserver<>(responseObserver))
        );
    }

    private void readHighestSequenceNr(ReadHighestSequenceNrRequest request,
                                       StreamObserver<ReadHighestSequenceNrResponse> responseObserver) {
        checkConnection(responseObserver).ifPresent(client ->
                                                            client.readHighestSequenceNr(contextProvider.getContext(), request, new ForwardingStreamObserver<>(responseObserver))
        );
    }


    private StreamObserver<QueryEventsRequest> queryEvents(StreamObserver<QueryEventsResponse> responseObserver) {
        return checkConnection(responseObserver).map(client -> client.queryEvents(contextProvider.getContext(), responseObserver)).orElse(null);
    }


    private static class EventTrackerInfo {
        private final StreamObserver<InputStream> responseObserver;
        private final String client;
        private final AtomicLong lastToken;

        public EventTrackerInfo(StreamObserver<InputStream> responseObserver, String client, long lastToken) {
            this.responseObserver = responseObserver;
            this.client = client;
            this.lastToken = new AtomicLong(lastToken);
        }

        public StreamObserver<InputStream> getResponseObserver() {
            return responseObserver;
        }

        public String getClient() {
            return client;
        }

        public long getLastToken() {
            return lastToken.get();
        }

        public void incrementLastToken() {
            lastToken.incrementAndGet();
        }
    }

    private class ForwardingStreamObserver<T> implements StreamObserver<T> {

        private final StreamObserver<T> responseObserver;

        public ForwardingStreamObserver(
                StreamObserver<T> responseObserver) {
            this.responseObserver = responseObserver;
        }

        @Override
        public void onNext(T t) {
            responseObserver.onNext(t);
        }

        @Override
        public void onError(Throwable cause) {
            logger.warn(ERROR_ON_CONNECTION_FROM_EVENT_STORE, cause.getMessage());
            responseObserver.onError(GrpcExceptionBuilder.build(cause));
        }

        @Override
        public void onCompleted() {
            responseObserver.onCompleted();
        }
    }

    private class GetEventsRequestStreamObserver implements StreamObserver<GetEventsRequest> {

        private final StreamObserver<InputStream> responseObserver;
        private final EventStore eventStore;
        private final String context;
        volatile StreamObserver<GetEventsRequest> eventStoreRequestObserver;
        volatile EventTrackerInfo trackerInfo;

        public GetEventsRequestStreamObserver(StreamObserver<InputStream> responseObserver, EventStore eventStore,
                                              String context) {
            this.responseObserver = responseObserver;
            this.eventStore = eventStore;
            this.context = context;
        }

        @Override
        public void onNext(GetEventsRequest getEventsRequest) {
            if (!registerEventTracker(getEventsRequest)) {
                return;
            }

            try {
                eventStoreRequestObserver.onNext(getEventsRequest);
            } catch (Exception reason ) {
                logger.warn("Error on connection sending event to client: {}", reason.getMessage());
                if( eventStoreRequestObserver != null ) eventStoreRequestObserver.onCompleted();
                removeTrackerInfo();
            }
        }

        private boolean registerEventTracker(GetEventsRequest getEventsRequest) {
            if( eventStoreRequestObserver == null) {
                trackerInfo = new EventTrackerInfo(responseObserver, getEventsRequest.getClient(), getEventsRequest.getTrackingToken()-1);
                try {
                    eventStoreRequestObserver =
                            eventStore.listEvents(context, new StreamObserver<InputStream>() {
                                @Override
                                public void onNext(InputStream eventWithToken) {
                                    responseObserver.onNext(eventWithToken);
                                    trackerInfo.incrementLastToken();
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    logger.warn(ERROR_ON_CONNECTION_FROM_EVENT_STORE,
                                                throwable.getMessage());
                                    try {
                                        responseObserver.onError(GrpcExceptionBuilder.build(throwable));
                                    } catch( RuntimeException ex) {
                                        logger.info("Failed to send error: {}", ex.getMessage());
                                    }
                                    removeTrackerInfo();
                                }

                                @Override
                                public void onCompleted() {
                                    try {
                                        responseObserver.onCompleted();
                                    } catch (RuntimeException ignored) {
                                        // ignore error on confirming half close
                                    }
                                }
                            });
                } catch (RuntimeException cause) {
                    responseObserver.onError(GrpcExceptionBuilder.build(cause));
                    return false;
                }

                trackingEventProcessors.computeIfAbsent(trackerInfo.client, key -> new CopyOnWriteArrayList<>()).add(trackerInfo);
                logger.info("Starting tracking event processor for {}:{} - {}",
                            getEventsRequest.getClient(),
                            getEventsRequest.getComponent(),
                            getEventsRequest.getTrackingToken());
            }
            return true;
        }

        @Override
        public void onError(Throwable reason) {
            logger.warn("Error on connection from client: {}", reason.getMessage());
            if( eventStoreRequestObserver != null ) eventStoreRequestObserver.onCompleted();
            removeTrackerInfo();
        }

        private void removeTrackerInfo() {
            if (trackerInfo != null) {
                trackingEventProcessors.computeIfPresent(trackerInfo.client, (c,streams) -> {
                    logger.debug("{}: {} streams", trackerInfo.client, streams.size());
                    streams.remove(trackerInfo);
                    return streams.isEmpty() ? null : streams;
                });
            }
        }

        @Override
        public void onCompleted() {
            if( eventStoreRequestObserver != null ) eventStoreRequestObserver.onCompleted();
            removeTrackerInfo();
            responseObserver.onCompleted();
        }
    }
}
