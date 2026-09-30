package com.lsmkv;

import com.lsmkv.wal.WalRecoveryEngine;
import com.lsmkv.wal.WriteAheadLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public class CrashRecoveryIntegrationTest {

    public static class CrashWorker {
        public static void main(String[] args) throws Exception {
            File walFile = new File(args[0]);
            try (WriteAheadLog wal = new WriteAheadLog(walFile)) {
                int seq = 0;
                while (true) {
                    byte[] key = ("key-" + seq).getBytes(StandardCharsets.UTF_8);
                    byte[] val = ("value-" + seq).getBytes(StandardCharsets.UTF_8);
                    wal.append(key, val, false);
                    seq++;
                }
            }
        }
    }

    @TempDir
    Path tempDir;

    @Test
    public void testHardCrashRecovery() throws Exception {
        File walFile = tempDir.resolve("test.wal").toFile();

        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-cp", classpath,
                CrashWorker.class.getName(),
                walFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process worker = pb.start();

        // Allow rapid writes to occur
        Thread.sleep(150);

        // Abruptly kill the worker (SIGKILL)
        worker.destroyForcibly();
        worker.waitFor();

        // Perform WAL recovery
        List<WalRecoveryEngine.WalRecord> recovered = WalRecoveryEngine.recover(walFile);

        Assertions.assertFalse(recovered.isEmpty(), "Engine should recover acknowledged records");
        for (WalRecoveryEngine.WalRecord r : recovered) {
            Assertions.assertNotNull(r.key);
            Assertions.assertTrue(r.key.length > 0);
        }
    }
}
