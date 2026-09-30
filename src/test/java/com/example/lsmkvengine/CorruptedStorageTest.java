package com.example.lsmkvengine;

import com.lsmkv.wal.WalRecoveryEngine;
import com.lsmkv.wal.WriteAheadLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public class CorruptedStorageTest {

    @TempDir
    Path tempDir;

    @Test
    public void testCorruptedWalTruncation() throws IOException {
        File walFile = tempDir.resolve("corrupt_test.wal").toFile();

        try (WriteAheadLog wal = new WriteAheadLog(walFile)) {
            wal.append("validKey1".getBytes(StandardCharsets.UTF_8), "validVal1".getBytes(StandardCharsets.UTF_8), false);
            wal.append("validKey2".getBytes(StandardCharsets.UTF_8), "validVal2".getBytes(StandardCharsets.UTF_8), false);
        }

        try (FileOutputStream fos = new FileOutputStream(walFile, true)) {
            fos.write(new byte[]{0x1F, 0x2A, 0x3B, 0x4C, 0x5D});
            fos.flush();
        }

        List<WalRecoveryEngine.WalRecord> records = WalRecoveryEngine.recover(walFile);

        Assertions.assertEquals(2, records.size());
        Assertions.assertEquals("validKey1", new String(records.get(0).key, StandardCharsets.UTF_8));
        Assertions.assertEquals("validKey2", new String(records.get(1).key, StandardCharsets.UTF_8));
    }

    @Test
    public void testCorruptedSSTableGracefulHandling() throws IOException {
        File sstableFile = tempDir.resolve("bad_sstable.db").toFile();

        try (FileOutputStream fos = new FileOutputStream(sstableFile)) {
            fos.write("CORRUPTED_ENTRY_WITHOUT_DELIMITER\n".getBytes(StandardCharsets.UTF_8));
            fos.write("keyValid:valueValid\n".getBytes(StandardCharsets.UTF_8));
        }

        SSTable sstable = new SSTable(sstableFile);
        Assertions.assertNull(sstable.get("unknownKey"));
        Assertions.assertEquals("valueValid", sstable.get("keyValid"));
    }
}
