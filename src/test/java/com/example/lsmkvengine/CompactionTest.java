package com.example.lsmkvengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.List;

public class CompactionTest {

    @TempDir
    Path tempDir;

    @Test
    public void testCompactionOrderingAndTombstones() throws IOException {
        File olderTable = tempDir.resolve("sstable-old.db").toFile();
        File newerTable = tempDir.resolve("sstable-new.db").toFile();
        File compactedTable = tempDir.resolve("sstable-compacted.db").toFile();

        try (BufferedWriter w = new BufferedWriter(new FileWriter(olderTable))) {
            w.write("user:1\tAlice\t1000\tfalse\n");
            w.write("user:2\tBob\t1001\tfalse\n");
        }

        try (BufferedWriter w = new BufferedWriter(new FileWriter(newerTable))) {
            w.write("user:1\tAlice2\t2000\tfalse\n");
            w.write("user:2\tDELETED\t2001\ttrue\n");
        }

        Compactor.compact(List.of(olderTable, newerTable), compactedTable, true);

        try (BufferedReader reader = new BufferedReader(new FileReader(compactedTable))) {
            String line1 = reader.readLine();
            Assertions.assertNotNull(line1);
            Assertions.assertTrue(line1.startsWith("user:1\tAlice2"));

            String line2 = reader.readLine();
            Assertions.assertNull(line2, "Tombstone should be eliminated at bottom level");
        }
    }
}
