package io.axoniq.axonhub.component.processor.balancing;

/**
 * Created by Sara Pellegrini on 07/08/2018.
 * sara.pellegrini@gmail.com
 */
public interface OperationFactory {

    LoadBalancingOperation move(Integer segmentIdentifier,
                                TrackingEventProcessor trackingEventProcessor,
                                String sourceInstance,
                                String destinationInstance);

}
