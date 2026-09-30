package com.example.lsmkvengine;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-grade LSM-Tree Key-Value Engine.
 *
 * <p><b>Concurrency Model:</b></p>
 * Single-Writer architecture. All public operations (Put, Get, Delete, Flush, Close)
 * are synchronized on the engine instance monitor.
 *
 * <p><b>Future Architecture Target:</b></p>
 * <pre>
 *   multiple readers
 *          ↓
 *      MemTable (Lock-free ConcurrentSkipListMap)
 *          ↓
 *   background flush (Immutable MemTable swap)
 *          ↓
 *       SSTables (L0..LN)
 *          ↓
 *  background compaction (Isolated multi-way merge)
 * </pre>
 */
public class Engine implements Closeable, AutoCloseable {
    public static final String TOMBSTONE = "__LSM_TOMBSTONE__";

    private final File dataDir;
    private final WAL wal;
    private Memtable memtable;
    private final List<SSTable> ssTables;
    private final int memtableThreshold;
    private boolean closed = false;

    public Engine(File dataDir) throws IOException {
        this(dataDir, 1000);
    }

    public Engine(File dataDir, int memtableThreshold) throws IOException {
        this.dataDir = dataDir;
        this.memtableThreshold = memtableThreshold;
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        File walFile = new File(dataDir, "wal.log");
        this.wal = new WAL(walFile);
        this.memtable = new Memtable();
        this.ssTables = new ArrayList<>();

        // 1. Recover state from WAL
        wal.recover(this.memtable);

        // 2. Discover existing SSTables in directory
        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".sst"));
        if (files != null) {
            for (File file : files) {
                try {
                    ssTables.add(new SSTable(file));
                } catch (Exception ignored) {}
            }
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Engine is closed.");
        }
    }

    /* =========================================================================
     * Minimal Public Interface
     * ========================================================================= */

    /**
     * Put(key, value): Writes a key-value record to the WAL and active Memtable.
     */
    public synchronized void Put(String key, String value) throws IOException {
        put(key, value);
    }

    public synchronized void put(String key, String value) throws IOException {
        ensureOpen();
        wal.append(key, value);
        memtable.put(key, value);
        if (memtable.size() >= memtableThreshold) {
            flush();
        }
    }

    /**
     * Get(key): Retrieves the latest value for a key across Memtable and SSTables.
     * Returns null if key does not exist or was deleted.
     */
    public synchronized String Get(String key) throws IOException {
        return get(key);
    }

    public synchronized String get(String key) throws IOException {
        ensureOpen();
        // 1. Check active Memtable
        String val = memtable.get(key);
        if (val != null) {
            return TOMBSTONE.equals(val) ? null : val;
        }

        // 2. Hierarchical query across SSTables (newest to oldest)
        for (int i = ssTables.size() - 1; i >= 0; i--) {
            val = ssTables.get(i).get(key);
            if (val != null) {
                return TOMBSTONE.equals(val) ? null : val;
            }
        }

        return null;
    }

    /**
     * Delete(key): Inserts a tombstone marker for the key.
     */
    public synchronized void Delete(String key) throws IOException {
        delete(key);
    }

    public synchronized void delete(String key) throws IOException {
        ensureOpen();
        wal.append(key, TOMBSTONE);
        memtable.put(key, TOMBSTONE);
        if (memtable.size() >= memtableThreshold) {
            flush();
        }
    }

    /**
     * Flush(): Forces an immediate flush of the active Memtable to an on-disk SSTable.
     */
    public synchronized void Flush() throws IOException {
        flush();
    }

    public synchronized void flush() throws IOException {
        ensureOpen();
        if (memtable.isEmpty()) {
            return;
        }

        long sstId = System.currentTimeMillis();
        File sstFile = new File(dataDir, String.format("sstable_%d.sst", sstId));
        SSTable sstable = SSTable.flush(memtable, sstFile);
        ssTables.add(sstable);

        // Reset in-memory buffer and truncate WAL
        memtable = new Memtable();
        wal.clear();
    }

    /**
     * Close(): Flushes pending state, closes storage channels, and halts the engine.
     */
    public synchronized void Close() throws IOException {
        close();
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            try {
                if (!memtable.isEmpty()) {
                    flush();
                }
                wal.close();
            } finally {
                closed = true;
            }
        }
    }

    public synchronized List<SSTable> getSsTables() {
        return new ArrayList<>(ssTables);
    }
}
