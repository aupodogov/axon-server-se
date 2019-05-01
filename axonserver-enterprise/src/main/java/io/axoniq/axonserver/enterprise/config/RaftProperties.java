package io.axoniq.axonserver.enterprise.config;


import io.axoniq.axonserver.cluster.replication.file.StorageProperties;
import io.axoniq.axonserver.config.SystemInfoProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author Marc Gathier
 * @since 4.1
 */
@ConfigurationProperties(prefix = "axoniq.axonserver.replication")
@Configuration
public class RaftProperties extends StorageProperties {
    private int minElectionTimeout = 1000;
    private int maxElectionTimeout = 2500;
    private int heartbeatTimeout = 100;
    /**
     * Maximum number of append entry messages sent to one peer before moving to the next.
     */
    private int maxEntriesPerBatch = 10;

    /**
     * Number of unconfirmed append entry messages that may be sent to peer.
     */
    private int flowBuffer = 1000;
    /**
     * Number of unconfirmed install snapshot messages that may be sent to peer.
     */
    private int snapshotFlowBuffer = 500;
    /**
     * Maximum number of objects that can be sent in a single install snapshot message.
     */
    private int maxSnapshotChunksPerBatch = 1000;

    private final SystemInfoProvider systemInfoProvider;
    private int maxReplicationRound = 10;
    private boolean logCompactionEnabled = true;
    private int logRetentionHours = 1;

    public RaftProperties(SystemInfoProvider systemInfoProvider) {
        this.systemInfoProvider = systemInfoProvider;
    }

    public int getMinElectionTimeout() {
        return minElectionTimeout;
    }

    public void setMinElectionTimeout(int minElectionTimeout) {
        this.minElectionTimeout = minElectionTimeout;
    }

    public int getMaxElectionTimeout() {
        return maxElectionTimeout;
    }

    public void setMaxElectionTimeout(int maxElectionTimeout) {
        this.maxElectionTimeout = maxElectionTimeout;
    }

    public int getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    public void setHeartbeatTimeout(int heartbeatTimeout) {
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public int getMaxEntriesPerBatch() {
        return maxEntriesPerBatch;
    }

    public void setMaxEntriesPerBatch(int maxEntriesPerBatch) {
        this.maxEntriesPerBatch = maxEntriesPerBatch;
    }

    public int getFlowBuffer() {
        return flowBuffer;
    }

    public void setFlowBuffer(int flowBuffer) {
        this.flowBuffer = flowBuffer;
    }

    @Override
    public boolean isCleanerHackEnabled() {
        return systemInfoProvider.javaOnWindows() && ! systemInfoProvider.javaWithModules();
    }

    @Override
    public boolean isUseMmapIndex() {
        return ! (systemInfoProvider.javaOnWindows() && systemInfoProvider.javaWithModules());
    }

    public int getMaxReplicationRound() {
        return maxReplicationRound;
    }

    public void setMaxReplicationRound(int maxReplicationRound) {
        this.maxReplicationRound = maxReplicationRound;
    }

    public boolean isLogCompactionEnabled() {
        return logCompactionEnabled;
    }

    public void setLogCompactionEnabled(boolean logCompactionEnabled) {
        this.logCompactionEnabled = logCompactionEnabled;
    }

    public int getLogRetentionHours() {
        return logRetentionHours;
    }

    public void setLogRetentionHours(int logRetentionHours) {
        this.logRetentionHours = Math.max(logRetentionHours, 1);
    }

    /**
     * Returns the maximum number of serializedObjects to be sent in a single InstallSnapshotRequest.
     * @return maximum number of serializedObjects sent in a single InstallSnapshotRequest
     */
    public int getMaxSnapshotChunksPerBatch() {
        return maxSnapshotChunksPerBatch;
    }

    /**
     * Sets the maximum number of serializedObjects to be sent in a single InstallSnapshotRequest.
     * @param maxSnapshotChunksPerBatch the maximum number of serializedObjects
     */
    public void setMaxSnapshotChunksPerBatch(int maxSnapshotChunksPerBatch) {
        this.maxSnapshotChunksPerBatch = maxSnapshotChunksPerBatch;
    }

    public int getSnapshotFlowBuffer() {
        return snapshotFlowBuffer;
    }

    public void setSnapshotFlowBuffer(int snapshotFlowBuffer) {
        this.snapshotFlowBuffer = snapshotFlowBuffer;
    }
}
