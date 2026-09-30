package com.example.lsmkvengine;

import java.io.*;
import java.util.*;

public class SSTable {
    private final File file;
    private final Map<String, String> cache = new HashMap<>();

    public SSTable(File file) {
        this.file = file;
        loadIndex();
    }

    private void loadIndex() {
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int firstTab = line.indexOf("\t");
                if (firstTab != -1) {
                    int secondTab = line.indexOf("\t", firstTab + 1);
                    String key = line.substring(0, firstTab);
                    String val = (secondTab != -1) ? line.substring(firstTab + 1, secondTab) : line.substring(firstTab + 1);
                    cache.put(key, val);
                } else {
                    int colon = line.indexOf(":");
                    if (colon != -1) {
                        cache.put(line.substring(0, colon), line.substring(colon + 1));
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    public static SSTable flush(Memtable memtable, File targetFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile), 64 * 1024)) {
            for (String key : memtable.keySet()) {
                String val = memtable.get(key);
                writer.write(key + "\t" + val + "\t" + System.currentTimeMillis() + "\tfalse\n");
            }
        }
        return new SSTable(targetFile);
    }

    public String get(String key) {
        return cache.get(key);
    }
}
