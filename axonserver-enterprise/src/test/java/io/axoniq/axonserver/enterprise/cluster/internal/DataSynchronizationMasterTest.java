package io.axoniq.axonserver.enterprise.cluster.internal;

import io.axoniq.axonserver.enterprise.cluster.events.ClusterEvents;
import io.axoniq.axonserver.enterprise.context.ContextController;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.internal.StartSynchronization;
import io.axoniq.axonserver.grpc.internal.SynchronizationReplicaInbound;
import io.axoniq.axonserver.grpc.internal.SynchronizationReplicaOutbound;
import io.axoniq.axonserver.grpc.internal.TransactionWithToken;
import io.axoniq.axonserver.localstorage.EventType;
import io.axoniq.axonserver.localstorage.EventTypeContext;
import io.axoniq.axonserver.localstorage.LocalEventStore;
import io.axoniq.axonserver.localstorage.SerializedEvent;
import io.axoniq.axonserver.util.CountingStreamObserver;
import io.grpc.stub.StreamObserver;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.runners.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.*;

/**
 * Author: marc
 */
@RunWith(MockitoJUnitRunner.class)
public class DataSynchronizationMasterTest {
    private DataSynchronizationMaster testSubject;
    @Mock
    private ContextController contextController;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private LocalEventStore localEventStore;

    @Before
    public void setUp()  {
        ApplicationEventPublisher eventPublisher = new ApplicationEventPublisher() {
            @Override
            public void publishEvent(ApplicationEvent applicationEvent) {
            }

            @Override
            public void publishEvent(Object o) {

            }
        };
        SyncStatusController syncStatusController = mock(SyncStatusController.class);
        testSubject = new DataSynchronizationMaster(contextController, syncStatusController, eventPublisher);
        when(applicationContext.getBean(eq(LocalEventStore.class))).thenReturn(localEventStore);
        when( localEventStore.streamEventTransactions(any(), anyLong(), any())).thenReturn(new CompletableFuture<>());
        when( localEventStore.streamSnapshotTransactions(any(), anyLong(), any())).thenReturn(new CompletableFuture<>());

        testSubject.setApplicationContext(applicationContext);
    }

    @Test
    public void getQuorum() {
    }

    @Test
    public void registerListener() {
    }

    @Test
    public void publish() {
        setupConnection(new CountingStreamObserver<>(), "test1");
        setupConnection(new CountingStreamObserver<>(), "test2");

        testSubject.publish(new EventTypeContext("default", EventType.EVENT),
                            Collections.singletonList(new SerializedEvent(Event.getDefaultInstance())), 100);

    }

    @Test
    public void setApplicationContext() {
    }

    @Test
    public void publishSafepoints() {
        setupConnection(new CountingStreamObserver<>(), "test1");

        testSubject.publishSafepoints("default", 10, 10);
    }

    private StreamObserver<SynchronizationReplicaOutbound> setupConnection(StreamObserver<SynchronizationReplicaInbound> streamToReplica, String replicaName) {
        StreamObserver<SynchronizationReplicaOutbound> inboundStream = testSubject
                .openConnection(streamToReplica);

        inboundStream.onNext(SynchronizationReplicaOutbound.newBuilder()
                                                           .setStart(StartSynchronization.newBuilder()
                                                                                         .setNodeName(replicaName)
                                                                                         .setContext("default")
                                                                                         .setSnaphshotToken(0)
                                                                                         .setEventToken(0)
                                                                                         .setPermits(100)
                                                                                         .build())
                                                           .build());

        return inboundStream;
    }

    @Test
    public void on() {
        setupConnection(new CountingStreamObserver<>(), "test1");
        assertEquals(1, testSubject.getConnectionsPerContext().size());
        testSubject.on(new ClusterEvents.MasterStepDown("default", false));
        assertEquals(0, testSubject.getConnectionsPerContext().size());
    }

    @Test
    public void openConnection() {
        doAnswer(invocationOnMock -> {
            long token = (long)invocationOnMock.getArguments()[1];
            Predicate<TransactionWithToken> consumer = (Predicate<TransactionWithToken>) invocationOnMock.getArguments()[2];
            TransactionWithToken transactionWithToken;
            do{
                transactionWithToken = TransactionWithToken.newBuilder().setToken(token++)
                                                           .addEvents(Event.newBuilder().build().toByteString()).build();

            } while( consumer.test(transactionWithToken));
            return new CompletableFuture<>();
        }).when(localEventStore).streamEventTransactions(any(), anyLong(), any());

        setupConnection(new CountingStreamObserver<>(), "demo");
    }

    @Test
    public void getConnectionsPerContext() {
    }
}