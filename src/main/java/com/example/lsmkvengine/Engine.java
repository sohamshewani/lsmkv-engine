package com.example.lsmkvengine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Engine {
    private final Memtable memtable = new Memtable();
    private final List<SSTable> sstables = new ArrayList<>();
    private final WAL wal;
    private final File dataDir;

    public Engine(File dataDir) throws IOException {
        this.dataDir = dataDir;
        if (!dataDir.exists()) dataDir.mkdirs();
        this.wal = new WAL(new File(dataDir, "commit.wal"));
    }

    public synchronized void put(String key, String value) throws IOException {
        wal.append(key, value);
        memtable.put(key, value);
        if (memtable.size() >= 1000) {
            flush();
        }
    }

    public synchronized String get(String key) throws IOException {
        String val = memtable.get(key);
        if (val != null) return val;
        for (int i = sstables.size() - 1; i >= 0; i--) {
            val = sstables.get(i).get(key);
            if (val != null) return val;
        }
        return null;
    }

    public synchronized void flush() throws IOException {
        File sstableFile = new File(dataDir, "sstable-" + System.currentTimeMillis() + ".db");
        SSTable table = SSTable.flush(memtable, sstableFile);
        sstables.add(table);
        memtable.clear();
        wal.clear();
    }
}
