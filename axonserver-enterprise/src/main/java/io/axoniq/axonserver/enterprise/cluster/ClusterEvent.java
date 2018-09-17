package io.axoniq.axonserver.enterprise.cluster;

import io.axoniq.axonserver.enterprise.jpa.ClusterNode;

/**
 * Author: marc
 */
public class ClusterEvent {

    public enum EventType {
        NODE_ADDED,
        NODE_DELETED
    }

    private final EventType eventType;
    private final ClusterNode clusterNode;

    public ClusterEvent(EventType eventType, ClusterNode clusterNode) {
        this.eventType = eventType;
        this.clusterNode = clusterNode;
    }

    public EventType getEventType() {
        return eventType;
    }

    public ClusterNode getClusterNode() {
        return clusterNode;
    }
}