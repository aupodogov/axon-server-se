package io.axoniq.axonserver.cluster.grpc;

import io.axoniq.axonserver.cluster.RaftNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Author: marc
 */
public class FakeRaftGroupManager implements RaftGroupManager {

    private final Map<String,RaftNode> raftNodes = new HashMap<>();

    public FakeRaftGroupManager(RaftNode raftNode) {
        raftNodes.put(raftNode.groupId(), raftNode);
    }

    @Override
    public RaftNode raftNode(String groupId) {
        return raftNodes.get(groupId);
    }
}