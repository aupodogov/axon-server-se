package io.axoniq.axonserver.component.processor.listener;

import io.axoniq.axonserver.component.ComponentItem;
import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
import io.axoniq.axonserver.grpc.control.EventProcessorInfo.EventTrackerInfo;

import java.util.Iterator;

/**
 * Created by Sara Pellegrini on 21/03/2018.
 * sara.pellegrini@gmail.com
 */
public interface ClientProcessor extends ComponentItem, Iterable<EventTrackerInfo> {

    String clientId();

    EventProcessorInfo eventProcessorInfo();

    default Boolean running(){
        return eventProcessorInfo().getRunning();
    }

    @Override
    default Iterator<EventTrackerInfo> iterator() {
        return eventProcessorInfo().getEventTrackersInfoList().iterator();
    }
}
