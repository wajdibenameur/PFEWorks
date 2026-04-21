package tn.iteam.cache;

import java.time.Instant;

/**
 * Immutable snapshot wrapper with metadata for cache entries.
 * Provides clear visibility into data freshness and source.
 * 
 * @param <T> The type of data being cached
 */
public record CacheSnapshot<T>(
    T data,
    Instant createdAt,
    Instant updatedAt,
    CacheProvider provider,
    boolean stale
) {

    /**
     * Creates a new snapshot with current timestamp.
     */
    public static <T> CacheSnapshot<T> of(T data, CacheProvider provider) {
        Instant now = Instant.now();
        return new CacheSnapshot<>(data, now, now, provider, false);
    }

    /**
     * Creates a new snapshot with explicit timestamps.
     */
    public static <T> CacheSnapshot<T> of(T data, Instant createdAt, Instant updatedAt, CacheProvider provider) {
        return new CacheSnapshot<>(data, createdAt, updatedAt, provider, false);
    }

    /**
     * Creates a stale snapshot (data too old).
     */
    public static <T> CacheSnapshot<T> stale(T data, Instant createdAt, CacheProvider provider) {
        return new CacheSnapshot<>(data, createdAt, Instant.now(), provider, true);
    }

    /**
     * Returns the age of this snapshot in seconds.
     */
    public long ageSeconds() {
        return java.time.Duration.between(createdAt, Instant.now()).getSeconds();
    }

    /**
     * Returns true if this snapshot is older than the given threshold.
     */
    public boolean isOlderThan(long seconds) {
        return ageSeconds() > seconds;
    }

    /**
     * Returns true if this snapshot was created before the given instant.
     */
    public boolean isCreatedBefore(Instant instant) {
        return createdAt.isBefore(instant);
    }

    /**
     * Returns a new snapshot with updated data, preserving metadata.
     */
    public CacheSnapshot<T> withData(T newData) {
        return new CacheSnapshot<>(newData, createdAt, Instant.now(), provider, stale);
    }

    /**
     * Returns a new snapshot marked as stale.
     */
    public CacheSnapshot<T> markStale() {
        return new CacheSnapshot<>(data, createdAt, Instant.now(), provider, true);
    }

    /**
     * Returns a new snapshot with updated provider.
     */
    public CacheSnapshot<T> withProvider(CacheProvider newProvider) {
        return new CacheSnapshot<>(data, createdAt, updatedAt, newProvider, stale);
    }

    /**
     * Cache provider source enum.
     */
    public enum CacheProvider {
        REDIS,
        IN_MEMORY,
        NONE
    }
}