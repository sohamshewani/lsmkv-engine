package com.example.lsmkvengine;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class Compactor {

    private record IteratorEntry(Record record, BufferedReader reader, int sourcePriority) {}

    public static void compact(List<File> inputTables, File outputFile, boolean isBottomLevel) throws IOException {
        PriorityQueue<IteratorEntry> pq = new PriorityQueue<>((a, b) -> {
            int cmp = a.record.key().compareTo(b.record.key());
            if (cmp != 0) return cmp;
            return Integer.compare(b.sourcePriority, a.sourcePriority);
        });

        List<BufferedReader> openedReaders = new ArrayList<>();
        try {
            for (int i = 0; i < inputTables.size(); i++) {
                File file = inputTables.get(i);
                if (!file.exists()) continue;
                BufferedReader reader = new BufferedReader(new FileReader(file));
                openedReaders.add(reader);
                Record rec = readNext(reader);
                if (rec != null) {
                    pq.add(new IteratorEntry(rec, reader, i));
                }
            }

            File tempOutput = new File(outputFile.getAbsolutePath() + ".compact.tmp");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempOutput))) {
                String currentKey = null;

                while (!pq.isEmpty()) {
                    IteratorEntry entry = pq.poll();
                    Record rec = entry.record();

                    if (currentKey == null || !currentKey.equals(rec.key())) {
                        currentKey = rec.key();
                        if (!(isBottomLevel && rec.isTombstone())) {
                            writer.write(encode(rec));
                            writer.newLine();
                        }
                    }

                    Record nextRec = readNext(entry.reader());
                    if (nextRec != null) {
                        pq.add(new IteratorEntry(nextRec, entry.reader(), entry.sourcePriority()));
                    }
                }
                writer.flush();
            }

            Files.move(tempOutput.toPath(), outputFile.toPath(), 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, 
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            for (File oldFile : inputTables) {
                if (!oldFile.equals(outputFile)) {
                    Files.deleteIfExists(oldFile.toPath());
                }
            }
        } finally {
            for (BufferedReader r : openedReaders) {
                try { r.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static Record readNext(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) return null;
        String[] parts = line.split("\t", 4);
        if (parts.length < 4) return null;
        return new Record(parts[0], parts[1], Long.parseLong(parts[2]), Boolean.parseBoolean(parts[3]));
    }

    private static String encode(Record r) {
        return r.key() + "\t" + r.value() + "\t" + r.timestamp() + "\t" + r.isTombstone();
    }
}
