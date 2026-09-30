package com.example.lsmkvengine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

public class SSTable {
    public static final int MAGIC_HEADER = 0x53535442;
    public static final int MAGIC_FOOTER = 0x42545353;
    public static final int VERSION = 1;
    public static final int DEFAULT_BLOCK_SIZE = 4096;

    private final File file;
    private final BloomFilter bloomFilter;
    private final List<IndexEntry> sparseIndex = new ArrayList<>();
    private final Map<String, String> memoryIndex = new LinkedHashMap<>();

    public record IndexEntry(String firstKey, long offset, int blockSize) {}

    public SSTable(File file) throws IOException {
        this.file = file;
        this.bloomFilter = new BloomFilter(10000, 0.01);
        loadTable();
    }

    private void loadTable() throws IOException {
        if (!file.exists()) return;

        // Check if binary header exists
        if (file.length() >= 36) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(0);
                if (raf.readInt() == MAGIC_HEADER) {
                    raf.readInt(); // ver
                    raf.readInt(); // lvl
                    raf.readInt(); // count

                    long fileLength = raf.length();
                    raf.seek(fileLength - 20);
                    long indexOffset = raf.readLong();
                    raf.seek(indexOffset);
                    int indexCount = raf.readInt();
                    for (int i = 0; i < indexCount; i++) {
                        int keyLen = raf.readShort();
                        byte[] keyBytes = new byte[keyLen];
                        raf.readFully(keyBytes);
                        String firstKey = new String(keyBytes, StandardCharsets.UTF_8);
                        long offset = raf.readLong();
                        int blockSize = raf.readInt();
                        sparseIndex.add(new IndexEntry(firstKey, offset, blockSize));
                    }
                }
            } catch (Exception ignored) {}
        }

        // Always read records into index cache for exact O(1) query consistency
        if (!sparseIndex.isEmpty()) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                for (IndexEntry entry : sparseIndex) {
                    raf.seek(entry.offset);
                    byte[] blockData = new byte[entry.blockSize];
                    raf.readFully(blockData);
                    int storedCrc = raf.readInt();

                    CRC32 crc = new CRC32();
                    crc.update(blockData);
                    if ((int) crc.getValue() != storedCrc) {
                        throw new IOException("SSTable data corruption detected! CRC mismatch at offset " + entry.offset);
                    }

                    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(blockData));
                    while (dis.available() > 0) {
                        int kLen = dis.readShort();
                        byte[] kBytes = new byte[kLen];
                        dis.readFully(kBytes);
                        String k = new String(kBytes, StandardCharsets.UTF_8);

                        int vLen = dis.readInt();
                        byte[] vBytes = new byte[vLen];
                        dis.readFully(vBytes);
                        String v = new String(vBytes, StandardCharsets.UTF_8);
                        dis.readLong();
                        boolean isTombstone = dis.readBoolean();

                        bloomFilter.add(k);
                        memoryIndex.put(k, isTombstone ? Engine.TOMBSTONE : v);
                    }
                }
            }
        } else {
            // Text legacy fallback
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int tab = line.indexOf("\t");
                    if (tab != -1) {
                        String k = line.substring(0, tab);
                        String v = line.substring(tab + 1).split("\t")[0];
                        memoryIndex.put(k, v);
                        bloomFilter.add(k);
                    } else {
                        int colon = line.indexOf(":");
                        if (colon != -1) {
                            String k = line.substring(0, colon);
                            String v = line.substring(colon + 1);
                            memoryIndex.put(k, v);
                            bloomFilter.add(k);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    public static SSTable flush(Memtable memtable, File targetFile) throws IOException {
        return flush(memtable, targetFile, 0);
    }

    public static SSTable flush(Memtable memtable, File targetFile, int level) throws IOException {
        int entryCount = memtable.size();
        List<IndexEntry> indexEntries = new ArrayList<>();

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             DataOutputStream dos = new DataOutputStream(bos)) {

            dos.writeInt(MAGIC_HEADER);
            dos.writeInt(VERSION);
            dos.writeInt(level);
            dos.writeInt(entryCount);

            long currentOffset = 16;
            ByteArrayOutputStream blockBuf = new ByteArrayOutputStream(DEFAULT_BLOCK_SIZE);
            DataOutputStream blockDos = new DataOutputStream(blockBuf);
            String firstKeyInBlock = null;

            List<String> sortedKeys = new ArrayList<>(memtable.keySet());
            Collections.sort(sortedKeys);

            for (String key : sortedKeys) {
                String val = memtable.get(key);
                boolean isTombstone = Engine.TOMBSTONE.equals(val);

                if (firstKeyInBlock == null) {
                    firstKeyInBlock = key;
                }

                byte[] kBytes = key.getBytes(StandardCharsets.UTF_8);
                byte[] vBytes = val != null ? val.getBytes(StandardCharsets.UTF_8) : new byte[0];

                blockDos.writeShort(kBytes.length);
                blockDos.write(kBytes);
                blockDos.writeInt(vBytes.length);
                blockDos.write(vBytes);
                blockDos.writeLong(System.currentTimeMillis());
                blockDos.writeBoolean(isTombstone);

                if (blockBuf.size() >= DEFAULT_BLOCK_SIZE) {
                    currentOffset += writeBlock(dos, blockBuf, indexEntries, firstKeyInBlock, currentOffset);
                    blockBuf.reset();
                    firstKeyInBlock = null;
                }
            }

            if (blockBuf.size() > 0) {
                currentOffset += writeBlock(dos, blockBuf, indexEntries, firstKeyInBlock, currentOffset);
                blockBuf.reset();
            }

            long indexOffset = currentOffset;
            dos.writeInt(indexEntries.size());
            currentOffset += 4;
            for (IndexEntry entry : indexEntries) {
                byte[] kb = entry.firstKey.getBytes(StandardCharsets.UTF_8);
                dos.writeShort(kb.length);
                dos.write(kb);
                dos.writeLong(entry.offset);
                dos.writeInt(entry.blockSize);
                currentOffset += 2 + kb.length + 8 + 4;
            }

            long bloomOffset = currentOffset;
            dos.writeInt(0);
            dos.writeInt(0);
            currentOffset += 8;

            dos.writeLong(indexOffset);
            dos.writeLong(bloomOffset);
            dos.writeInt(MAGIC_FOOTER);
            dos.flush();
        }

        return new SSTable(targetFile);
    }

    private static int writeBlock(DataOutputStream dos, ByteArrayOutputStream blockBuf,
                                  List<IndexEntry> indexEntries, String firstKey, long currentOffset) throws IOException {
        byte[] data = blockBuf.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(data);
        long checksum = crc.getValue();

        dos.write(data);
        dos.writeInt((int) checksum);

        indexEntries.add(new IndexEntry(firstKey, currentOffset, data.length));
        return data.length + 4;
    }

    public String get(String key) throws IOException {
        // Fast-path O(1) in-memory index
        return memoryIndex.get(key);
    }

    public File getFile() {
        return file;
    }
}
