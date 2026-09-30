package com.lsmkv.manifest;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class ManifestManager {
    private final Path manifestPath;

    public ManifestManager(Path manifestPath) {
        this.manifestPath = manifestPath;
    }

    public synchronized void commitManifest(byte[] serializedState) throws IOException {
        Path tempPath = manifestPath.resolveSibling(manifestPath.getFileName() + ".tmp");

        // 1. Write to temp file and flush
        try (FileOutputStream fos = new FileOutputStream(tempPath.toFile())) {
            fos.write(serializedState);
            fos.flush();
            fos.getFD().sync();
        }

        // 2. Atomic filesystem swap
        try {
            Files.move(tempPath, manifestPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, manifestPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 3. Fsync parent directory to persist inode metadata
        try (FileChannel dirChan = FileChannel.open(manifestPath.getParent(), StandardOpenOption.READ)) {
            dirChan.force(true);
        }
    }
}
