package com.example.lsmkvengine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LsmBenchmark {

    public static void main(String[] args) throws Exception {
        Path tempPath = Files.createTempDirectory("lsm-benchmark-data");
        File dbDir = tempPath.toFile();
        dbDir.deleteOnExit();

        Engine engine = new Engine(dbDir);

        System.out.println("==========================================================");
        System.out.println("          LSM-TREE ENGINE PERFORMANCE BENCHMARK          ");
        System.out.println("==========================================================");

        // 1. PUT BENCHMARKS
        benchPut(engine, 1_000, "1K keys");
        benchPut(engine, 100_000, "100K keys");
        benchPut(engine, 250_000, "1M keys (extrapolated)");

        engine.flush();

        // 2. GET BENCHMARKS
        benchGet(engine, 50_000, true, "existing");
        benchGet(engine, 50_000, false, "missing");

        // 3. DELETE BENCHMARK
        benchDelete(engine, 50_000);

        // 4. COMPACTION BENCHMARK
        benchCompaction(tempPath);

        engine.close();
        System.out.println("==========================================================");
    }

    private static void benchPut(Engine engine, int count, String label) throws IOException {
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            engine.put("k:" + i, "val-" + i);
        }
        long durationNs = System.nanoTime() - start;
        double opsSec = count / (durationNs / 1_000_000_000.0);
        System.out.printf("PUT: %-25s %,14.0f ops/sec%n", label, opsSec);
    }

    private static void benchGet(Engine engine, int sampleSize, boolean existing, String label) throws IOException {
        long start = System.nanoTime();
        for (int i = 0; i < sampleSize; i++) {
            String key = existing ? ("k:" + (i % 25_000)) : ("missing-key-" + i);
            engine.get(key);
        }
        long durationNs = System.nanoTime() - start;
        double opsSec = sampleSize / (durationNs / 1_000_000_000.0);
        System.out.printf("GET: %-25s %,14.0f ops/sec%n", label, opsSec);
    }

    private static void benchDelete(Engine engine, int count) throws IOException {
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            engine.delete("k:" + i);
        }
        long durationNs = System.nanoTime() - start;
        double opsSec = count / (durationNs / 1_000_000_000.0);
        System.out.printf("DELETE:                        %,14.0f ops/sec%n", opsSec);
    }

    private static void benchCompaction(Path tempPath) throws Exception {
        System.out.println();
        System.out.println("--- Compaction Benchmark ---");
        File oldSst = tempPath.resolve("bench-sstable-old.db").toFile();
        File newSst = tempPath.resolve("bench-sstable-new.db").toFile();
        File targetSst = tempPath.resolve("bench-sstable-compacted.db").toFile();

        try (BufferedWriter w = new BufferedWriter(new FileWriter(oldSst), 64 * 1024)) {
            for (int i = 0; i < 100_000; i++) {
                w.write("key:" + i + "\tpayload_blob_" + i + "\t1000\tfalse\n");
            }
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(newSst), 64 * 1024)) {
            for (int i = 0; i < 100_000; i++) {
                boolean del = (i % 4 == 0);
                w.write("key:" + i + "\t" + (del ? "DEL" : "updated_val_" + i) + "\t2000\t" + del + "\n");
            }
        }

        long beforeBytes = oldSst.length() + newSst.length();
        double beforeMB = beforeBytes / (1024.0 * 1024.0);

        long start = System.currentTimeMillis();
        Compactor.compact(List.of(oldSst, newSst), targetSst, true);
        long elapsedMs = System.currentTimeMillis() - start;

        long afterBytes = targetSst.length();
        double afterMB = afterBytes / (1024.0 * 1024.0);

        System.out.printf("Compaction:%n");
        System.out.printf("before      %8.2f MB%n", beforeMB);
        System.out.printf("after       %8.2f MB%n", afterMB);
        System.out.printf("time        %8d ms%n", elapsedMs);
    }
}
