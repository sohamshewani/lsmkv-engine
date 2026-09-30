package com.example.lsmkvengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class LsmEngineTest {

    @TempDir
    Path tempDir;

    @Test
    public void testMemtableBasicOperations() {
        Memtable mem = new Memtable();
        mem.put("k1", "v1");
        mem.put("k2", "v2");

        Assertions.assertEquals("v1", mem.get("k1"));
        Assertions.assertEquals("v2", mem.get("k2"));
        Assertions.assertEquals(2, mem.size());

        mem.clear();
        Assertions.assertEquals(0, mem.size());
        Assertions.assertNull(mem.get("k1"));
    }

    @Test
    public void testSSTableFlushAndRead() throws IOException {
        Memtable mem = new Memtable();
        mem.put("user1", "val1");
        mem.put("user2", "val2");

        File sstableFile = tempDir.resolve("test.sst").toFile();
        SSTable sst = SSTable.flush(mem, sstableFile);

        Assertions.assertEquals("val1", sst.get("user1"));
        Assertions.assertEquals("val2", sst.get("user2"));
        Assertions.assertNull(sst.get("unknown"));
    }

    @Test
    public void testEnginePutAndGet() throws IOException {
        File dataDir = tempDir.resolve("engine_data").toFile();
        Engine engine = new Engine(dataDir);

        engine.put("itemA", "100");
        engine.put("itemB", "200");

        Assertions.assertEquals("100", engine.get("itemA"));
        Assertions.assertEquals("200", engine.get("itemB"));
        Assertions.assertNull(engine.get("itemC"));
    }

    @Test
    public void testWalRecoveryReplay() throws IOException {
        File dataDir = tempDir.resolve("wal_data").toFile();
        dataDir.mkdirs();
        File walFile = new File(dataDir, "commit.wal");

        WAL wal = new WAL(walFile);
        wal.append("persisted_key", "persisted_value");

        Engine engine = new Engine(dataDir);
        Recovery recovery = new Recovery(engine, wal);
        recovery.recover();

        Assertions.assertEquals("persisted_value", engine.get("persisted_key"));
    }
}
