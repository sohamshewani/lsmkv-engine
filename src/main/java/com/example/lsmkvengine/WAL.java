package com.example.lsmkvengine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class WAL {
    private final File walFile;

    public WAL(File walFile) {
        this.walFile = walFile;
    }

    public synchronized void append(String key, String value) throws IOException {
        try (FileWriter writer = new FileWriter(walFile, true)) {
            writer.write(key + ":" + value + "\n");
            writer.flush();
        }
    }

    public synchronized List<String> readAll() throws IOException {
        List<String> records = new ArrayList<>();
        if (!walFile.exists()) return records;
        try (BufferedReader reader = new BufferedReader(new FileReader(walFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                records.add(line);
            }
        }
        return records;
    }

    public synchronized void clear() throws IOException {
        if (walFile.exists()) {
            new FileWriter(walFile, false).close();
        }
    }
}
