package io.axoniq.axonserver.enterprise.messaging.event;

import io.axoniq.axonserver.config.MessagingPlatformConfiguration;
import io.axoniq.axonserver.enterprise.cluster.internal.ContextAddingInterceptor;
import io.axoniq.axonserver.enterprise.cluster.internal.InternalTokenAddingInterceptor;
import io.axoniq.axonserver.enterprise.jpa.ClusterNode;
import io.axoniq.axonserver.enterprise.messaging.RemoteAxonServerStreamObserver;
import io.axoniq.axonserver.exception.ErrorCode;
import io.axoniq.axonserver.exception.MessagingPlatformException;
import io.axoniq.axonserver.grpc.ChannelProvider;
import io.axoniq.axonserver.grpc.GrpcExceptionBuilder;
import io.axoniq.axonserver.grpc.event.Confirmation;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventStoreGrpc;
import io.axoniq.axonserver.grpc.event.GetAggregateEventsRequest;
import io.axoniq.axonserver.grpc.event.GetAggregateSnapshotsRequest;
import io.axoniq.axonserver.grpc.event.GetEventsRequest;
import io.axoniq.axonserver.grpc.event.GetFirstTokenRequest;
import io.axoniq.axonserver.grpc.event.GetLastTokenRequest;
import io.axoniq.axonserver.grpc.event.GetTokenAtRequest;
import io.axoniq.axonserver.grpc.event.QueryEventsRequest;
import io.axoniq.axonserver.grpc.event.QueryEventsResponse;
import io.axoniq.axonserver.grpc.event.ReadHighestSequenceNrRequest;
import io.axoniq.axonserver.grpc.event.ReadHighestSequenceNrResponse;
import io.axoniq.axonserver.grpc.event.TrackingToken;
import io.axoniq.axonserver.message.event.EventDispatcher;
import io.axoniq.axonserver.message.event.NoOpStreamObserver;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Client for the event store used by Axon Server when the leader of the event store is on a different Axon Server node.
 * @author Marc Gathier
 * @since 4.0
 */
public class RemoteEventStore implements io.axoniq.axonserver.message.event.EventStore {
    private final ClusterNode clusterNode;
    private final MessagingPlatformConfiguration messagingPlatformConfiguration;
    private final ChannelProvider channelProvider;

    public RemoteEventStore(ClusterNode clusterNode,
                            MessagingPlatformConfiguration messagingPlatformConfiguration,
                            ChannelProvider channelProvider) {
        this.clusterNode = clusterNode;
        this.messagingPlatformConfiguration = messagingPlatformConfiguration;
        this.channelProvider = channelProvider;
    }

    private EventStoreGrpc.EventStoreStub getEventStoreStub(String context) {
        Channel channel = channelProvider.get(clusterNode);
        if (channel == null) throw new MessagingPlatformException(ErrorCode.NO_EVENTSTORE,
                                                                  "No connection to event store available");
        return EventStoreGrpc.newStub(channel).withInterceptors(
                new ContextAddingInterceptor(() -> context),
                new InternalTokenAddingInterceptor(messagingPlatformConfiguration.getAccesscontrol().getInternalToken()));
    }

    private EventDispatcherStub getNonMarshallingStub(String context) {
        Channel channel = channelProvider.get(clusterNode);
        if (channel == null) throw new MessagingPlatformException(ErrorCode.NO_EVENTSTORE,
                                                                  "No connection to event store available");
        return new EventDispatcherStub(channel).withInterceptors(
                new ContextAddingInterceptor(() -> context),
                new InternalTokenAddingInterceptor(messagingPlatformConfiguration.getAccesscontrol().getInternalToken()));
    }

    @Override
    public CompletableFuture<Confirmation> appendSnapshot(String context, Event eventMessage) {
        EventStoreGrpc.EventStoreStub stub = getEventStoreStub(context);
        CompletableFuture<Confirmation> completableFuture = new CompletableFuture<>();
        stub.appendSnapshot(eventMessage, new CompletableStreamObserver<>(completableFuture));
        return completableFuture;
    }

    @Override
    public StreamObserver<InputStream> createAppendEventConnection(String context,
                                                                   StreamObserver<Confirmation> responseObserver) {
        EventDispatcherStub stub = getNonMarshallingStub(context);

        try {
            return io.grpc.Context.current()
                                  .fork()
                                  .wrap(() -> stub.appendEvent(new RemoteAxonServerStreamObserver<>(responseObserver)))
                                  .call();
        } catch (Exception e) {
            responseObserver.onError(GrpcExceptionBuilder.build(e));
            return new NoOpStreamObserver<>();
        }
    }

    @Override
    public void listAggregateEvents(String context, GetAggregateEventsRequest request,
                                    StreamObserver<InputStream> responseStreamObserver) {
        EventDispatcherStub stub = getNonMarshallingStub(context);
        stub.listAggregateEvents(request, new RemoteAxonServerStreamObserver<>(responseStreamObserver));

    }

    @Override
    public void listAggregateSnapshots(String context, GetAggregateSnapshotsRequest request,
                                    StreamObserver<InputStream> responseStreamObserver) {
        EventDispatcherStub stub = getNonMarshallingStub(context);
        stub.listAggregateSnapshots(request, new RemoteAxonServerStreamObserver<>(responseStreamObserver));

    }

    @Override
    public void deleteAllEventData(String context) {
        throw new UnsupportedOperationException("Development mode deletion is not supported in clustered environments");
    }

    @Override
    public StreamObserver<GetEventsRequest> listEvents(String context,
                                                       StreamObserver<InputStream> responseStreamObserver) {
        EventDispatcherStub stub = getNonMarshallingStub(context);
        try {
            return io.grpc.Context.current()
                                  .fork()
                                  .wrap(() -> stub
                                          .listEvents(new RemoteAxonServerStreamObserver<>(responseStreamObserver)))
                                  .call();
        } catch (Exception e) {
            responseStreamObserver.onError(GrpcExceptionBuilder.build(e));
            return new NoOpStreamObserver<>();
        }
    }

    @Override
    public void getFirstToken(String context, GetFirstTokenRequest request,
                              StreamObserver<TrackingToken> responseObserver) {
        getEventStoreStub(context).getFirstToken(request, new RemoteAxonServerStreamObserver<>(responseObserver));
    }

    @Override
    public void getLastToken(String context, GetLastTokenRequest request,
                             StreamObserver<TrackingToken> responseObserver) {

        getEventStoreStub(context).getLastToken(request, new RemoteAxonServerStreamObserver<>(responseObserver));
    }

    @Override
    public void getTokenAt(String context, GetTokenAtRequest request, StreamObserver<TrackingToken> responseObserver) {
        getEventStoreStub(context).getTokenAt(request, new RemoteAxonServerStreamObserver<>(responseObserver));
    }

    @Override
    public void readHighestSequenceNr(String context, ReadHighestSequenceNrRequest request,
                                      StreamObserver<ReadHighestSequenceNrResponse> responseObserver) {
        getEventStoreStub(context).readHighestSequenceNr(request, new RemoteAxonServerStreamObserver<>(responseObserver));
    }

    @Override
    public StreamObserver<QueryEventsRequest> queryEvents(String context,
                                                          StreamObserver<QueryEventsResponse> responseObserver) {
        try {
            return io.grpc.Context.current()
                                  .fork()
                                  .wrap(() -> getEventStoreStub(context)
                                          .queryEvents(new RemoteAxonServerStreamObserver<>(responseObserver))).call();
        } catch (Exception e) {
            responseObserver.onError(GrpcExceptionBuilder.build(e));
            return new NoOpStreamObserver<>();
        }
    }

    private static class CompletableStreamObserver<T> implements StreamObserver<T> {

        private final CompletableFuture<T> completableFuture;

        private CompletableStreamObserver(
                CompletableFuture<T> completableFuture) {
            this.completableFuture = completableFuture;
        }

        @Override
        public void onNext(T t) {
            completableFuture.complete(t);
        }

        @Override
        public void onError(Throwable throwable) {
            completableFuture.completeExceptionally(GrpcExceptionBuilder.parse(throwable));

        }

        @Override
        public void onCompleted() {
            // no-op
        }
    }

    private static class EventDispatcherStub extends AbstractStub<EventDispatcherStub> {
        private EventDispatcherStub(Channel channel) {
            super(channel);
        }

        private EventDispatcherStub(Channel channel, CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected EventDispatcherStub build(Channel channel, CallOptions callOptions) {
            return new EventDispatcherStub(channel, callOptions);
        }

        private StreamObserver<GetEventsRequest> listEvents(StreamObserver<InputStream> inputStream) {
            return ClientCalls.asyncBidiStreamingCall(
                    getChannel().newCall(EventDispatcher.METHOD_LIST_EVENTS, getCallOptions()), inputStream);
        }

        private void listAggregateEvents(GetAggregateEventsRequest request, StreamObserver<InputStream> responseStream) {
            ClientCalls.asyncServerStreamingCall(
                    getChannel().newCall(EventDispatcher.METHOD_LIST_AGGREGATE_EVENTS, getCallOptions()), request, responseStream);
        }

        public void listAggregateSnapshots(GetAggregateSnapshotsRequest request, StreamObserver<InputStream> responseStream) {
            ClientCalls.asyncServerStreamingCall(
                    getChannel().newCall(EventDispatcher.METHOD_LIST_AGGREGATE_SNAPSHOTS, getCallOptions()), request, responseStream);
        }

        public StreamObserver<InputStream> appendEvent(
                StreamObserver<Confirmation> responseObserver) {
            return ClientCalls.asyncBidiStreamingCall(getChannel().newCall(EventDispatcher.METHOD_APPEND_EVENT, getCallOptions()), responseObserver);
        }

    }

}
