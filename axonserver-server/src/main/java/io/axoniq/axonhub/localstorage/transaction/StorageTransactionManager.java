package io.axoniq.axonhub.localstorage.transaction;

import io.axoniq.axondb.Event;
import io.axoniq.axonhub.localstorage.StorageCallback;

import java.util.List;

/**
 * Author: marc
 */
public interface StorageTransactionManager {

    void store(List<Event> eventList, StorageCallback storageCallback);

    long getLastToken();

    boolean reserveSequenceNumbers(List<Event> eventList);

    default long waitingTransactions() {
        return 0;
    }

    default void rollback(long token) {
        // default no-op
    }

    default void cancelPendingTransactions() {

    }
}
