package com.example.lsmkvengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyStressTest {

    @TempDir
    Path tempDir;

    @Test
    public void testHighConcurrencyReadWrite() throws Exception {
        File dbDir = tempDir.resolve("concurrent_db").toFile();
        Engine engine = new Engine(dbDir);

        int threadCount = 8;
        int operationsPerThread = 250;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "thread-" + threadId + "-key-" + i;
                        String val = "payload-" + i;
                        engine.put(key, val);

                        String readBack = engine.get(key);
                        if (!val.equals(readBack)) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        Assertions.assertTrue(finished);
        Assertions.assertEquals(0, errorCount.get());
        engine.close();
    }
}
