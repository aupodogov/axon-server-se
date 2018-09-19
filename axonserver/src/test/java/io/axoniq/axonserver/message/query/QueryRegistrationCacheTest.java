package io.axoniq.axonserver.message.query;

import io.axoniq.axonserver.grpc.query.QueryProviderInbound;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.topology.Topology;
import io.grpc.stub.StreamObserver;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.runners.*;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.*;

/**
 * Author: marc
 */
@RunWith(MockitoJUnitRunner.class)
public class QueryRegistrationCacheTest {
    private QueryRegistrationCache queryRegistrationCache;
    private DummyStreamObserver dummyStreamObserver = new DummyStreamObserver();
    @Mock
    private QueryHandlerSelector queryHandlerSelector;

    @Before
    public void setup() {
        queryRegistrationCache = new QueryRegistrationCache(queryHandlerSelector);
    }

    @Test
    public void remove() {
        DirectQueryHandler provider1 = new DirectQueryHandler(dummyStreamObserver, "client", "component");
        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test"),
                                   "test",
                                   provider1);

        queryRegistrationCache.remove(provider1.getClientName());
        Map<QueryDefinition, Map<String, Set<QueryHandler>>> all = queryRegistrationCache.getAll();
        assertEquals(0, all.size());
    }
    @Test
    public void removeWithRemaining() {
        DirectQueryHandler provider1 = new DirectQueryHandler(dummyStreamObserver, "client", "component");
        DirectQueryHandler provider2 = new DirectQueryHandler(dummyStreamObserver, "client2", "component");
        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test"),
                                   "test",
                                   provider1);
        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test"),
                                   "test",
                                   provider2);

        queryRegistrationCache.remove(provider1.getClientName());
        Map<QueryDefinition, Map<String, Set<QueryHandler>>> all = queryRegistrationCache.getAll();
        assertEquals(1, all.size());
    }
    @Test
    public void remove1() {
        DirectQueryHandler provider1 = new DirectQueryHandler(dummyStreamObserver, "client", "component");
        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test"),
                                   "test",
                                   provider1);
        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test1"),
                                   "test",
                                   provider1);

        queryRegistrationCache.remove(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test1"), provider1.getClientName());
        Map<QueryDefinition, Map<String, Set<QueryHandler>>> all = queryRegistrationCache.getAll();
        assertEquals(1, all.size());
    }

    @Test
    public void add() {

        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test"),
                                   "test",
                                   new DirectQueryHandler(dummyStreamObserver, "client", "component"));
        Map<QueryDefinition, Map<String, Set<QueryHandler>>> all = queryRegistrationCache.getAll();
        assertEquals(1, all.size());
        assertEquals(1, all.get(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test")).size());
        assertEquals(1, all.get(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test")).get("component").size());
        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test"),
                                   "test",
                                   new DirectQueryHandler(dummyStreamObserver, "client3", "component"));
        all = queryRegistrationCache.getAll();
        assertEquals(2, all.get(new QueryDefinition(Topology.DEFAULT_CONTEXT , "test")).get("component").size());
    }

    @Test
    public void find() {
        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test"),
                                   "test",
                                   new DirectQueryHandler(dummyStreamObserver, "client", "component"));
        QueryRequest request = QueryRequest.newBuilder().setQuery("test").build();

        assertNotNull(queryRegistrationCache.find(Topology.DEFAULT_CONTEXT, request,"client"));
        assertNull(queryRegistrationCache.find(Topology.DEFAULT_CONTEXT, request,  "client1"));
    }


    @Test
    public void getForClient() {
        DirectQueryHandler provider1 = new DirectQueryHandler(dummyStreamObserver, "client", "component");
        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test"),
                                   "test",
                                   provider1);
        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test1"),
                                   "test",
                                   provider1);
        assertEquals(2, queryRegistrationCache.getForClient(provider1.getClientName()).size());

    }

    @Test
    public void find1() {
        queryRegistrationCache.add(new QueryDefinition(Topology.DEFAULT_CONTEXT, "test"),
                                   "test",
                                   new DirectQueryHandler(dummyStreamObserver, "client", "component"));
        QueryRequest request = QueryRequest.newBuilder().setQuery("test").build();
        QueryRequest request2 = QueryRequest.newBuilder().setQuery("test1").build();
        when( queryHandlerSelector.select(anyObject(), anyObject(), anyObject())).thenReturn("client");
        assertNotNull(queryRegistrationCache.find(Topology.DEFAULT_CONTEXT, request));
        assertTrue(queryRegistrationCache.find(Topology.DEFAULT_CONTEXT, request2).isEmpty());
    }

    private class DummyStreamObserver implements StreamObserver<QueryProviderInbound> {
        @Override
        public void onNext(QueryProviderInbound queryProviderInbound) {

        }

        @Override
        public void onError(Throwable throwable) {

        }

        @Override
        public void onCompleted() {

        }
    }
}