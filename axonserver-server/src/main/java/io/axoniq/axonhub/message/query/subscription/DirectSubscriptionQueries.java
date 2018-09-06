package io.axoniq.axonhub.message.query.subscription;

import io.axoniq.axonhub.ClusterEvents.ApplicationDisconnected;
import io.axoniq.axonhub.QueryRequest;
import io.axoniq.axonhub.SubscriptionQuery;
import io.axoniq.axonhub.SubscriptionQueryEvents.SubscriptionQueryCanceled;
import io.axoniq.axonhub.SubscriptionQueryEvents.SubscriptionQueryRequested;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Created by Sara Pellegrini on 14/05/2018.
 * sara.pellegrini@gmail.com
 */
@Component
public class DirectSubscriptionQueries implements Iterable<DirectSubscriptionQueries.ContextSubscriptionQuery> {

    private final Map<String, Map<String, ContextSubscriptionQuery>> map = new ConcurrentHashMap<>();

    @EventListener
    public void on(SubscriptionQueryRequested event) {
        SubscriptionQuery request = event.subscription();
        QueryRequest query = request.getQueryRequest();
        ContextSubscriptionQuery contextSubscriptionQuery = new ContextSubscriptionQuery(event.context(), request);
        clientRequests(query.getClientId()).put(request.getSubscriptionIdentifier(), contextSubscriptionQuery);
    }

    @EventListener
    public void on(ApplicationDisconnected applicationDisconnected){
        map.remove(applicationDisconnected.getClient());
    }

    @EventListener
    public void on(SubscriptionQueryCanceled event) {
        QueryRequest request = event.unsubscribe().getQueryRequest();
        clientRequests(request.getClientId()).remove(event.subscriptionId());
    }

    @Override
    @Nonnull
    public Iterator<ContextSubscriptionQuery> iterator() {
        return map.values().stream().map(Map::values).flatMap(Collection::stream).iterator();
    }

    private Map<String, ContextSubscriptionQuery> clientRequests(String clientId) {
        return map.computeIfAbsent(clientId,id -> new ConcurrentHashMap<>());
    }

    public static class ContextSubscriptionQuery {

        private final String context;

        private final SubscriptionQuery subscriptionQuery;

        ContextSubscriptionQuery(String context, SubscriptionQuery subscriptionQuery) {
            this.context = context;
            this.subscriptionQuery = subscriptionQuery;
        }

        public String context() {
            return context;
        }

        SubscriptionQuery subscriptionQuery() {
            return subscriptionQuery;
        }

        String queryName(){
            return subscriptionQuery.getQueryRequest().getQuery();
        }
    }
}
