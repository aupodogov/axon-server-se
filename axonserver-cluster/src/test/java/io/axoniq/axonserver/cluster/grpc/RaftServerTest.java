package io.axoniq.axonserver.cluster.grpc;

import io.axoniq.axonserver.cluster.RaftConfiguration;
import io.axoniq.axonserver.cluster.RaftGroup;
import io.axoniq.axonserver.cluster.RaftNode;
import io.axoniq.axonserver.cluster.RaftPeer;
import io.axoniq.axonserver.cluster.RaftServerConfiguration;
import io.axoniq.axonserver.cluster.election.ElectionStore;
import io.axoniq.axonserver.cluster.election.InMemoryElectionStore;
import io.axoniq.axonserver.cluster.replication.InMemoryLogEntryStore;
import io.axoniq.axonserver.cluster.replication.LogEntryStore;
import io.axoniq.axonserver.grpc.cluster.Node;
import org.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.axoniq.axonserver.cluster.TestUtils.assertWithin;
import static junit.framework.TestCase.assertNotNull;

/**
 * Author: marc
 */
public class RaftServerTest {

    Map<String, RaftNode> clusterNodes = new HashMap<>();
    List<Node> nodes = Arrays.asList(node("node1", "localhost", 7777),
//                                     node("node2", "localhost", 7778),
//                                     node("node3", "localhost", 7779),
                                     node("node4", "localhost", 7780),
                                     node("node5", "localhost", 7781));

    List<RaftServer> raftServers = new ArrayList<>();


    @Before
    public void setUp() throws Exception {

        nodes.forEach(node -> {
            RaftGroup raftGroup = new GrpcRaftGroup(nodes, node.getNodeId());
            clusterNodes.put(node.getNodeId(), new RaftNode(node.getNodeId(), raftGroup));
        });

        nodes.forEach(node -> {
            RaftNode raftNode = clusterNodes.get(node.getNodeId());
            LeaderElectionService leaderElectionService = new LeaderElectionService();
            leaderElectionService.addRaftNode(raftNode);

            LogReplicationService logReplicationService = new LogReplicationService();
            logReplicationService.addRaftNode(raftNode);


            RaftServerConfiguration configuration = new RaftServerConfiguration();
            configuration.setInternalPort(node.getPort());
            RaftServer raftServer = new RaftServer(configuration, leaderElectionService, logReplicationService);
            raftServers.add(raftServer);
        });


    }

    private Node node(String id, String hostname, int port) {
        return Node.newBuilder().setNodeId(id).setHost(hostname).setPort(port).build();
    }

    @Test
    public void start() throws InterruptedException, TimeoutException, ExecutionException {
        raftServers.forEach(r -> r.start());
        clusterNodes.forEach((id, node) -> node.start());

        try {

            assertWithin(5, TimeUnit.SECONDS, () -> {
                RaftNode leader = clusterNodes.values().stream().filter(n -> n.isLeader()).findFirst().orElse(null);
                assertNotNull(leader);
            });

            RaftNode leader = clusterNodes.values().stream().filter(n -> n.isLeader()).findFirst().orElse(null);


            CompletableFuture<Void>[] futures = new CompletableFuture[2500];
            Thread.currentThread().setPriority(5);
            AtomicInteger successCount = new AtomicInteger();
                long before = System.currentTimeMillis();
                for (int i = 0; i < futures.length; i++) {
                    futures[i] = leader.appendEntry("Test", "Test".getBytes()).exceptionally(e -> null)
                                       .thenAccept(v -> {
                                           successCount.incrementAndGet();
                                       });
                }
                CompletableFuture.allOf(futures).get(60, TimeUnit.SECONDS);
                long after = System.currentTimeMillis();
                System.out.println("Applied on leader within " + (after - before) + "ms.");
                Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        } finally {
            clusterNodes.forEach((id, node) -> node.stop());
            raftServers.forEach(r -> r.stop(() -> {}));
        }
    }

    private class GrpcRaftGroup implements RaftGroup, RaftConfiguration {

        final LogEntryStore logEntryStore;
        final ElectionStore electionStore;
        final Map<String,RaftPeer> raftPeerMap = new HashMap<>();
        private final String localNode;

        private GrpcRaftGroup(List<Node> nodes, String localNode) {
            this.localNode = localNode;
            logEntryStore = new InMemoryLogEntryStore();
            electionStore = new InMemoryElectionStore();
            initializePeers(nodes);
        }

        private void initializePeers(List<Node> nodes) {
            raftPeerMap.clear();
            nodes.forEach(node -> raftPeerMap.put(node.getNodeId(), new GrpcRaftPeer(node)));
        }

        @Override
        public LogEntryStore localLogEntryStore() {
            return logEntryStore;
        }

        @Override
        public ElectionStore localElectionStore() {
            return electionStore;
        }

        @Override
        public RaftConfiguration raftConfiguration() {
            return this;
        }

        @Override
        public RaftPeer peer(String nodeId) {
            return raftPeerMap.get(nodeId);
        }

        @Override
        public RaftNode localNode() {
            return clusterNodes.get(localNode);
        }

        @Override
        public List<Node> groupMembers() {
            return nodes;
        }

        @Override
        public String groupId() {
            return "MyGroupId";
        }

//        @Override
//        public int minElectionTimeout() {
//            return 1000;
//        }
//
//        @Override
//        public int maxElectionTimeout() {
//            return 3000;
//        }
////
//        @Override
//        public int heartbeatTimeout() {
//            return 100;
//        }

        @Override
        public void update(List<Node> nodes) {
            initializePeers(nodes);
        }
    }
}