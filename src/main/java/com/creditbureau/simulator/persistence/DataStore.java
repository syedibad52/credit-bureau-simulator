package com.creditbureau.simulator.persistence;

import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.Consumer;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.Tradeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the data store.
 * Uses thread-safe collections for deterministic operation.
 * 
 * <p>Data is stored in insertion order and retrieved in deterministic sorted order.
 * A TreeMap is used for consumers to ensure consistent alphabetical ordering.
 */
public class DataStore implements DataStoreInterface {

    private final List<Tradeline> tradelines = new CopyOnWriteArrayList<>();
    private final List<Enquiry> enquiries = new CopyOnWriteArrayList<>();
    private final List<AccountEvent> events = new CopyOnWriteArrayList<>();
    private final Map<String, Consumer> consumers = new TreeMap<>(); // Sorted by consumer ID

    @Override
    public void storeTradelines(List<Tradeline> newTradelines) {
        tradelines.addAll(newTradelines);
        for (Tradeline t : newTradelines) {
            getOrCreateConsumer(t.consumerId());
        }
    }

    @Override
    public void storeEnquiries(List<Enquiry> newEnquiries) {
        enquiries.addAll(newEnquiries);
        for (Enquiry e : newEnquiries) {
            getOrCreateConsumer(e.consumerId());
        }
    }

    @Override
    public void storeEvents(List<AccountEvent> newEvents) {
        events.addAll(newEvents);
    }

    @Override
    public List<Tradeline> getTradelinesForConsumer(String consumerId) {
        return tradelines.stream()
                .filter(t -> t.consumerId().equals(consumerId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Enquiry> getEnquiriesForConsumer(String consumerId) {
        return enquiries.stream()
                .filter(e -> e.consumerId().equals(consumerId))
                .collect(Collectors.toList());
    }

    @Override
    public List<AccountEvent> getEventsForConsumer(String consumerId) {
        return events.stream()
                .filter(e -> e.consumerId().equals(consumerId))
                .collect(Collectors.toList());
    }

    @Override
    public Consumer getOrCreateConsumer(String consumerId) {
        return consumers.computeIfAbsent(consumerId, Consumer::new);
    }

    @Override
    public Optional<Consumer> getConsumer(String consumerId) {
        return Optional.ofNullable(consumers.get(consumerId));
    }

    @Override
    public void storeConsumer(Consumer consumer) {
        consumers.put(consumer.getConsumerId(), consumer);
    }

    @Override
    public List<String> getAllConsumerIds() {
        return new ArrayList<>(consumers.keySet());
    }

    @Override
    public void clear() {
        tradelines.clear();
        enquiries.clear();
        events.clear();
        consumers.clear();
    }

    /** Get total count of stored tradelines (for diagnostics). */
    public int getTradelineCount() { return tradelines.size(); }

    /** Get total count of stored enquiries (for diagnostics). */
    public int getEnquiryCount() { return enquiries.size(); }

    /** Get total count of stored events (for diagnostics). */
    public int getEventCount() { return events.size(); }
}

