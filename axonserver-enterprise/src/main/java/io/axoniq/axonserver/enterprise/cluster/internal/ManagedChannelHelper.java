package io.axoniq.axonserver.enterprise.cluster.internal;

import io.axoniq.axonserver.config.MessagingPlatformConfiguration;
import io.axoniq.axonserver.enterprise.jpa.ClusterNode;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

/**
 * Author: marc
 */
public class ManagedChannelHelper {
    private static final Logger logger = LoggerFactory.getLogger(ManagedChannelHelper.class);

    private static final Map<String, ManagedChannel> channelPerNode = new ConcurrentHashMap<>();

    public static ManagedChannel createManagedChannel(MessagingPlatformConfiguration messagingPlatformConfiguration, ClusterNode clusterNode) {

        ManagedChannel channel = channelPerNode.get(clusterNode.getName());
        if( channel != null) return channel;

        channel = createManagedChannel(messagingPlatformConfiguration, clusterNode.getInternalHostName(), clusterNode.getGrpcInternalPort());
        if( channel != null) {
            channelPerNode.put(clusterNode.getName(), channel);
        }
        return channel;
    }

    public static ManagedChannel createManagedChannel(MessagingPlatformConfiguration messagingPlatformConfiguration, String host, int port) {
        ManagedChannel channel = null;
        try {
            NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port);

            if( messagingPlatformConfiguration.getKeepAliveTime() > 0) {
                builder.keepAliveTime(messagingPlatformConfiguration.getKeepAliveTime(), TimeUnit.MILLISECONDS)
                       .keepAliveTimeout(messagingPlatformConfiguration.getKeepAliveTimeout(), TimeUnit.MILLISECONDS)
                       .keepAliveWithoutCalls(true);
            }
            if( messagingPlatformConfiguration.getSsl() != null && messagingPlatformConfiguration.getSsl().isEnabled()) {
                if (addSslConfig(messagingPlatformConfiguration, builder)) {
                    return null;
                }
            } else {
                builder.usePlaintext();
            }
            if( messagingPlatformConfiguration.getMaxMessageSize() > 0) {
                builder.maxInboundMessageSize(messagingPlatformConfiguration.getMaxMessageSize());
            }
            builder.directExecutor();
            channel = builder.build();
        } catch(Exception ex) {
            logger.debug("Error while building channel - {}", ex.getMessage());
            return null;
        }
        return channel;
    }

    private static boolean addSslConfig(MessagingPlatformConfiguration messagingPlatformConfiguration,
                                        NettyChannelBuilder builder) {
        try {
            SslContext sslContext = GrpcSslContexts.forClient()
                                                   .trustManager(new File(messagingPlatformConfiguration.getSsl().getInternalCertChainFile()))
                                                   .build();
            builder.sslContext(sslContext);
        } catch (SSLException e) {
            logger.error("Failed to setup SSL context", e);
            return true;
        }
        return false;
    }

    public static void checkShutdownNeeded(String name, Throwable throwable) {
        if( throwable instanceof StatusRuntimeException) {
            StatusRuntimeException sre = (StatusRuntimeException)throwable;
            if( sre.getStatus().getCode().equals(Status.Code.UNAVAILABLE)) {
                ManagedChannel channel = channelPerNode.remove(name);
                if( channel != null) {
                    try {
                        channel.shutdown().awaitTermination(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

            }
        }
    }
}
