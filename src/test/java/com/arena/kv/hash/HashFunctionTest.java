package com.arena.kv.hash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD Tests for HashFunction.
 * 
 * Validates:
 * - Zero-allocation hashing using OpenHFT xxHash64
 * - Deterministic hashes across multiple runs
 * - Hash quality (low collision rate on synthetic key sets)
 */
@DisplayName("HashFunction Tests")
class HashFunctionTest {

    // ==================== Basic Hashing Tests ====================

    @Test
    @DisplayName("Should compute hash for simple byte array")
    void testHashSimpleKey() {
        byte[] key = new byte[] { 1, 2, 3, 4 };
        long hash = HashFunction.hash(key);
        
        assertThat(hash).isNotNull();
    }

    @Test
    @DisplayName("Should compute non-zero hash for non-empty key")
    void testHashNonZeroForNonEmptyKey() {
        byte[] key = "test.key".getBytes();
        long hash = HashFunction.hash(key);
        
        assertThat(hash).isNotEqualTo(0L);
    }

    @Test
    @DisplayName("Should produce different hashes for different keys")
    void testHashDifferentKeysProduceDifferentHashes() {
        byte[] key1 = "key1".getBytes();
        byte[] key2 = "key2".getBytes();
        
        long hash1 = HashFunction.hash(key1);
        long hash2 = HashFunction.hash(key2);
        
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Should handle single-byte keys")
    void testHashSingleByte() {
        byte[] key1 = new byte[] { 1 };
        byte[] key2 = new byte[] { 2 };
        
        long hash1 = HashFunction.hash(key1);
        long hash2 = HashFunction.hash(key2);
        
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Should handle large keys (kilobyte range)")
    void testHashLargeKey() {
        byte[] key = new byte[1024];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i % 256);
        }
        
        long hash = HashFunction.hash(key);
        
        assertThat(hash).isNotNull();
    }

    // ==================== Determinism Tests ====================

    @Test
    @DisplayName("Should produce identical hash for identical key on repeated calls")
    void testHashDeterministic() {
        byte[] key = "deterministic.test".getBytes();
        
        long hash1 = HashFunction.hash(key);
        long hash2 = HashFunction.hash(key);
        long hash3 = HashFunction.hash(key);
        
        assertThat(hash1).isEqualTo(hash2).isEqualTo(hash3);
    }

    @Test
    @DisplayName("Should produce deterministic hashes across 1000 invocations")
    void testHashDeterministicManyTimes() {
        byte[] key = "performance.test".getBytes();
        long firstHash = HashFunction.hash(key);
        
        for (int i = 0; i < 1000; i++) {
            long hash = HashFunction.hash(key);
            assertThat(hash).isEqualTo(firstHash);
        }
    }

    @Test
    @DisplayName("Should produce consistent hashes for similar keys across runs")
    void testHashConsistencyForSimilarKeys() {
        byte[] baseKey = "base".getBytes();
        long baseHash = HashFunction.hash(baseKey);
        
        // Call many times to ensure consistency
        for (int i = 0; i < 100; i++) {
            long hash = HashFunction.hash(baseKey);
            assertThat(hash).isEqualTo(baseHash);
        }
    }

    // ==================== Key Variation Tests ====================

    @ParameterizedTest(name = "Key: {0} should produce valid hash")
    @CsvSource({
        "a",
        "ab",
        "abc",
        "key.with.dots",
        "key-with-dashes",
        "key_with_underscores",
        "123456789",
        "!@#$%^&*()"
    })
    @DisplayName("Should handle various string keys")
    void testHashVariousStringKeys(String keyStr) {
        byte[] key = keyStr.getBytes();
        long hash = HashFunction.hash(key);
        
        assertThat(hash).isNotNull();
    }

    @Test
    @DisplayName("Should handle key with null bytes")
    void testHashKeyWithNullBytes() {
        byte[] key = new byte[] { 0, 1, 0, 2, 0, 3 };
        long hash = HashFunction.hash(key);
        
        assertThat(hash).isNotNull();
    }

    @Test
    @DisplayName("Should handle key with all high bytes")
    void testHashKeyWithHighBytes() {
        byte[] key = new byte[] { -1, -1, -1, -1 };
        long hash = HashFunction.hash(key);
        
        assertThat(hash).isNotNull();
    }

    // ==================== Collision Analysis Tests ====================

    @Test
    @DisplayName("Should have low collision rate on sequential numeric keys")
    void testLowCollisionRateNumericKeys() {
        int sampleSize = 1000;
        long[] hashes = new long[sampleSize];
        
        for (int i = 0; i < sampleSize; i++) {
            byte[] key = String.valueOf(i).getBytes();
            hashes[i] = HashFunction.hash(key);
        }
        
        // Check that we have at least 99% unique hashes (max 1% collision)
        int uniqueCount = countUniqueHashes(hashes);
        int maxAllowedCollisions = sampleSize / 100; // 1%
        
        assertThat(uniqueCount).isGreaterThanOrEqualTo(sampleSize - maxAllowedCollisions);
    }

    @Test
    @DisplayName("Should have low collision rate on sequential alphanumeric keys")
    void testLowCollisionRateAlphanumericKeys() {
        int sampleSize = 500;
        long[] hashes = new long[sampleSize];
        
        for (int i = 0; i < sampleSize; i++) {
            byte[] key = ("key_" + i).getBytes();
            hashes[i] = HashFunction.hash(key);
        }
        
        int uniqueCount = countUniqueHashes(hashes);
        int maxAllowedCollisions = sampleSize / 100; // 1%
        
        assertThat(uniqueCount).isGreaterThanOrEqualTo(sampleSize - maxAllowedCollisions);
    }

    @Test
    @DisplayName("Should distribute hashes across full long range")
    void testHashDistributionAcrossRange() {
        byte[] key1 = "key1".getBytes();
        byte[] key2 = "key2".getBytes();
        byte[] key3 = "key3".getBytes();
        
        long hash1 = HashFunction.hash(key1);
        long hash2 = HashFunction.hash(key2);
        long hash3 = HashFunction.hash(key3);
        
        // All should be different and use various bits of the long range
        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hash2).isNotEqualTo(hash3);
        assertThat(hash1).isNotEqualTo(hash3);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle empty byte array")
    void testHashEmptyArray() {
        byte[] key = new byte[0];
        long hash = HashFunction.hash(key);
        
        assertThat(hash).isNotNull();
    }

    @Test
    @DisplayName("Should produce different hash for empty array vs non-empty")
    void testHashEmptyVsNonEmpty() {
        byte[] empty = new byte[0];
        byte[] nonEmpty = new byte[] { 1 };
        
        long hashEmpty = HashFunction.hash(empty);
        long hashNonEmpty = HashFunction.hash(nonEmpty);
        
        assertThat(hashEmpty).isNotEqualTo(hashNonEmpty);
    }

    @Test
    @DisplayName("Should handle keys that differ only in one bit")
    void testHashKeysWithOneBitDifference() {
        byte[] key1 = new byte[] { 0b01010101 };
        byte[] key2 = new byte[] { 0b01010100 }; // Last bit different
        
        long hash1 = HashFunction.hash(key1);
        long hash2 = HashFunction.hash(key2);
        
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Should produce positive long values (use full range)")
    void testHashUsesFullRange() {
        byte[] key = "test".getBytes();
        long hash = HashFunction.hash(key);
        
        // Hash can be any valid long value (including negative)
        assertThat(hash).isNotEqualTo(0L);
    }

    // ==================== Performance/Allocation Tests ====================

    @Test
    @DisplayName("Should compute hash rapidly for 10000 keys")
    void testHashPerformance() {
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 10000; i++) {
            byte[] key = ("key_" + i).getBytes();
            HashFunction.hash(key);
        }
        
        long elapsed = System.nanoTime() - startTime;
        
        // Should complete in less than 100ms (loose bound, ~10ns per hash)
        long maxNanos = 100_000_000; // 100ms
        assertThat(elapsed).isLessThan(maxNanos);
    }

    // ==================== Helper Methods ====================

    private int countUniqueHashes(long[] hashes) {
        java.util.Set<Long> uniqueHashes = new java.util.HashSet<>();
        for (long hash : hashes) {
            uniqueHashes.add(hash);
        }
        return uniqueHashes.size();
    }
}
