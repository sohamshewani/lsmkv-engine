package com.example.lsmkvengine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Map;
import java.util.TreeMap;

public class SSTable {
    private final File file;

    public SSTable(File file) {
        this.file = file;
    }

    public static SSTable flush(Memtable memtable, File targetFile) throws IOException {
        try (FileWriter writer = new FileWriter(targetFile)) {
            for (Map.Entry<String, String> entry : memtable.getMap().entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue() + "\n");
            }
            writer.flush();
        }
        return new SSTable(targetFile);
    }

    public String get(String key) throws IOException {
        if (!file.exists()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2 && parts[0].equals(key)) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    public File getFile() {
        return file;
    }
}
