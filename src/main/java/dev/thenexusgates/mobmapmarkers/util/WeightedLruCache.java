package dev.thenexusgates.mobmapmarkers.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

public final class WeightedLruCache<K, V> {

    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final ToLongFunction<V> weightFunction;
    private final int maxEntries;
    private final long maxTotalWeight;

    private long totalWeight;

    public WeightedLruCache(int maxEntries, long maxTotalWeight, ToLongFunction<V> weightFunction) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be greater than zero");
        }
        if (maxTotalWeight < 0L) {
            throw new IllegalArgumentException("maxTotalWeight cannot be negative");
        }

        this.maxEntries = maxEntries;
        this.maxTotalWeight = maxTotalWeight;
        this.weightFunction = Objects.requireNonNull(weightFunction, "weightFunction");
    }

    public synchronized V get(K key) {
        return entries.get(key);
    }

    public V getOrCreate(K key, Supplier<? extends V> valueSupplier) {
        Objects.requireNonNull(valueSupplier, "valueSupplier");

        V cached = get(key);
        if (cached != null) {
            return cached;
        }

        V created = valueSupplier.get();
        if (created == null) {
            return null;
        }

        synchronized (this) {
            V existing = entries.get(key);
            if (existing != null) {
                return existing;
            }

            cacheValue(key, created);
            return created;
        }
    }

    public synchronized void put(K key, V value) {
        if (value == null) {
            remove(key);
            return;
        }

        cacheValue(key, value);
    }

    public synchronized V remove(K key) {
        V removed = entries.remove(key);
        if (removed != null) {
            totalWeight -= weightOf(removed);
            if (totalWeight < 0L) {
                totalWeight = 0L;
            }
        }
        return removed;
    }

    public synchronized void clear() {
        entries.clear();
        totalWeight = 0L;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long totalWeight() {
        return totalWeight;
    }

    private void cacheValue(K key, V value) {
        long weight = weightOf(value);
        if (maxTotalWeight > 0L && weight > maxTotalWeight) {
            return;
        }

        V previous = entries.put(key, value);
        if (previous != null) {
            totalWeight -= weightOf(previous);
        }
        totalWeight += weight;
        evictIfNeeded();
    }

    private void evictIfNeeded() {
        Iterator<Map.Entry<K, V>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()
                && (entries.size() > maxEntries || (maxTotalWeight > 0L && totalWeight > maxTotalWeight))) {
            Map.Entry<K, V> eldest = iterator.next();
            totalWeight -= weightOf(eldest.getValue());
            iterator.remove();
        }

        if (totalWeight < 0L) {
            totalWeight = 0L;
        }
    }

    private long weightOf(V value) {
        return Math.max(0L, weightFunction.applyAsLong(value));
    }
}