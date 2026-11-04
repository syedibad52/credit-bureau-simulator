package com.creditbureau.simulator.persistence;

import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.Consumer;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.Tradeline;

import java.util.List;
import java.util.Optional;

/**
 * Interface for data storage operations.
 * Provides a clean abstraction allowing future database integration
 * without changing the business logic layer.
 */
public interface DataStoreInterface {

    /** Store a batch of parsed tradelines. */
    void storeTradelines(List<Tradeline> tradelines);

    /** Store a batch of parsed enquiries. */
    void storeEnquiries(List<Enquiry> enquiries);

    /** Store a batch of parsed account events. */
    void storeEvents(List<AccountEvent> events);

    /** Retrieve all tradelines for a specific consumer. */
    List<Tradeline> getTradelinesForConsumer(String consumerId);

    /** Retrieve all enquiries for a specific consumer. */
    List<Enquiry> getEnquiriesForConsumer(String consumerId);

    /** Retrieve all events for a specific consumer. */
    List<AccountEvent> getEventsForConsumer(String consumerId);

    /** Retrieve or create a Consumer aggregate. */
    Consumer getOrCreateConsumer(String consumerId);

    /** Retrieve a Consumer if they exist. */
    Optional<Consumer> getConsumer(String consumerId);

    /** Store/update a Consumer aggregate (with merged accounts, score history, etc.). */
    void storeConsumer(Consumer consumer);

    /** Get all known consumer IDs. */
    List<String> getAllConsumerIds();

    /** Clear all stored data. */
    void clear();
}

