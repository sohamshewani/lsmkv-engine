package com.example.lsmkvengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class LargeDatasetFuzzTest {

    @TempDir
    Path tempDir;

    @Test
    public void testRandomizedFuzzEngine() throws IOException {
        File dbDir = tempDir.resolve("fuzz_db").toFile();
        Engine engine = new Engine(dbDir);

        Map<String, String> shadowMap = new HashMap<>();
        Random random = new Random(42);
        int totalOps = 5000;
        int keySpace = 500;

        for (int op = 0; op < totalOps; op++) {
            String key = "key:" + random.nextInt(keySpace);
            int action = random.nextInt(10);

            if (action < 6) {
                String val = "v_" + op + "_" + random.nextInt(99999);
                engine.put(key, val);
                shadowMap.put(key, val);
            } else if (action < 8) {
                engine.delete(key);
                shadowMap.remove(key);
            } else {
                String expected = shadowMap.get(key);
                String actual = engine.get(key);
                Assertions.assertEquals(expected, actual);
            }
        }

        for (int i = 0; i < keySpace; i++) {
            String key = "key:" + i;
            String expected = shadowMap.get(key);
            String actual = engine.get(key);
            Assertions.assertEquals(expected, actual);
        }

        engine.close();
    }
}
