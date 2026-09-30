package com.example.lsmkvengine;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class WAL implements AutoCloseable {
    private final File walFile;
    private BufferedWriter writer;

    public WAL(File walFile) throws IOException {
        this.walFile = walFile;
        this.writer = new BufferedWriter(new FileWriter(walFile, true), 64 * 1024);
    }

    public synchronized void append(String key, String value) throws IOException {
        writer.write(key);
        writer.write(":");
        writer.write(value);
        writer.newLine();
    }

    public synchronized void flush() throws IOException {
        writer.flush();
    }

    public synchronized List<String> readAll() throws IOException {
        flush();
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
        writer.close();
        if (walFile.exists()) {
            walFile.delete();
        }
        this.writer = new BufferedWriter(new FileWriter(walFile, true), 64 * 1024);
    }

    @Override
    public synchronized void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }
}
