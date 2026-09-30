package com.example.lsmkvengine;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

public class BloomFilter implements Serializable {
    private final BitSet bitSet;
    private final int bitSetSize;
    private final int numHashFunctions;

    public BloomFilter(int expectedEntries, double falsePositiveRate) {
        this.bitSetSize = Math.max(64, (int) (-expectedEntries * Math.log(falsePositiveRate) / (Math.log(2) * Math.log(2))));
        this.numHashFunctions = Math.max(1, (int) ((bitSetSize / (double) expectedEntries) * Math.log(2)));
        this.bitSet = new BitSet(bitSetSize);
    }

    public BloomFilter(byte[] rawBytes, int numHashFunctions) {
        this.bitSet = BitSet.valueOf(rawBytes);
        this.bitSetSize = rawBytes.length * 8;
        this.numHashFunctions = numHashFunctions;
    }

    public void add(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        int hash1 = Murmur3.hash32(bytes, 0);
        int hash2 = Murmur3.hash32(bytes, hash1);

        for (int i = 0; i < numHashFunctions; i++) {
            int combinedHash = hash1 + (i * hash2);
            if (combinedHash < 0) combinedHash = ~combinedHash;
            bitSet.set(combinedHash % bitSetSize);
        }
    }

    public boolean mightContain(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        int hash1 = Murmur3.hash32(bytes, 0);
        int hash2 = Murmur3.hash32(bytes, hash1);

        for (int i = 0; i < numHashFunctions; i++) {
            int combinedHash = hash1 + (i * hash2);
            if (combinedHash < 0) combinedHash = ~combinedHash;
            if (!bitSet.get(combinedHash % bitSetSize)) {
                return false;
            }
        }
        return true;
    }

    public byte[] toByteArray() {
        return bitSet.toByteArray();
    }

    public int getNumHashFunctions() {
        return numHashFunctions;
    }

    private static class Murmur3 {
        public static int hash32(byte[] data, int seed) {
            int c1 = 0xcc9e2d51;
            int c2 = 0x1b873593;
            int h1 = seed;
            int roundedEnd = (data.length & 0xfffffffc);

            for (int i = 0; i < roundedEnd; i += 4) {
                int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
                h1 = Integer.rotateLeft(h1, 13);
                h1 = h1 * 5 + 0xe6546b64;
            }

            int k1 = 0;
            int tail = data.length & 0x03;
            if (tail == 3) k1 ^= (data[roundedEnd + 2] & 0xff) << 16;
            if (tail >= 2) k1 ^= (data[roundedEnd + 1] & 0xff) << 8;
            if (tail >= 1) {
                k1 ^= (data[roundedEnd] & 0xff);
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
            }

            h1 ^= data.length;
            h1 ^= (h1 >>> 16);
            h1 *= 0x85ebca6b;
            h1 ^= (h1 >>> 13);
            h1 *= 0xc2b2ae35;
            h1 ^= (h1 >>> 16);
            return h1;
        }
    }
}
