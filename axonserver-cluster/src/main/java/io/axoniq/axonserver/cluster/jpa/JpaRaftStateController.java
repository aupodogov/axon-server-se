package io.axoniq.axonserver.cluster.jpa;

import io.axoniq.axonserver.cluster.ProcessorStore;
import io.axoniq.axonserver.cluster.election.ElectionStore;

/**
 * Author: marc
 */
public class JpaRaftStateController implements ElectionStore, ProcessorStore {
    private final String groupId;
    private final JpaRaftStateRepository repository;
    private JpaRaftState raftState;

    public JpaRaftStateController(String groupId, JpaRaftStateRepository repository) {
        this.groupId = groupId;
        this.repository = repository;
    }

    public void init() {
        repository.findById(groupId).ifPresent(state -> this.raftState = state);
        if( raftState == null) {
            this.raftState = repository.save(new JpaRaftState(groupId));
        }
    }
    @Override
    public String votedFor() {
        return raftState.getVotedFor();
    }

    @Override
    public void markVotedFor(String candidate) {
        raftState.setVotedFor(candidate);
        //sync();
    }

    @Override
    public long currentTerm() {
        return raftState.getCurrentTerm();
    }

    @Override
    public void updateCurrentTerm(long term) {
        raftState.setCurrentTerm(term);
        //sync();
    }

    @Override
    public void updateLastApplied(long lastApplied) {
        raftState.setLastApplied(lastApplied);
    }

    @Override
    public void updateCommitIndex(long commitIndex) {
        raftState.setCommitIndex(commitIndex);
    }

    @Override
    public long commitIndex() {
        return raftState.getCommitIndex();
    }

    @Override
    public long lastApplied() {
        return raftState.getLastApplied();
    }

    public void sync() {
        raftState = repository.save(raftState);
    }
}
