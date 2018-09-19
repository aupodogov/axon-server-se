package io.axoniq.axonserver.message.command;

import io.axoniq.axonserver.ProcessingInstructionHelper;
import io.axoniq.axonserver.grpc.command.Command;

/**
 * Author: marc
 */
public class WrappedCommand {
    private final String context;
    private final Command command;
    private final long priority;

    public WrappedCommand(String context, Command command) {
        this.context = context;
        this.command = command;
        this.priority = ProcessingInstructionHelper.priority(command.getProcessingInstructionsList());
    }

    public Command command() {
        return command;
    }

    public String context() {
        return context;
    }

    public long priority() {
        return priority;
    }

}
