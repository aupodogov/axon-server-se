package io.axoniq.axonserver.cluster;

import io.axoniq.axonserver.cluster.election.InMemoryElectionStore;
import io.axoniq.axonserver.cluster.replication.EntryIterator;
import io.axoniq.axonserver.cluster.replication.InMemoryLogEntryStore;
import io.axoniq.axonserver.cluster.replication.LogEntryStore;
import io.axoniq.axonserver.cluster.snapshot.SnapshotManager;
import io.axoniq.axonserver.grpc.cluster.Entry;
import io.axoniq.axonserver.grpc.cluster.Node;
import org.junit.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Sara Pellegrini
 * @since 4.0
 */
public class RaftNodeTest {

    private RaftNode testSubject;
    private FakeScheduler scheduler;


    @Test
    public void rescheduleLogCompaction() {
        SnapshotManager snapshotManager = mock(SnapshotManager.class);
        LogEntryStore logEntryStore = mock(LogEntryStore.class);
        when(logEntryStore.lastLog()).thenReturn(new TermIndex(0,0));
        when(logEntryStore.createIterator(anyLong())).thenReturn(new FakeEntryIterator());
        RaftConfiguration raftConfiguration = mock(RaftConfiguration.class);
        when(raftConfiguration.enableLogCompaction()).thenReturn(true);
        when(raftConfiguration.minElectionTimeout()).thenReturn(150);
        when(raftConfiguration.maxElectionTimeout()).thenReturn(300);
        when(raftConfiguration.groupId()).thenReturn("myGroupId");
        RaftGroup raftGroup = mock(RaftGroup.class);
        when(raftGroup.raftConfiguration()).thenReturn(raftConfiguration);
        when(raftGroup.localLogEntryStore()).thenReturn(logEntryStore);
        when(raftGroup.logEntryProcessor()).thenReturn(new LogEntryProcessor(new InMemoryProcessorStore()));
        when(raftGroup.localElectionStore()).thenReturn(new InMemoryElectionStore());
        FakeScheduler scheduler = new FakeScheduler();
        testSubject = new RaftNode("myNode", raftGroup, scheduler, snapshotManager);
        when(raftGroup.localNode()).thenReturn(testSubject);
        when(raftConfiguration.groupMembers()).thenReturn(asList(Node.newBuilder().setNodeId("myNode").build()));

        testSubject.start();
        scheduler.timeElapses(115, TimeUnit.MINUTES);
        verify(logEntryStore, times(1)).clearOlderThan(anyLong(),any(),any());
        testSubject.restartLogCleaning();
        scheduler.timeElapses(55, TimeUnit.MINUTES);
        verify(logEntryStore, times(1)).clearOlderThan(anyLong(),any(),any());
        testSubject.stop();
    }
}