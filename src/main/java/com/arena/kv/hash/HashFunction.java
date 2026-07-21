package com.arena.kv.hash;

import net.openhft.hashing.LongHashFunction;

/**
 * Zero-allocation hash function using OpenHFT xxHash64.
 * 
 * Provides deterministic, high-quality 64-bit hashes for byte arrays.
 * Designed for use in PrimitiveHashIndex with zero heap allocations on hot path.
 */
public class HashFunction {
    
    private static final LongHashFunction HASH_FUNCTION = LongHashFunction.xx();
    private static final long EMPTY_KEY_HASH = 0x9e3779b97f4a7c15L; // FNV offset basis
    
    /**
     * Compute a 64-bit xxHash64 hash for the given byte array.
     * 
     * @param key the byte array to hash (may be empty)
     * @return a 64-bit hash value (deterministic across invocations)
     */
    public static long hash(byte[] key) {
        if (key == null || key.length == 0) {
            return EMPTY_KEY_HASH;
        }
        
        return HASH_FUNCTION.hashBytes(key);
    }
}
