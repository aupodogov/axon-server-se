package io.axoniq.axonserver.cluster;

/**
 * @author Sara Pellegrini
 * @since 4.0
 */
public interface Election {

    void onVoteReceived(String voter, boolean granted);

    boolean isWon();

}

