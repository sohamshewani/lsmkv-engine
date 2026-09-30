package com.example.lsmkvengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class BasicOperationsTest {

    @TempDir
    Path tempDir;

    @Test
    public void testPutGetUpdateDeleteFlow() throws IOException {
        File dbDir = tempDir.resolve("basic_db").toFile();
        Engine engine = new Engine(dbDir);

        engine.put("user:100", "Alice");
        Assertions.assertEquals("Alice", engine.get("user:100"));

        engine.put("user:100", "Alice Cooper");
        Assertions.assertEquals("Alice Cooper", engine.get("user:100"));

        engine.delete("user:100");
        Assertions.assertNull(engine.get("user:100"), "Deleted key must resolve to null");

        engine.put("user:100", "Alice 3.0");
        Assertions.assertEquals("Alice 3.0", engine.get("user:100"));
        engine.close();
    }

    @Test
    public void testRestartAndRecovery() throws IOException {
        File dbDir = tempDir.resolve("restart_db").toFile();

        Engine engine1 = new Engine(dbDir);
        for (int i = 0; i < 1500; i++) {
            engine1.put("k-" + i, "v-" + i);
        }
        engine1.put("wal-only-key", "wal-persisted-val");
        engine1.close();

        Engine engine2 = new Engine(dbDir);
        Recovery recovery = new Recovery(engine2, new WAL(new File(dbDir, "commit.wal")));
        recovery.recover();

        Assertions.assertEquals("v-0", engine2.get("k-0"));
        Assertions.assertEquals("v-1499", engine2.get("k-1499"));
        Assertions.assertEquals("wal-persisted-val", engine2.get("wal-only-key"));
        engine2.close();
    }
}
