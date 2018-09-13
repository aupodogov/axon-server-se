package io.axoniq.axonserver.enterprise.cluster;

import io.axoniq.axonserver.enterprise.cluster.internal.ProxyCommandHandler;
import io.axoniq.axonserver.message.command.CommandRegistrationCache;
import io.axoniq.axonserver.message.command.DirectCommandHandler;
import io.axoniq.axonserver.message.query.QueryHandlerSelector;
import io.axoniq.axonserver.message.query.QueryRegistrationCache;
import io.axoniq.axonserver.topology.Topology;
import io.axoniq.axonserver.util.CountingStreamObserver;
import org.junit.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static io.axoniq.axonserver.enterprise.cluster.NodeSelectionStrategy.ME;
import static org.junit.Assert.*;

/**
 * Author: marc
 */
public class SubscriptionCountBasedNodeSelectionStrategyTest {
    private SubscriptionCountBasedNodeSelectionStrategy testSubject;
    private CommandRegistrationCache commandRegistry;
    @Before
    public void setUp() {
        QueryHandlerSelector queryHandlerSelector= (queryDefinition, componentName, queryHandlers) -> null;
        QueryRegistrationCache queryRegistry = new QueryRegistrationCache(queryHandlerSelector);
        commandRegistry = new CommandRegistrationCache();
        testSubject = new SubscriptionCountBasedNodeSelectionStrategy(commandRegistry, queryRegistry);
    }

    @Test
    public void selectNodeNoSubscriptions() {
        Collection<String> activeNodes = Collections.emptyList();
        assertEquals(NodeSelectionStrategy.ME, testSubject.selectNode("client1", "component1", activeNodes));
    }

    @Test
    public void selectNodeWithComponent() {
        Collection<String> activeNodes = Arrays.asList(ME, "server1");
        commandRegistry.add(Topology.DEFAULT_CONTEXT, "command1",
                            new DirectCommandHandler(new CountingStreamObserver<>(), "client1", "component1"));
        commandRegistry.add(Topology.DEFAULT_CONTEXT, "command1",
                new ProxyCommandHandler(new CountingStreamObserver<>(), "client2", "component2", "server1"));

        assertEquals(ME,testSubject.selectNode("client3", "component2", activeNodes));
        assertEquals("server1", testSubject.selectNode("client3", "component1", activeNodes) );
    }
    @Test
    public void selectNodeWithoutSubscriptions() {
        Collection<String> activeNodes = Arrays.asList(ME, "server1", "server2");
        commandRegistry.add(Topology.DEFAULT_CONTEXT, "command1",
                new DirectCommandHandler(new CountingStreamObserver<>(), "client1", "component1"));
        commandRegistry.add(Topology.DEFAULT_CONTEXT, "command1",
                new ProxyCommandHandler(new CountingStreamObserver<>(), "client2", "component2", "server1"));

        assertEquals("server2",testSubject.selectNode("client3", "component3", activeNodes) );
    }

}