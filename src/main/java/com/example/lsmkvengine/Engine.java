package com.example.lsmkvengine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Single-writer LSM-Tree Key-Value Engine.
 * Thread safety: serialized via synchronized monitor locks.
 */
public class Engine implements AutoCloseable {
    public static final String TOMBSTONE = "__LSM_TOMBSTONE_VAL__";

    private final Memtable memtable = new Memtable();
    private final List<SSTable> sstables = new ArrayList<>();
    private final WAL wal;
    private final File dataDir;
    private final LeveledCompactionManager compactionManager;
    private static final int MEMTABLE_THRESHOLD = 32_000;

    public Engine(File dataDir) throws IOException {
        this.dataDir = dataDir;
        if (!dataDir.exists()) dataDir.mkdirs();
        this.wal = new WAL(new File(dataDir, "commit.wal"));
        this.compactionManager = new LeveledCompactionManager(dataDir, 4);

        File[] existingFiles = dataDir.listFiles((dir, name) -> name.endsWith(".db"));
        if (existingFiles != null) {
            java.util.Arrays.sort(existingFiles, java.util.Comparator.comparingLong(File::lastModified));
            for (File f : existingFiles) {
                sstables.add(new SSTable(f));
            }
        }
    }

    public synchronized void put(String key, String value) throws IOException {
        wal.append(key, value);
        memtable.put(key, value);
        if (memtable.size() >= MEMTABLE_THRESHOLD) {
            flush();
        }
    }

    public synchronized void delete(String key) throws IOException {
        put(key, TOMBSTONE);
    }

    public synchronized String get(String key) throws IOException {
        String val = memtable.get(key);
        if (val != null) {
            return TOMBSTONE.equals(val) ? null : val;
        }
        for (int i = sstables.size() - 1; i >= 0; i--) {
            val = sstables.get(i).get(key);
            if (val != null) {
                return TOMBSTONE.equals(val) ? null : val;
            }
        }
        return null;
    }

    public synchronized void flush() throws IOException {
        if (memtable.size() == 0) return;
        File sstableFile = new File(dataDir, "sstable-L0-" + System.nanoTime() + ".db");
        SSTable table = SSTable.flush(memtable, sstableFile);
        sstables.add(table);
        memtable.clear();
        wal.clear();
        compactionManager.registerL0Table(sstableFile);
    }

    @Override
    public void close() throws IOException {
        compactionManager.shutdown();
        wal.close();
    }
}
