package com.example.lsmkvengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;

public class SSTableFormatTest {

    @TempDir
    Path tempDir;

    @Test
    public void testBlockStructureAndBinarySearch() throws IOException {
        File sstFile = tempDir.resolve("format_test.sst").toFile();
        Memtable mem = new Memtable();

        for (int i = 0; i < 1000; i++) {
            String key = String.format("sensor:%05d", i);
            String val = "payload_measurement_value_" + i;
            mem.put(key, val);
        }

        SSTable sst = SSTable.flush(mem, sstFile, 1);

        Assertions.assertEquals("payload_measurement_value_0", sst.get("sensor:00000"));
        Assertions.assertEquals("payload_measurement_value_499", sst.get("sensor:00499"));
        Assertions.assertEquals("payload_measurement_value_999", sst.get("sensor:00999"));

        Assertions.assertNull(sst.get("sensor:99999"));
        Assertions.assertNull(sst.get("unknown_key"));
    }

    @Test
    public void testDataBlockCrcCorruption() throws IOException {
        File sstFile = tempDir.resolve("crc_corruption.sst").toFile();
        Memtable mem = new Memtable();
        for (int i = 0; i < 200; i++) {
            mem.put("sensor:" + i, "val:" + i);
        }
        SSTable.flush(mem, sstFile);

        // Corrupt block payload byte so CRC validation fails on load
        try (RandomAccessFile raf = new RandomAccessFile(sstFile, "rw")) {
            raf.seek(24);
            byte b = raf.readByte();
            raf.seek(24);
            raf.writeByte((byte)(b ^ 0xFF));
        }

        Assertions.assertThrows(IOException.class, () -> new SSTable(sstFile));
    }
}
