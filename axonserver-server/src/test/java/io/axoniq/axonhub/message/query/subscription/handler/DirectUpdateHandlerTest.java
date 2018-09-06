package io.axoniq.axonhub.message.query.subscription.handler;

import io.axoniq.axonhub.QueryUpdate;
import io.axoniq.axonhub.QueryUpdateComplete;
import io.axoniq.axonhub.QueryUpdateCompleteExceptionally;
import io.axoniq.axonhub.SubscriptionQueryResponse;
import org.junit.*;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by Sara Pellegrini on 16/05/2018.
 * sara.pellegrini@gmail.com
 */
public class DirectUpdateHandlerTest {

    @Test
    public void onUpdate() {
        List<SubscriptionQueryResponse> messages = new LinkedList<>();
        DirectUpdateHandler directUpdateHandler = new DirectUpdateHandler(messages::add);
        QueryUpdate update = QueryUpdate.newBuilder().build();
        SubscriptionQueryResponse response = SubscriptionQueryResponse.newBuilder().setUpdate(update).build();
        directUpdateHandler.onSubscriptionQueryResponse(response);
        assertEquals(1, messages.size());
        assertEquals(update, messages.get(0).getUpdate());
    }

    @Test
    public void onComplete() {
        List<SubscriptionQueryResponse> messages = new LinkedList<>();
        DirectUpdateHandler directUpdateHandler = new DirectUpdateHandler(messages::add);
        QueryUpdateComplete updateComplete = QueryUpdateComplete.newBuilder().build();
        SubscriptionQueryResponse response = SubscriptionQueryResponse.newBuilder().setComplete(updateComplete).build();
        directUpdateHandler.onSubscriptionQueryResponse(response);
        directUpdateHandler.onSubscriptionQueryResponse(response);
        assertEquals(2, messages.size());
        assertEquals(updateComplete, messages.get(0).getComplete());
    }

    @Test
    public void onCompleteExceptionally() {
        List<SubscriptionQueryResponse> messages = new LinkedList<>();
        DirectUpdateHandler directUpdateHandler = new DirectUpdateHandler(messages::add);
        QueryUpdateCompleteExceptionally completeExceptionally = QueryUpdateCompleteExceptionally.newBuilder().build();
        SubscriptionQueryResponse response = SubscriptionQueryResponse.newBuilder()
                                                                      .setCompleteExceptionally(completeExceptionally)
                                                                      .build();
        directUpdateHandler.onSubscriptionQueryResponse(response);
        assertEquals(1, messages.size());
        assertEquals(completeExceptionally, messages.get(0).getCompleteExceptionally());
    }
}