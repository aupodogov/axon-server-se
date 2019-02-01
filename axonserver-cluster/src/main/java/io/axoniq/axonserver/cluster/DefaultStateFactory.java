package io.axoniq.axonserver.cluster;

import io.axoniq.axonserver.cluster.configuration.current.CachedCurrentConfiguration;
import io.axoniq.axonserver.cluster.scheduler.DefaultScheduler;
import io.axoniq.axonserver.cluster.scheduler.Scheduler;
import io.axoniq.axonserver.cluster.snapshot.SnapshotManager;
import io.axoniq.axonserver.grpc.cluster.Node;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Sara Pellegrini
 * @since 4.0
 */
public class DefaultStateFactory implements MembershipStateFactory {

    private final RaftGroup raftGroup;
    private final StateTransitionHandler transitionHandler;
    private final BiConsumer<Long, String> termUpdateHandler;
    private final Supplier<Scheduler> schedulerFactory;
    private final SnapshotManager snapshotManager;
    private final CurrentConfiguration currentConfiguration;
    private final Function<Consumer<List<Node>>, Registration> registerConfigurationListener;


    public DefaultStateFactory(RaftGroup raftGroup,
                               StateTransitionHandler transitionHandler,
                               BiConsumer<Long, String> termUpdateHandler,
                               SnapshotManager snapshotManager) {
        this.raftGroup = raftGroup;
        this.transitionHandler = transitionHandler;
        this.termUpdateHandler = termUpdateHandler;
        this.snapshotManager = snapshotManager;
        this.schedulerFactory = DefaultScheduler::new;
        CachedCurrentConfiguration configuration = new CachedCurrentConfiguration(raftGroup);
        this.currentConfiguration = configuration;
        this.registerConfigurationListener = configuration::registerChangeListener;

    }

    private MembershipStateFactory stateFactory(){
        MembershipStateFactory stateFactory = raftGroup.localNode().stateFactory();
        return stateFactory != null ? stateFactory : this;
    }

    @Override
    public IdleState idleState(String nodeId) {
        return new IdleState(nodeId);
    }

    @Override
    public LeaderState leaderState() {
        return LeaderState.builder()
                          .raftGroup(raftGroup)
                          .schedulerFactory(schedulerFactory)
                          .transitionHandler(transitionHandler)
                          .termUpdateHandler(termUpdateHandler)
                          .snapshotManager(snapshotManager)
                          .currentConfiguration(currentConfiguration)
                          .registerConfigurationListenerFn(registerConfigurationListener)
                          .stateFactory(stateFactory())
                          .build();
    }

    @Override
    public FollowerState followerState() {
        return FollowerState.builder()
                            .raftGroup(raftGroup)
                            .schedulerFactory(schedulerFactory)
                            .transitionHandler(transitionHandler)
                            .termUpdateHandler(termUpdateHandler)
                            .snapshotManager(snapshotManager)
                            .currentConfiguration(currentConfiguration)
                            .registerConfigurationListenerFn(registerConfigurationListener)
                            .stateFactory(stateFactory())
                            .build();
    }

    @Override
    public CandidateState candidateState() {
        return CandidateState.builder()
                             .raftGroup(raftGroup)
                             .schedulerFactory(schedulerFactory)
                             .transitionHandler(transitionHandler)
                             .termUpdateHandler(termUpdateHandler)
                             .snapshotManager(snapshotManager)
                             .currentConfiguration(currentConfiguration)
                             .registerConfigurationListenerFn(registerConfigurationListener)
                             .stateFactory(stateFactory())
                             .build();
    }
}