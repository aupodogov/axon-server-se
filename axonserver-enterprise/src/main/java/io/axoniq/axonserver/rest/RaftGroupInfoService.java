package io.axoniq.axonserver.rest;

import io.axoniq.axonserver.cluster.jpa.JpaRaftGroupNode;
import io.axoniq.axonserver.cluster.jpa.JpaRaftGroupNodeRepository;
import io.axoniq.axonserver.config.MessagingPlatformConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * @author Marc Gathier
 */
@RestController
@RequestMapping("v1/raft")
public class RaftGroupInfoService {
    private final JpaRaftGroupNodeRepository raftGroupNodeRepository;
    private final MessagingPlatformConfiguration messagingPlatformConfiguration;


    public RaftGroupInfoService(JpaRaftGroupNodeRepository raftGroupNodeRepository,
                                MessagingPlatformConfiguration messagingPlatformConfiguration) {
        this.raftGroupNodeRepository = raftGroupNodeRepository;
        this.messagingPlatformConfiguration = messagingPlatformConfiguration;
    }

    @GetMapping("groups")
    public Set<JpaRaftGroupNode> status(){
        return raftGroupNodeRepository.findByHostAndPort(messagingPlatformConfiguration.getFullyQualifiedInternalHostname(),
                                                         messagingPlatformConfiguration.getInternalPort());
    }

    @GetMapping("members/{group}")
    public Set<JpaRaftGroupNode> members(@PathVariable("group") String group){
        return raftGroupNodeRepository.findByGroupId(group);
    }

}
