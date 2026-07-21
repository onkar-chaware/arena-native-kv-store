package com.arena.kv.core;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

/**
 * Thread-safe on-heap primitive hash map: long hash -> long offset.
 * Uses a single StampedLock to guarantee lock safety during linear probing and resizing,
 * while allowing massive optimistic read parallelism on get().
 */
public class PrimitiveHashIndex {

    private static final int INITIAL_CAPACITY = 16384; // ~16K
    private static final float LOAD_FACTOR_THRESHOLD = 0.75f;

    public static final long NOT_FOUND = -1L;
    private static final long EMPTY_SLOT = 0L;
    private static final long TOMBSTONE = -2L;

    // Volatile references to ensure instant CPU cache visibility across reads
    private volatile long[] hashes;
    private volatile long[] offsets;
    private volatile int capacity;

    private int entryCount;
    private int tombstoneCount;

    private final StampedLock lock = new StampedLock();

    public PrimitiveHashIndex() {
        this.capacity = INITIAL_CAPACITY;
        this.hashes = new long[capacity];
        this.offsets = new long[capacity];
        this.entryCount = 0;
        this.tombstoneCount = 0;

        Arrays.fill(this.hashes, EMPTY_SLOT);
    }

    /**
     * Store a hash -> offset mapping.
     */
    public void put(long hash, long offset) {
        long safeHash = maskHash(hash);
        long stamp = lock.writeLock();
        try {
            ensureCapacityInternal();
            putInternal(safeHash, offset);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Retrieve the offset for a given hash using zero-lock optimistic reads.
     */
    public long get(long hash) {
        long safeHash = maskHash(hash);

        // 1. Try lock-free optimistic read
        long stamp = lock.tryOptimisticRead();
        long[] capturedHashes = this.hashes;
        long[] capturedOffsets = this.offsets;
        int capturedCap = this.capacity;

        long result = getInternal(safeHash, capturedHashes, capturedOffsets, capturedCap);

        // 2. If any write/resize occurred, validation will fail
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                result = getInternal(safeHash, this.hashes, this.offsets, this.capacity);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        return result;
    }

    /**
     * Remove mapping for a given hash.
     */
    public void remove(long hash) {
        long safeHash = maskHash(hash);
        long stamp = lock.writeLock();
        try {
            removeInternal(safeHash);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private long maskHash(long hash) {
        if (hash == EMPTY_SLOT || hash == TOMBSTONE) {
            return 1L; // Prevent collisions with sentinel values
        }
        return hash;
    }

    private void putInternal(long hash, long offset) {
        int idx = findSlot(hash);

        if (hashes[idx] == EMPTY_SLOT || hashes[idx] == TOMBSTONE) {
            if (hashes[idx] == TOMBSTONE) {
                tombstoneCount--;
            }
            entryCount++;
        }

        // Write offset BEFORE hash so optimistic readers don't see uninitialized offsets
        offsets[idx] = offset;
        hashes[idx] = hash;
    }

    private long getInternal(long hash, long[] hashArr, long[] offsetArr, int cap) {
        int mask = cap - 1;
        int idx = (int) (hash & mask);
        int probes = 0;

        while (probes < cap) {
            long storedHash = hashArr[idx];

            if (storedHash == EMPTY_SLOT) {
                return NOT_FOUND;
            }
            if (storedHash == hash) {
                return offsetArr[idx];
            }

            idx = (idx + 1) & mask;
            probes++;
        }

        return NOT_FOUND;
    }

    private void removeInternal(long hash) {
        int idx = findExistingSlot(hash);
        if (idx != -1) {
            hashes[idx] = TOMBSTONE;
            offsets[idx] = NOT_FOUND;
            entryCount--;
            tombstoneCount++;
        }
    }

    private int findSlot(long hash) {
        int mask = capacity - 1;
        int idx = (int) (hash & mask);
        int firstTombstone = -1;
        int probes = 0;

        while (probes < capacity) {
            long storedHash = hashes[idx];
            if (storedHash == EMPTY_SLOT) {
                return firstTombstone != -1 ? firstTombstone : idx;
            }
            if (storedHash == hash) {
                return idx;
            }
            if (storedHash == TOMBSTONE && firstTombstone == -1) {
                firstTombstone = idx;
            }
            idx = (idx + 1) & mask;
            probes++;
        }

        throw new IllegalStateException(
                String.format("Hash table full: capacity=%d, entries=%d, tombstones=%d",
                        capacity, entryCount, tombstoneCount)
        );
    }

    private int findExistingSlot(long hash) {
        int mask = capacity - 1;
        int idx = (int) (hash & mask);
        int probes = 0;

        while (probes < capacity) {
            long storedHash = hashes[idx];
            if (storedHash == EMPTY_SLOT) {
                return -1;
            }
            if (storedHash == hash) {
                return idx;
            }
            idx = (idx + 1) & mask;
            probes++;
        }
        return -1;
    }

    private void ensureCapacityInternal() {
        // Trigger rebuild when combined slot pollution (live + tombstones) exceeds 75%
        float totalOccupancy = (float) (entryCount + tombstoneCount) / capacity;
        if (totalOccupancy > LOAD_FACTOR_THRESHOLD) {
            rehash();
        }
    }

    private void rehash() {
        // Calculate what the load factor WOULD be after purging tombstones
        float liveLoadFactor = (float) entryCount / capacity;

        // If live entries alone take up > 50% of current capacity, double capacity
        // to grant sufficient headroom. Otherwise, keep capacity same and just purge tombstones.
        int newCapacity = (liveLoadFactor > 0.50f) ? capacity * 2 : capacity;

        long[] oldHashes = hashes;
        long[] oldOffsets = offsets;
        int oldCapacity = capacity;

        long[] newHashes = new long[newCapacity];
        long[] newOffsets = new long[newCapacity];
        Arrays.fill(newHashes, EMPTY_SLOT);

        this.capacity = newCapacity;
        this.hashes = newHashes;
        this.offsets = newOffsets;
        this.entryCount = 0;
        this.tombstoneCount = 0;

        for (int i = 0; i < oldCapacity; i++) {
            long h = oldHashes[i];
            if (h != EMPTY_SLOT && h != TOMBSTONE) {
                putInternal(h, oldOffsets[i]);
            }
        }
    }

    public int getEntryCount() {
        long stamp = lock.readLock();
        try {
            return entryCount;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public int getCapacity() {
        return capacity;
    }
}