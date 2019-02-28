package io.axoniq.axonserver.rest;

import io.axoniq.axonserver.enterprise.cluster.GrpcRaftController;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Rest APIs for Raft management
 * @author Sara Pellegrini
 * @since 4.1
 */
@RestController
@CrossOrigin
@Profile("internal")
@RequestMapping("internal/raft")
public class RaftManagementRestController {

    private final GrpcRaftController grpcRaftController;

    public RaftManagementRestController(
            GrpcRaftController grpcRaftController) {
        this.grpcRaftController = grpcRaftController;
    }

    @PostMapping(path = "context/{context}/cleanLogEntries/{seconds}")
    public void cleanLogOlderThen(@PathVariable("context") String context, @PathVariable("seconds") long seconds){
        this.grpcRaftController.getRaftGroup(context).localNode().forceLogCleaning(seconds, SECONDS);
    }
}