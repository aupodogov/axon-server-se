package io.axoniq.axonhub.cluster;

import io.axoniq.axonhub.grpc.Publisher;
import io.axoniq.axonhub.internal.grpc.ConnectorCommand;
import org.springframework.stereotype.Component;

/**
 * Created by Sara Pellegrini on 14/05/2018.
 * sara.pellegrini@gmail.com
 */
@Component
public class ClusterPublisher implements Publisher<ConnectorCommand> {

    private final ClusterController clusterController;

    public ClusterPublisher(ClusterController clusterController) {
        this.clusterController = clusterController;
    }

    @Override
    public void publish(ConnectorCommand message) {
        clusterController.activeConnections().forEach(connection -> connection.publish(message));
    }
}
