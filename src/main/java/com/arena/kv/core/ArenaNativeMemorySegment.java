package com.arena.kv.core;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Off-heap memory management using Java 21+ Foreign Function & Memory (FFM) API.
 * 
 * Provides thread-safe allocation and serialization of key-value pairs with TTL support.
 * 
 * Frame Layout (14-byte header + variable payload):
 * [2B keyLen][4B valLen][8B ttl][keyBytes...][valBytes...]
 * 
 * All hot-path methods must maintain zero-allocation guarantees.
 */
public class ArenaNativeMemorySegment {
    
    public static final int FRAME_HEADER_SIZE = 14; // 2 + 4 + 8
    private static final int PAYLOAD_OFFSET = 14;

    private final Arena arena;
    private final MemorySegment segment;
    private final AtomicLong allocator;
    private final long capacity;

    /**
     * Initialize an off-heap memory segment with given capacity.
     */
    public ArenaNativeMemorySegment(long capacity) {
        this.capacity = capacity;
        this.arena = Arena.ofShared();
        this.segment = arena.allocate(capacity);
        this.allocator = new AtomicLong(0);
    }

    /**
     * Thread-safe write of key-value pair to off-heap memory.
     * Returns the offset to the entry in the segment.
     */
    public long write(byte[] key, byte[] value, long ttlMillis) {
        int frameSize = FRAME_HEADER_SIZE + key.length + value.length;
        long offset = allocator.getAndAdd(frameSize);
        
        if (offset + frameSize > capacity) {
            throw new IllegalStateException("Out of memory");
        }

        // Write key length (2 bytes, little-endian short)
        writeShort(offset, (short) key.length);
        
        // Write value length (4 bytes, little-endian int)
        writeInt(offset + 2, value.length);
        
        // Write TTL (8 bytes, little-endian long)
        writeLong(offset + 6, ttlMillis);
        
        // Write key bytes starting at offset 14
        for (int i = 0; i < key.length; i++) {
            writeByte(offset + PAYLOAD_OFFSET + i, key[i]);
        }
        
        // Write value bytes
        int valueOffset = PAYLOAD_OFFSET + key.length;
        for (int i = 0; i < value.length; i++) {
            writeByte(offset + valueOffset + i, value[i]);
        }

        return offset;
    }

    private void writeShort(long offset, short value) {
        writeByte(offset, (byte) (value & 0xFF));              // Low byte first (little-endian)
        writeByte(offset + 1, (byte) ((value >> 8) & 0xFF));   // High byte second
    }

    private void writeInt(long offset, int value) {
        writeByte(offset, (byte) (value & 0xFF));              // Little-endian
        writeByte(offset + 1, (byte) ((value >> 8) & 0xFF));
        writeByte(offset + 2, (byte) ((value >> 16) & 0xFF));
        writeByte(offset + 3, (byte) ((value >> 24) & 0xFF));
    }

    private void writeLong(long offset, long value) {
        writeByte(offset, (byte) (value & 0xFF));              // Little-endian
        writeByte(offset + 1, (byte) ((value >> 8) & 0xFF));
        writeByte(offset + 2, (byte) ((value >> 16) & 0xFF));
        writeByte(offset + 3, (byte) ((value >> 24) & 0xFF));
        writeByte(offset + 4, (byte) ((value >> 32) & 0xFF));
        writeByte(offset + 5, (byte) ((value >> 40) & 0xFF));
        writeByte(offset + 6, (byte) ((value >> 48) & 0xFF));
        writeByte(offset + 7, (byte) ((value >> 56) & 0xFF));
    }

    private void writeByte(long offset, byte value) {
        segment.setAtIndex(ValueLayout.JAVA_BYTE, offset, value);
    }

    /**
     * Read the key length from frame header (zero-allocation).
     */
    public short readKeyLength(long offset) {
        return readShort(offset);
    }

    /**
     * Read the value length from frame header (zero-allocation).
     */
    public int readValueLength(long offset) {
        return readInt(offset + 2);
    }

    /**
     * Read the TTL from frame header (zero-allocation).
     */
    public long readTTL(long offset) {
        return readLong(offset + 6);
    }

    private short readShort(long offset) {
        byte b1 = readByte(offset);
        byte b2 = readByte(offset + 1);
        return (short) ((b1 & 0xFF) | ((b2 & 0xFF) << 8));  // Little-endian
    }

    private int readInt(long offset) {
        byte b1 = readByte(offset);
        byte b2 = readByte(offset + 1);
        byte b3 = readByte(offset + 2);
        byte b4 = readByte(offset + 3);
        return (b1 & 0xFF) | ((b2 & 0xFF) << 8) | ((b3 & 0xFF) << 16) | ((b4 & 0xFF) << 24);  // Little-endian
    }

    private long readLong(long offset) {
        byte b1 = readByte(offset);
        byte b2 = readByte(offset + 1);
        byte b3 = readByte(offset + 2);
        byte b4 = readByte(offset + 3);
        byte b5 = readByte(offset + 4);
        byte b6 = readByte(offset + 5);
        byte b7 = readByte(offset + 6);
        byte b8 = readByte(offset + 7);
        return ((long) (b1 & 0xFF)) |
               (((long) (b2 & 0xFF)) << 8) |
               (((long) (b3 & 0xFF)) << 16) |
               (((long) (b4 & 0xFF)) << 24) |
               (((long) (b5 & 0xFF)) << 32) |
               (((long) (b6 & 0xFF)) << 40) |
               (((long) (b7 & 0xFF)) << 48) |
               (((long) (b8 & 0xFF)) << 56);
    }

    private byte readByte(long offset) {
        return segment.getAtIndex(ValueLayout.JAVA_BYTE, offset);
    }

    /**
     * Read the stored key bytes.
     */
    public byte[] readKey(long offset) {
        short keyLen = readKeyLength(offset);
        byte[] key = new byte[keyLen];
        for (int i = 0; i < keyLen; i++) {
            key[i] = readByte(offset + PAYLOAD_OFFSET + i);
        }
        return key;
    }

    /**
     * Read the stored value bytes if the key matches.
     */
    public byte[] readValue(long offset, byte[] key) {
        if (!matchesKey(offset, key)) {
            return null;
        }
        
        int valueLen = readValueLength(offset);
        int valueOffset = PAYLOAD_OFFSET + key.length;
        byte[] value = new byte[valueLen];
        for (int i = 0; i < valueLen; i++) {
            value[i] = readByte(offset + valueOffset + i);
        }
        return value;
    }

    /**
     * Check if the stored key matches the given key (collision resolution).
     */
    public boolean matchesKey(long offset, byte[] key) {
        short storedKeyLen = readKeyLength(offset);
        if (storedKeyLen != key.length) {
            return false;
        }
        
        for (int i = 0; i < key.length; i++) {
            byte storedByte = readByte(offset + PAYLOAD_OFFSET + i);
            if (storedByte != key[i]) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Get the offset where value bytes are stored (zero-allocation).
     */
    public long getValueOffset(long offset) {
        short keyLen = readKeyLength(offset);
        return offset + FRAME_HEADER_SIZE + keyLen;
    }

    /**
     * Check if an entry has expired based on current time.
     */
    public boolean isExpired(long offset, long nowMillis) {
        long ttl = readTTL(offset);
        return nowMillis >= ttl;
    }

    /**
     * Release native memory resources.
     */
    public void close() {
        arena.close();
    }
}