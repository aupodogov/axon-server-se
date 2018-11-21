package io.axoniq.axonserver.cluster;

import io.axoniq.axonserver.cluster.election.ElectionStore;
import io.axoniq.axonserver.grpc.cluster.*;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Sara Pellegrini
 * @since 4.0
 */
public abstract class AbstractMembershipState implements MembershipState {

    private final RaftGroup raftGroup;
    private final Consumer<MembershipState> transitionHandler;
    private final MembershipStateFactory stateFactory;
    private final Scheduler scheduler;
    private final BiFunction<Long, Long, Long> randomValueSupplier;

    protected AbstractMembershipState(Builder builder) {
        builder.validate();
        this.raftGroup = builder.raftGroup;
        this.transitionHandler = builder.transitionHandler;
        this.stateFactory = builder.stateFactory;
        this.scheduler = builder.scheduler;
        this.randomValueSupplier = builder.randomValueSupplier;
    }

    protected <R> R handleAsFollower(Function<MembershipState, R> handler) {
        MembershipState followerState = stateFactory().followerState();
        changeStateTo(followerState);
        return handler.apply(followerState);
    }

    protected int clusterSize() {
        return raftGroup().raftConfiguration().groupMembers().size();
    }

    public static abstract class Builder<B extends Builder<B>> {

        private RaftGroup raftGroup;
        private Consumer<MembershipState> transitionHandler;
        private MembershipStateFactory stateFactory;
        private Scheduler scheduler;
        private BiFunction<Long, Long, Long> randomValueSupplier =
                (min, max) -> ThreadLocalRandom.current().nextLong(min, max);

        public B raftGroup(RaftGroup raftGroup) {
            this.raftGroup = raftGroup;
            return self();
        }

        public B transitionHandler(Consumer<MembershipState> transitionHandler) {
            this.transitionHandler = transitionHandler;
            return self();
        }

        public B stateFactory(MembershipStateFactory stateFactory) {
            this.stateFactory = stateFactory;
            return self();
        }

        public B scheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return self();
        }

        public B randomValueSupplier(BiFunction<Long, Long, Long> randomValueSupplier) {
            this.randomValueSupplier = randomValueSupplier;
            return self();
        }

        protected void validate() {
            if (scheduler == null) {
                scheduler = new DefaultScheduler();
            }
            if (raftGroup == null) {
                throw new IllegalStateException("The RAFT group must be provided");
            }
            if (transitionHandler == null) {
                throw new IllegalStateException("The transitionHandler must be provided");
            }
            if (stateFactory == null) {
                throw new IllegalStateException("The stateFactory must be provided");
            }
        }

        @SuppressWarnings("unchecked")
        private final B self() {
            return (B) this;
        }

        abstract MembershipState build();
    }

    protected String votedFor() {
        return raftGroup.localElectionStore().votedFor();
    }

    protected void markVotedFor(String candidateId) {
        raftGroup.localElectionStore().markVotedFor(candidateId);
    }

    protected long lastAppliedIndex() {
        return raftGroup.localLogEntryStore().lastAppliedIndex();
    }

    protected long lastLogTerm() {
        return raftGroup.localLogEntryStore().lastLogTerm();
    }

    protected long lastLogIndex() {
        return raftGroup.localLogEntryStore().lastLogIndex();
    }

    protected long currentTerm() {
        return raftGroup.localElectionStore().currentTerm();
    }

    String me() {
        return raftGroup.localNode().nodeId();
    }

    protected RaftGroup raftGroup() {
        return raftGroup;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public MembershipStateFactory stateFactory() {
        return stateFactory;
    }

    protected long lastAppliedEventSequence() {
        return raftGroup.lastAppliedEventSequence();
    }

    protected void changeStateTo(MembershipState newState) {
        transitionHandler.accept(newState);
    }

    protected void updateCurrentTerm(long term) {
        if (term > currentTerm()) {
            ElectionStore electionStore = raftGroup.localElectionStore();
            electionStore.updateCurrentTerm(term);
            electionStore.markVotedFor(null);
        }
    }

    protected long maxElectionTimeout() {
        return raftGroup.raftConfiguration().maxElectionTimeout();
    }

    protected long minElectionTimeout() {
        return raftGroup.raftConfiguration().minElectionTimeout();
    }

    protected String groupId() {
        return raftGroup().raftConfiguration().groupId();
    }

    protected Stream<RaftPeer> otherNodesStream() {
        return raftGroup.raftConfiguration().groupMembers().stream()
                        .map(Node::getNodeId)
                        .filter(id -> !id.equals(me()))
                        .map(raftGroup::peer);
    }

    protected Iterable<RaftPeer> otherNodes() {
        return otherNodesStream().collect(Collectors.toList());
    }

    protected long otherNodesCount() {
        return otherNodesStream().count();
    }

    protected long random(long min, long max) {
        return randomValueSupplier.apply(min, max);
    }

    protected AppendEntriesResponse appendEntriesFailure() {
        AppendEntryFailure failure = AppendEntryFailure.newBuilder()
                                                       .setLastAppliedIndex(lastAppliedIndex())
                                                       .setLastAppliedEventSequence(lastAppliedEventSequence())
                                                       .build();
        return AppendEntriesResponse.newBuilder()
                                    .setGroupId(groupId())
                                    .setTerm(currentTerm())
                                    .setFailure(failure)
                                    .build();
    }

    protected InstallSnapshotResponse installSnapshotFailure() {
        return InstallSnapshotResponse.newBuilder()
                                      .setGroupId(groupId())
                                      .setTerm(currentTerm())
                                      .setFailure(InstallSnapshotFailure.newBuilder().build())
                                      .build();
    }

    protected RequestVoteResponse requestVoteResponse(boolean voteGranted) {
        return RequestVoteResponse.newBuilder()
                                  .setGroupId(groupId())
                                  .setVoteGranted(voteGranted)
                                  .setTerm(currentTerm())
                                  .build();
    }
}
