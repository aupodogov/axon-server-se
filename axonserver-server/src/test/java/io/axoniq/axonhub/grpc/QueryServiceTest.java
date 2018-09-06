package io.axoniq.axonhub.grpc;

import io.axoniq.axonhub.DispatchEvents;
import io.axoniq.axonhub.ProcessingInstructionHelper;
import io.axoniq.axonhub.QueryRequest;
import io.axoniq.axonhub.QueryResponse;
import io.axoniq.axonhub.QuerySubscription;
import io.axoniq.axonhub.SubscriptionEvents;
import io.axoniq.axonhub.context.ContextController;
import io.axoniq.axonhub.message.FlowControlQueues;
import io.axoniq.axonhub.message.query.QueryDispatcher;
import io.axoniq.axonhub.message.query.WrappedQuery;
import io.axoniq.axonhub.util.CountingStreamObserver;
import io.grpc.stub.StreamObserver;
import org.junit.*;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.isA;

/**
 * Author: marc
 */
public class QueryServiceTest {
    private QueryService testSubject;
    private QueryDispatcher queryDispatcher;
    private FlowControlQueues<WrappedQuery> queryQueue;

    private ApplicationEventPublisher eventPublisher;

    @Before
    public void setUp()  {
        queryDispatcher = mock(QueryDispatcher.class);
        queryQueue = new FlowControlQueues<>();
        eventPublisher = mock(ApplicationEventPublisher.class);
        when(queryDispatcher.getQueryQueue()).thenReturn(queryQueue);
        testSubject = new QueryService(queryDispatcher, () -> ContextController.DEFAULT, eventPublisher);
    }

    @Test
    public void flowControl() throws Exception {
        CountingStreamObserver<QueryProviderInbound> countingStreamObserver  = new CountingStreamObserver<>();
        StreamObserver<QueryProviderOutbound> requestStream = testSubject.openStream(countingStreamObserver);
        requestStream.onNext(QueryProviderOutbound.newBuilder().setFlowControl(FlowControl.newBuilder().setPermits(2).setClientName("name").build()).build());
        Thread.sleep(250);
        assertEquals(1, queryQueue.getSegments().size());
        queryQueue.put("name", new WrappedQuery(ContextController.DEFAULT, QueryRequest.newBuilder()
                                                                                       .addProcessingInstructions(ProcessingInstructionHelper.timeout(10000))
                                                                                       .build(), System.currentTimeMillis() + 2000));
        Thread.sleep(150);
        assertEquals(1, countingStreamObserver.count);
        queryQueue.put("name", new WrappedQuery(ContextController.DEFAULT, QueryRequest.newBuilder()
                .build(), System.currentTimeMillis() - 2000));
        Thread.sleep(150);
        assertEquals(1, countingStreamObserver.count);
        verify(queryDispatcher).removeFromCache(any());
    }

    @Test
    public void subscribe()  {
        StreamObserver<QueryProviderOutbound> requestStream = testSubject.openStream(new CountingStreamObserver<>());
        requestStream.onNext(QueryProviderOutbound.newBuilder()
                .setSubscribe(QuerySubscription.newBuilder().setClientName("name").setComponentName("component").setQuery("query"))
                .build());
        verify(eventPublisher).publishEvent(isA(SubscriptionEvents.SubscribeQuery.class));
    }

    @Test
    public void unsubscribe()  {
        StreamObserver<QueryProviderOutbound> requestStream = testSubject.openStream(new CountingStreamObserver<>());
        requestStream.onNext(QueryProviderOutbound.newBuilder()
                .setUnsubscribe(QuerySubscription.newBuilder().setClientName("name").setComponentName("component").setQuery("command"))
                .build());
        verify(eventPublisher, times(0)).publishEvent(isA(SubscriptionEvents.UnsubscribeQuery.class));
    }
    @Test
    public void unsubscribeAfterSubscribe() {
        StreamObserver<QueryProviderOutbound> requestStream = testSubject.openStream(new CountingStreamObserver<>());
        requestStream.onNext(QueryProviderOutbound.newBuilder()
                .setSubscribe(QuerySubscription.newBuilder().setClientName("name").setComponentName("component").setQuery("command"))
                .build());
        requestStream.onNext(QueryProviderOutbound.newBuilder()
                .setUnsubscribe(QuerySubscription.newBuilder().setClientName("name").setComponentName("component").setQuery("command"))
                .build());
        verify(eventPublisher).publishEvent(isA(SubscriptionEvents.UnsubscribeQuery.class));
    }

    @Test
    public void cancelAfterSubscribe() {
        StreamObserver<QueryProviderOutbound> requestStream = testSubject.openStream(new CountingStreamObserver<>());
        requestStream.onNext(QueryProviderOutbound.newBuilder()
                .setSubscribe(QuerySubscription.newBuilder().setClientName("name").setComponentName("component").setQuery("command"))
                .build());
        requestStream.onError(new RuntimeException("failed"));
    }

    @Test
    public void cancelBeforeSubscribe() {
        StreamObserver<QueryProviderOutbound> requestStream = testSubject.openStream(new CountingStreamObserver<>());
        requestStream.onError(new RuntimeException("failed"));
    }

    @Test
    public void close() {
        StreamObserver<QueryProviderOutbound> requestStream = testSubject.openStream(new CountingStreamObserver<>());
        requestStream.onNext(QueryProviderOutbound.newBuilder().setFlowControl(FlowControl.newBuilder().setPermits(1).setClientName("name").build()).build());
        requestStream.onCompleted();
    }

    @Test
    public void dispatch()  {
        doAnswer(invocationOnMock -> {
            DispatchEvents.DispatchQuery dispatchCommand = (DispatchEvents.DispatchQuery) invocationOnMock.getArguments()[0];
            dispatchCommand.getCallback().accept(QueryResponse.newBuilder().build());
            return null;
        }).when(eventPublisher).publishEvent(isA(DispatchEvents.DispatchQuery.class));
        CountingStreamObserver<QueryResponse> responseObserver = new CountingStreamObserver<>();
        testSubject.query(QueryRequest.newBuilder().build(), responseObserver);
        assertEquals(1, responseObserver.count);
    }

}