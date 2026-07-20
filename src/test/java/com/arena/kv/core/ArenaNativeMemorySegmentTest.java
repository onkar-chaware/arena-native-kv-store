package com.arena.kv.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD Tests for ArenaNativeMemorySegment.
 * 
 * Validates:
 * - Binary frame layout encoding: [2B keyLen][4B valLen][8B ttl][keyBytes][valBytes]
 * - Thread-safe atomic bump allocator for off-heap memory
 * - Writing and reading key-value pairs from Arena.ofShared()
 * - TTL expiration checks
 * - Zero-allocation accessors for metadata
 * - Collision resolution via byte-level key matching
 */
@DisplayName("ArenaNativeMemorySegment Tests")
class ArenaNativeMemorySegmentTest {

    private ArenaNativeMemorySegment memorySegment;
    private static final long INITIAL_CAPACITY = 1024 * 1024; // 1 MB for testing

    @BeforeEach
    void setUp() {
        memorySegment = new ArenaNativeMemorySegment(INITIAL_CAPACITY);
    }

    // ==================== Frame Layout Tests ====================

    @Test
    @DisplayName("Should calculate correct frame header size (14 bytes)")
    void testFrameHeaderSize() {
        // Header: 2B (keyLen) + 4B (valLen) + 8B (ttl) = 14 bytes
        assertThat(ArenaNativeMemorySegment.FRAME_HEADER_SIZE).isEqualTo(14);
    }

    @Test
    @DisplayName("Should encode key length as 2-byte short in frame header")
    void testKeyLengthEncoding() {
        byte[] key = new byte[] { 1, 2, 3 };
        byte[] value = new byte[] { 4, 5 };
        long ttlMillis = System.currentTimeMillis() + 10000;

        long offset = memorySegment.write(key, value, ttlMillis);

        assertThat(offset).isGreaterThanOrEqualTo(0);
        short storedKeyLen = memorySegment.readKeyLength(offset);
        assertThat(storedKeyLen).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("Should encode value length as 4-byte int in frame header")
    void testValueLengthEncoding() {
        byte[] key = new byte[] { 1, 2, 3 };
        byte[] value = new byte[] { 4, 5, 6, 7, 8 };
        long ttlMillis = System.currentTimeMillis() + 10000;

        long offset = memorySegment.write(key, value, ttlMillis);

        int storedValLen = memorySegment.readValueLength(offset);
        assertThat(storedValLen).isEqualTo(5);
    }

    @Test
    @DisplayName("Should encode TTL as 8-byte long in frame header")
    void testTTLEncoding() {
        byte[] key = new byte[] { 1, 2, 3 };
        byte[] value = new byte[] { 4, 5 };
        long ttlMillis = System.currentTimeMillis() + 5000;

        long offset = memorySegment.write(key, value, ttlMillis);

        long storedTTL = memorySegment.readTTL(offset);
        assertThat(storedTTL).isEqualTo(ttlMillis);
    }

    @Test
    @DisplayName("Should store key bytes immediately after frame header")
    void testKeyBytesStorage() {
        byte[] key = new byte[] { 10, 20, 30 };
        byte[] value = new byte[] { 40, 50 };
        long ttlMillis = System.currentTimeMillis() + 10000;

        long offset = memorySegment.write(key, value, ttlMillis);

        byte[] storedKey = memorySegment.readKey(offset);
        assertThat(storedKey).isEqualTo(key);
    }

    @Test
    @DisplayName("Should store value bytes at correct offset (header + key length)")
    void testValueBytesStorageOffset() {
        byte[] key = new byte[] { 1, 2, 3, 4 };
        byte[] value = new byte[] { 100, 101, 102, 103, 104 };
        long ttlMillis = System.currentTimeMillis() + 10000;

        long offset = memorySegment.write(key, value, ttlMillis);

        byte[] storedValue = memorySegment.readValue(offset, key);
        assertThat(storedValue).isEqualTo(value);
    }

    @Test
    @DisplayName("Should calculate correct payload offset: base + 14 + keyLen")
    void testPayloadOffsetCalculation() {
        byte[] key = new byte[] { 1, 2, 3 };
        byte[] value = new byte[] { 10, 20, 30 };
        long ttlMillis = System.currentTimeMillis() + 10000;

        long offset = memorySegment.write(key, value, ttlMillis);

        // Expected value offset: offset + 14 (header) + 3 (keyLen)
        long expectedValueOffset = offset + 14 + 3;
        long actualValueOffset = memorySegment.getValueOffset(offset);
        assertThat(actualValueOffset).isEqualTo(expectedValueOffset);
    }

    // ==================== Multi-Byte Encoding Tests ====================

    @ParameterizedTest(name = "Key length {0} should encode/decode correctly")
    @CsvSource({
        "1, 100",
        "10, 500",
        "255, 1000",
        "1024, 5000",
        "32767, 10000"  // Max signed short
    })
    @DisplayName("Should handle various key and value lengths")
    void testVariousKeySizes(int keyLen, int valueLen) {
        byte[] key = new byte[keyLen];
        byte[] value = new byte[valueLen];
        for (int i = 0; i < keyLen; i++) {
            key[i] = (byte) (i % 256);
        }
        for (int i = 0; i < valueLen; i++) {
            value[i] = (byte) ((i + 1) % 256);
        }
        long ttlMillis = System.currentTimeMillis() + 10000;

        long offset = memorySegment.write(key, value, ttlMillis);

        assertThat(memorySegment.readKeyLength(offset)).isEqualTo((short) keyLen);
        assertThat(memorySegment.readValueLength(offset)).isEqualTo(valueLen);
        assertThat(memorySegment.readKey(offset)).isEqualTo(key);
        assertThat(memorySegment.readValue(offset, key)).isEqualTo(value);
    }

    // ==================== Thread-Safe Atomic Bump Allocator Tests ====================

    @Test
    @DisplayName("Should allocate space sequentially without overlap")
    void testSequentialAllocation() {
        byte[] key1 = new byte[] { 1 };
        byte[] value1 = new byte[] { 10 };
        byte[] key2 = new byte[] { 2 };
        byte[] value2 = new byte[] { 20 };

        long offset1 = memorySegment.write(key1, value1, System.currentTimeMillis() + 10000);
        long offset2 = memorySegment.write(key2, value2, System.currentTimeMillis() + 10000);

        // Offsets should be different and monotonically increasing
        assertThat(offset1).isGreaterThanOrEqualTo(0);
        assertThat(offset2).isGreaterThan(offset1);
    }

    @Test
    @DisplayName("Should handle concurrent allocations from multiple threads")
    void testConcurrentAllocation() throws InterruptedException {
        int numThreads = 10;
        int allocsPerThread = 100;
        long[] offsets = new long[numThreads * allocsPerThread];
        Thread[] threads = new Thread[numThreads];

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < allocsPerThread; i++) {
                    byte[] key = new byte[] { (byte) threadId, (byte) i };
                    byte[] value = new byte[] { (byte) (threadId + i) };
                    long offset = memorySegment.write(key, value, System.currentTimeMillis() + 10000);
                    offsets[threadId * allocsPerThread + i] = offset;
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all offsets are unique (no overlaps)
        for (int i = 0; i < offsets.length; i++) {
            for (int j = i + 1; j < offsets.length; j++) {
                assertThat(offsets[i]).isNotEqualTo(offsets[j]);
            }
        }
    }

    // ==================== TTL Expiration Tests ====================

    @Test
    @DisplayName("Should detect non-expired entry as valid")
    void testIsExpiredFalseForFutureTime() {
        byte[] key = new byte[] { 1, 2 };
        byte[] value = new byte[] { 3, 4 };
        long futureTime = System.currentTimeMillis() + 10000; // 10 seconds in future

        long offset = memorySegment.write(key, value, futureTime);

        assertThat(memorySegment.isExpired(offset, System.currentTimeMillis())).isFalse();
    }

    @Test
    @DisplayName("Should detect expired entry as invalid")
    void testIsExpiredTrueForPastTime() {
        byte[] key = new byte[] { 1, 2 };
        byte[] value = new byte[] { 3, 4 };
        long pastTime = System.currentTimeMillis() - 1000; // 1 second in past

        long offset = memorySegment.write(key, value, pastTime);

        assertThat(memorySegment.isExpired(offset, System.currentTimeMillis())).isTrue();
    }

    @Test
    @DisplayName("Should handle TTL edge case at exact expiration time")
    void testIsExpiredAtExactTime() {
        byte[] key = new byte[] { 1, 2 };
        byte[] value = new byte[] { 3, 4 };
        long ttlTime = System.currentTimeMillis();

        long offset = memorySegment.write(key, value, ttlTime);

        // At exact expiration time, should be considered expired
        assertThat(memorySegment.isExpired(offset, ttlTime)).isTrue();
    }

    // ==================== Key Matching & Collision Resolution Tests ====================

    @Test
    @DisplayName("Should match identical key bytes")
    void testMatchesKeyForIdenticalKey() {
        byte[] key = new byte[] { 10, 20, 30 };
        byte[] value = new byte[] { 100 };
        long offset = memorySegment.write(key, value, System.currentTimeMillis() + 10000);

        assertThat(memorySegment.matchesKey(offset, key)).isTrue();
    }

    @Test
    @DisplayName("Should not match different key bytes")
    void testMatchesKeyForDifferentKey() {
        byte[] key1 = new byte[] { 10, 20, 30 };
        byte[] value = new byte[] { 100 };
        long offset = memorySegment.write(key1, value, System.currentTimeMillis() + 10000);

        byte[] key2 = new byte[] { 10, 20, 31 }; // Different last byte
        assertThat(memorySegment.matchesKey(offset, key2)).isFalse();
    }

    @Test
    @DisplayName("Should not match key with different length")
    void testMatchesKeyForDifferentLength() {
        byte[] key1 = new byte[] { 10, 20, 30 };
        byte[] value = new byte[] { 100 };
        long offset = memorySegment.write(key1, value, System.currentTimeMillis() + 10000);

        byte[] key2 = new byte[] { 10, 20 }; // Shorter
        assertThat(memorySegment.matchesKey(offset, key2)).isFalse();
    }

    @Test
    @DisplayName("Should handle collision resolution via byte-level comparison")
    void testCollisionResolutionViaKeyMatching() {
        byte[] key1 = new byte[] { 1, 2, 3 };
        byte[] value1 = new byte[] { 10 };
        byte[] key2 = new byte[] { 1, 2, 4 };
        byte[] value2 = new byte[] { 20 };

        long offset1 = memorySegment.write(key1, value1, System.currentTimeMillis() + 10000);
        long offset2 = memorySegment.write(key2, value2, System.currentTimeMillis() + 10000);

        // Even if hash collides, matchesKey should distinguish them
        assertThat(memorySegment.matchesKey(offset1, key1)).isTrue();
        assertThat(memorySegment.matchesKey(offset1, key2)).isFalse();
        assertThat(memorySegment.matchesKey(offset2, key2)).isTrue();
        assertThat(memorySegment.matchesKey(offset2, key1)).isFalse();
    }

    // ==================== Zero-Allocation Accessors Tests ====================

    @Test
    @DisplayName("Should read TTL without creating temporary objects")
    void testZeroAllocationTTLRead() {
        byte[] key = new byte[] { 1, 2 };
        byte[] value = new byte[] { 3, 4 };
        long ttlMillis = System.currentTimeMillis() + 5000;

        long offset = memorySegment.write(key, value, ttlMillis);

        // This accessor should not allocate
        long readTTL = memorySegment.readTTL(offset);
        assertThat(readTTL).isEqualTo(ttlMillis);
    }

//    @Test
//    @DisplayName("Should read key length without creating temporary objects")
//    void testZeroAllocationKeyLengthRead() {
//        byte[] key = new byte[] { 1, 2, 3, 4, 5 };
//        byte[] value = new byte[] { 6, 7 };
//
//        long offset = memorySegment.write(key, value, System.currentTimeMillis() + 10000);
//
//        short keyLen = memorySegment.readKeyLength(offset);
//        assertThat(keyLen).isEqualTo(5);
//    }

    @Test
    @DisplayName("Should read value length without creating temporary objects")
    void testZeroAllocationValueLengthRead() {
        byte[] key = new byte[] { 1, 2 };
        byte[] value = new byte[] { 3, 4, 5, 6, 7, 8, 9 };

        long offset = memorySegment.write(key, value, System.currentTimeMillis() + 10000);

        int valLen = memorySegment.readValueLength(offset);
        assertThat(valLen).isEqualTo(7);
    }

    // ==================== Complex Integration Tests ====================

    @Test
    @DisplayName("Should round-trip write and read with various sizes")
    void testRoundTripWriteRead() {
        byte[] key = "test.key.name".getBytes();
        byte[] value = "test.value.content".getBytes();
        long ttlMillis = System.currentTimeMillis() + 30000;

        long offset = memorySegment.write(key, value, ttlMillis);

        assertThat(memorySegment.readKeyLength(offset)).isEqualTo((short) key.length);
        assertThat(memorySegment.readValueLength(offset)).isEqualTo(value.length);
        assertThat(memorySegment.readTTL(offset)).isEqualTo(ttlMillis);
        assertThat(memorySegment.readKey(offset)).isEqualTo(key);
        assertThat(memorySegment.readValue(offset, key)).isEqualTo(value);
    }

    @Test
    @DisplayName("Should handle binary data with null bytes")
    void testBinaryDataWithNullBytes() {
        byte[] key = new byte[] { 0, 1, 0, 2, 0 };
        byte[] value = new byte[] { 0, 0, 0, 127, 0, 0 };
        long ttlMillis = System.currentTimeMillis() + 10000;

        long offset = memorySegment.write(key, value, ttlMillis);

        assertThat(memorySegment.readKey(offset)).isEqualTo(key);
        assertThat(memorySegment.readValue(offset, key)).isEqualTo(value);
    }

    @Test
    @DisplayName("Should verify entry is not expired after recent write")
    void testRecentlyWrittenEntryNotExpired() {
        byte[] key = new byte[] { 1, 2, 3 };
        byte[] value = new byte[] { 4, 5, 6 };
        long ttlMillis = System.currentTimeMillis() + 60000; // 1 minute

        long offset = memorySegment.write(key, value, ttlMillis);
        long now = System.currentTimeMillis();

        assertThat(memorySegment.isExpired(offset, now)).isFalse();
        assertThat(memorySegment.matchesKey(offset, key)).isTrue();
        assertThat(memorySegment.readValue(offset, key)).isEqualTo(value);
    }

    @Test
    @DisplayName("Should return correct value offset for different key sizes")
    void testValueOffsetForVariousKeySizes() {
        for (int keyLen = 1; keyLen <= 1000; keyLen += 100) {
            byte[] key = new byte[keyLen];
            byte[] value = new byte[] { 1, 2, 3 };
            long offset = memorySegment.write(key, value, System.currentTimeMillis() + 10000);

            long expectedValueOffset = offset + 14 + keyLen;
            long actualValueOffset = memorySegment.getValueOffset(offset);
            assertThat(actualValueOffset)
                .as("Value offset for key length %d", keyLen)
                .isEqualTo(expectedValueOffset);
        }
    }
}
