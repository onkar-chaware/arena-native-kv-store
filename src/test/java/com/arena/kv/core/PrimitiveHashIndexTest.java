package com.arena.kv.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD Tests for PrimitiveHashIndex.
 * 
 * Validates:
 * - Insert key hash -> offset mapping
 * - Lookup key hash returns correct offset (or sentinel -1)
 * - Remove key hash removes mapping
 * - Handle collision resolution via linear probing
 * - Thread-safe concurrent operations (multiple threads inserting/removing without races)
 */
@DisplayName("PrimitiveHashIndex Tests")
class PrimitiveHashIndexTest {

    private PrimitiveHashIndex index;

    @BeforeEach
    void setUp() {
        index = new PrimitiveHashIndex();
    }

    // ==================== Basic Put/Get Tests ====================

    @Test
    @DisplayName("Should store and retrieve a single entry")
    void testPutAndGetSingleEntry() {
        long hash = 12345L;
        long offset = 1000L;
        
        index.put(hash, offset);
        long retrieved = index.get(hash);
        
        assertThat(retrieved).isEqualTo(offset);
    }

    @Test
    @DisplayName("Should return -1 for non-existent hash")
    void testGetNonExistentHash() {
        long hash = 99999L;
        long retrieved = index.get(hash);
        
        assertThat(retrieved).isEqualTo(-1L);
    }

    @Test
    @DisplayName("Should store multiple different entries")
    void testPutAndGetMultipleEntries() {
        long hash1 = 111L;
        long offset1 = 1000L;
        long hash2 = 222L;
        long offset2 = 2000L;
        long hash3 = 333L;
        long offset3 = 3000L;
        
        index.put(hash1, offset1);
        index.put(hash2, offset2);
        index.put(hash3, offset3);
        
        assertThat(index.get(hash1)).isEqualTo(offset1);
        assertThat(index.get(hash2)).isEqualTo(offset2);
        assertThat(index.get(hash3)).isEqualTo(offset3);
    }

    @Test
    @DisplayName("Should retrieve entries in any order")
    void testGetEntriesInAnyOrder() {
        long[] hashes = { 111L, 222L, 333L, 444L, 555L };
        long[] offsets = { 1000L, 2000L, 3000L, 4000L, 5000L };
        
        for (int i = 0; i < hashes.length; i++) {
            index.put(hashes[i], offsets[i]);
        }
        
        // Retrieve in reverse order
        for (int i = hashes.length - 1; i >= 0; i--) {
            assertThat(index.get(hashes[i])).isEqualTo(offsets[i]);
        }
    }

    // ==================== Overwrite Tests ====================

    @Test
    @DisplayName("Should overwrite existing entry with new offset")
    void testOverwriteExistingEntry() {
        long hash = 12345L;
        long offset1 = 1000L;
        long offset2 = 2000L;
        
        index.put(hash, offset1);
        assertThat(index.get(hash)).isEqualTo(offset1);
        
        index.put(hash, offset2);
        assertThat(index.get(hash)).isEqualTo(offset2);
    }

    @Test
    @DisplayName("Should correctly update entry that was overwritten")
    void testMultipleOverwrites() {
        long hash = 12345L;
        
        for (int i = 1; i <= 5; i++) {
            long offset = i * 1000L;
            index.put(hash, offset);
            assertThat(index.get(hash)).isEqualTo(offset);
        }
    }

    // ==================== Remove Tests ====================

    @Test
    @DisplayName("Should remove existing entry")
    void testRemoveExistingEntry() {
        long hash = 12345L;
        long offset = 1000L;
        
        index.put(hash, offset);
        assertThat(index.get(hash)).isEqualTo(offset);
        
        index.remove(hash);
        assertThat(index.get(hash)).isEqualTo(-1L);
    }

    @Test
    @DisplayName("Should not error when removing non-existent entry")
    void testRemoveNonExistentEntry() {
        long hash = 99999L;
        
        // Should not throw
        index.remove(hash);
        
        assertThat(index.get(hash)).isEqualTo(-1L);
    }

    @Test
    @DisplayName("Should allow reinserting after removal")
    void testReinsertAfterRemoval() {
        long hash = 12345L;
        long offset1 = 1000L;
        long offset2 = 2000L;
        
        index.put(hash, offset1);
        index.remove(hash);
        index.put(hash, offset2);
        
        assertThat(index.get(hash)).isEqualTo(offset2);
    }

    @Test
    @DisplayName("Should remove only specific entry, keeping others")
    void testRemoveSpecificEntry() {
        long hash1 = 111L;
        long offset1 = 1000L;
        long hash2 = 222L;
        long offset2 = 2000L;
        long hash3 = 333L;
        long offset3 = 3000L;
        
        index.put(hash1, offset1);
        index.put(hash2, offset2);
        index.put(hash3, offset3);
        
        index.remove(hash2);
        
        assertThat(index.get(hash1)).isEqualTo(offset1);
        assertThat(index.get(hash2)).isEqualTo(-1L);
        assertThat(index.get(hash3)).isEqualTo(offset3);
    }

    // ==================== Collision Resolution Tests ====================

    @Test
    @DisplayName("Should handle hash collision via linear probing")
    void testCollisionResolutionLinearProbing() {
        // Simulate collisions by using sequential hashes
        // (depends on implementation details, but should handle them)
        long[] hashes = new long[100];
        long[] offsets = new long[100];
        
        for (int i = 0; i < 100; i++) {
            hashes[i] = i * 1000L; // Simple sequential hashes
            offsets[i] = (i + 1) * 10000L;
            index.put(hashes[i], offsets[i]);
        }
        
        // All should be retrievable
        for (int i = 0; i < 100; i++) {
            assertThat(index.get(hashes[i])).isEqualTo(offsets[i]);
        }
    }

    @Test
    @DisplayName("Should maintain correctness under many insertions")
    void testManyInsertions() {
        int count = 1000;
        
        for (int i = 0; i < count; i++) {
            long hash = (long) i * 31L; // Simple hash distribution
            long offset = (long) i * 1000L;
            index.put(hash, offset);
        }
        
        // Verify all are present
        for (int i = 0; i < count; i++) {
            long hash = (long) i * 31L;
            long offset = (long) i * 1000L;
            assertThat(index.get(hash)).isEqualTo(offset);
        }
    }

    @Test
    @DisplayName("Should grow capacity automatically when needed")
    void testAutoGrowCapacity() {
        // Insert more entries than initial capacity (assume ~16K initial)
        int insertCount = 5000;
        
        for (int i = 0; i < insertCount; i++) {
            long hash = (long) i * 17L;
            long offset = (long) i * 1000L;
            index.put(hash, offset);
        }
        
        // All should still be retrievable after growth
        for (int i = 0; i < insertCount; i++) {
            long hash = (long) i * 17L;
            long offset = (long) i * 1000L;
            assertThat(index.get(hash)).isEqualTo(offset);
        }
    }

    @Test
    @DisplayName("Should maintain correct load factor during growth")
    void testLoadFactorDuringGrowth() {
        // Insert at load factor boundaries
        for (int i = 0; i < 10000; i++) {
            long hash = (long) i * 13L;
            long offset = (long) i * 1000L;
            index.put(hash, offset);
            
            // Spot check a few entries
            if (i % 1000 == 0) {
                for (int j = 0; j <= i; j += 100) {
                    long checkHash = (long) j * 13L;
                    long checkOffset = (long) j * 1000L;
                    assertThat(index.get(checkHash))
                        .as("Hash %d should still be present after %d insertions", checkHash, i)
                        .isEqualTo(checkOffset);
                }
            }
        }
    }

    // ==================== Concurrent Access Tests ====================

    @Test
    @DisplayName("Should handle concurrent puts without data loss")
    void testConcurrentPuts() throws InterruptedException {
        int numThreads = 10;
        int operationsPerThread = 1000;
        long[] allHashes = new long[numThreads * operationsPerThread];
        long[] allOffsets = new long[numThreads * operationsPerThread];
        Thread[] threads = new Thread[numThreads];
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    int idx = threadId * operationsPerThread + i;
                    long hash = ((long) idx * 19L) + threadId;
                    long offset = ((long) idx * 1000L) + threadId;
                    allHashes[idx] = hash;
                    allOffsets[idx] = offset;
                    index.put(hash, offset);
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all entries are present
        for (int i = 0; i < numThreads * operationsPerThread; i++) {
            assertThat(index.get(allHashes[i]))
                .as("Hash at index %d should be retrievable", i)
                .isEqualTo(allOffsets[i]);
        }
    }

    @Test
    @DisplayName("Should handle concurrent gets without errors")
    void testConcurrentGets() throws InterruptedException {
        // Pre-populate index
        int populateCount = 100;
        long[] hashes = new long[populateCount];
        long[] offsets = new long[populateCount];
        
        for (int i = 0; i < populateCount; i++) {
            hashes[i] = (long) i * 23L;
            offsets[i] = (long) i * 1000L;
            index.put(hashes[i], offsets[i]);
        }
        
        // Concurrent reads
        int numThreads = 10;
        int readsPerThread = 100;
        Thread[] threads = new Thread[numThreads];
        
        for (int t = 0; t < numThreads; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < readsPerThread; i++) {
                    for (int j = 0; j < populateCount; j++) {
                        long retrieved = index.get(hashes[j]);
                        assertThat(retrieved).isEqualTo(offsets[j]);
                    }
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
    }

    @Test
    @DisplayName("Should handle mixed concurrent put/get/remove operations")
    void testConcurrentMixedOperations() throws InterruptedException {
        int numThreads = 10;
        int operationsPerThread = 500;
        Thread[] threads = new Thread[numThreads];
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    long hash = ((long) i * 29L) + threadId;
                    long offset = ((long) i * 1000L) + threadId;
                    
                    // Put
                    index.put(hash, offset);
                    
                    // Get
                    long retrieved = index.get(hash);
                    if (retrieved != -1L) {
                        assertThat(retrieved).isEqualTo(offset);
                    }
                    
                    // Occasionally remove and reinsert
                    if (i % 10 == 0) {
                        index.remove(hash);
                        assertThat(index.get(hash)).isEqualTo(-1L);
                        index.put(hash, offset + 1);
                    }
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
    }

    @Test
    @DisplayName("Should not have data races with high concurrency")
    void testNoDataRacesHighConcurrency() throws InterruptedException {
        int numThreads = 20;
        int operationsPerThread = 100;
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(numThreads);
        Thread[] threads = new Thread[numThreads];
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                try {
                    barrier.await(); // Synchronize start
                    
                    for (int i = 0; i < operationsPerThread; i++) {
                        long hash = ((long) i * 37L) + threadId;
                        long offset = ((long) i * 1000L) + threadId;
                        index.put(hash, offset);
                    }
                    
                    barrier.await(); // Synchronize completion
                    
                    // Verify all entries from this thread
                    for (int i = 0; i < operationsPerThread; i++) {
                        long hash = ((long) i * 37L) + threadId;
                        long offset = ((long) i * 1000L) + threadId;
                        long retrieved = index.get(hash);
                        assertThat(retrieved).isEqualTo(offset);
                    }
                } catch (InterruptedException | java.util.concurrent.BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle hash value of 0")
    void testHashZero() {
        long hash = 0L;
        long offset = 1000L;
        
        index.put(hash, offset);
        assertThat(index.get(hash)).isEqualTo(offset);
    }

    @Test
    @DisplayName("Should handle large hash values")
    void testLargeHashValues() {
        long hash = Long.MAX_VALUE;
        long offset = 1000L;
        
        index.put(hash, offset);
        assertThat(index.get(hash)).isEqualTo(offset);
    }

    @Test
    @DisplayName("Should handle negative hash values")
    void testNegativeHashValues() {
        long hash = Long.MIN_VALUE;
        long offset = 1000L;
        
        index.put(hash, offset);
        assertThat(index.get(hash)).isEqualTo(offset);
    }

    @Test
    @DisplayName("Should handle offset value of 0")
    void testOffsetZero() {
        long hash = 12345L;
        long offset = 0L;
        
        index.put(hash, offset);
        assertThat(index.get(hash)).isEqualTo(offset);
    }

    @Test
    @DisplayName("Should handle large offset values")
    void testLargeOffsetValues() {
        long hash = 12345L;
        long offset = Long.MAX_VALUE;
        
        index.put(hash, offset);
        assertThat(index.get(hash)).isEqualTo(offset);
    }

    @Test
    @DisplayName("Should distinguish between offset -1 (not found) and stored offset")
    void testDistinguishNotFoundFromOffset() {
        long hash1 = 111L;
        long offset1 = -2L; // Valid offset, not -1
        long hash2 = 222L;
        
        index.put(hash1, offset1);
        
        assertThat(index.get(hash1)).isEqualTo(offset1);
        assertThat(index.get(hash2)).isEqualTo(-1L); // Not found sentinel
    }

    // ==================== Stress Tests ====================

    @Test
    @DisplayName("Should maintain correctness under stress with many additions and removals")
    void testStressWithManyAdditionsAndRemovals() {
        int iterations = 100;
        
        for (int iter = 0; iter < iterations; iter++) {
            for (int i = 0; i < 100; i++) {
                long hash = (long) i * 41L;
                long offset = (long) i * 1000L;
                index.put(hash, offset);
            }
            
            // Remove every other entry
            for (int i = 0; i < 100; i += 2) {
                long hash = (long) i * 41L;
                index.remove(hash);
            }
            
            // Verify remaining entries
            for (int i = 1; i < 100; i += 2) {
                long hash = (long) i * 41L;
                long offset = (long) i * 1000L;
                assertThat(index.get(hash)).isEqualTo(offset);
            }
            
            // Re-insert removed entries
            for (int i = 0; i < 100; i += 2) {
                long hash = (long) i * 41L;
                long offset = (long) i * 1000L;
                index.put(hash, offset);
            }
        }
    }

    @Test
    @DisplayName("Should perform efficiently with large number of entries")
    void testLargeNumberOfEntries() {
        int entryCount = 100_000;
        
        // Insert
        long startInsert = System.nanoTime();
        for (int i = 0; i < entryCount; i++) {
            long hash = (long) i * 43L;
            long offset = (long) i * 1000L;
            index.put(hash, offset);
        }
        long insertTime = System.nanoTime() - startInsert;
        
        // Retrieve all
        long startRetrieve = System.nanoTime();
        for (int i = 0; i < entryCount; i++) {
            long hash = (long) i * 43L;
            long offset = (long) i * 1000L;
            assertThat(index.get(hash)).isEqualTo(offset);
        }
        long retrieveTime = System.nanoTime() - startRetrieve;
        
        // Performance check: should complete reasonably fast
        long maxInsertTime = 10_000_000_000L; // 10 seconds
        long maxRetrieveTime = 5_000_000_000L; // 5 seconds
        
        assertThat(insertTime).isLessThan(maxInsertTime);
        assertThat(retrieveTime).isLessThan(maxRetrieveTime);
    }
}
