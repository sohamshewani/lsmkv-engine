package com.example.lsmkvengine;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Recovery {
    private final Engine engine;
    private final WAL wal;

    public Recovery(Engine engine, WAL wal) {
        this.engine = engine;
        this.wal = wal;
    }

    public void recover() throws IOException {
        List<String> entries = wal.readAll();
        for (String entry : entries) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                engine.put(parts[0], parts[1]);
            }
        }
    }
}
