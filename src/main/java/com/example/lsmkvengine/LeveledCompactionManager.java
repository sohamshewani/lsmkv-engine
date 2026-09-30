package com.example.lsmkvengine;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class LeveledCompactionManager {
    private final File dbDir;
    private final int l0Threshold;
    private final ExecutorService compactionExecutor;
    private final AtomicBoolean isCompacting = new AtomicBoolean(false);
    private final List<List<File>> levels = new CopyOnWriteArrayList<>();

    public LeveledCompactionManager(File dbDir, int l0Threshold) {
        this.dbDir = dbDir;
        this.l0Threshold = l0Threshold;
        this.compactionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LSM-Compactor-Thread");
            t.setDaemon(true);
            return t;
        });

        levels.add(new CopyOnWriteArrayList<>()); // L0
        levels.add(new CopyOnWriteArrayList<>()); // L1
        levels.add(new CopyOnWriteArrayList<>()); // L2
    }

    public synchronized void registerL0Table(File sstable) {
        levels.get(0).add(sstable);
        triggerBackgroundCompaction();
    }

    public void triggerBackgroundCompaction() {
        if (levels.get(0).size() >= l0Threshold && isCompacting.compareAndSet(false, true)) {
            compactionExecutor.submit(this::runCompactionTask);
        }
    }

    private void runCompactionTask() {
        try {
            List<File> l0Tables = new ArrayList<>(levels.get(0));
            if (l0Tables.size() < l0Threshold) return;

            File l1Target = new File(dbDir, "sstable-L1-" + System.currentTimeMillis() + ".db");
            List<File> mergeInputs = new ArrayList<>(l0Tables);
            mergeInputs.addAll(levels.get(1));

            Compactor.compact(mergeInputs, l1Target, false);

            levels.get(0).removeAll(l0Tables);
            levels.get(1).clear();
            levels.get(1).add(l1Target);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isCompacting.set(false);
        }
    }

    public void shutdown() {
        compactionExecutor.shutdown();
    }
}
