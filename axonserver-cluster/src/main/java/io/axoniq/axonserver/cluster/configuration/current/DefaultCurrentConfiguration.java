package io.axoniq.axonserver.cluster.configuration.current;

import io.axoniq.axonserver.cluster.CurrentConfiguration;
import io.axoniq.axonserver.cluster.RaftGroup;
import io.axoniq.axonserver.grpc.cluster.Entry;
import io.axoniq.axonserver.grpc.cluster.Node;

import java.util.List;
import java.util.function.Supplier;

/**
 * @author Sara Pellegrini
 * @since
 */
public class DefaultCurrentConfiguration implements CurrentConfiguration {

    private final Supplier<List<Node>> committedMembers;

    private final Iterable<Entry> uncommittedEntries;

    public DefaultCurrentConfiguration(RaftGroup raftGroup) {
        this(() -> raftGroup.raftConfiguration().groupMembers(),
             () -> raftGroup.localLogEntryStore().createIterator(raftGroup.logEntryProcessor().commitIndex() + 1));
    }

    public DefaultCurrentConfiguration(
            Supplier<List<Node>> committedMembers,
            Iterable<Entry> uncommittedEntries) {
        this.committedMembers = committedMembers;
        this.uncommittedEntries = uncommittedEntries;
    }

    @Override
    public List<Node> groupMembers() {
        for (Entry uncommittedEntry : uncommittedEntries) {
            if (uncommittedEntry.hasNewConfiguration()) {
                return uncommittedEntry.getNewConfiguration().getNodesList();
            }
        }
        return committedMembers.get();
    }

    @Override
    public boolean isCommitted() {
        for (Entry uncommittedEntry : uncommittedEntries) {
            if (uncommittedEntry.hasNewConfiguration()) {
                return false;
            }
        }
        return true;
    }

}