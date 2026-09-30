package com.lsmkv.wal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public class WalRecoveryEngine {

    public static class WalRecord {
        public final byte[] key;
        public final byte[] value;
        public final boolean isDelete;

        public WalRecord(byte[] key, byte[] value, boolean isDelete) {
            this.key = key;
            this.value = value;
            this.isDelete = isDelete;
        }
    }

    public static List<WalRecord> recover(File walFile) throws IOException {
        List<WalRecord> validRecords = new ArrayList<>();
        if (!walFile.exists()) {
            return validRecords;
        }

        try (FileInputStream fis = new FileInputStream(walFile);
             FileChannel channel = fis.getChannel()) {

            ByteBuffer headerBuf = ByteBuffer.allocate(13); // CRC(4) + KeyLen(4) + ValLen(4) + Type(1)

            while (true) {
                headerBuf.clear();
                long recordStartOffset = channel.position();
                int bytesRead = channel.read(headerBuf);

                if (bytesRead == -1 || bytesRead == 0) {
                    break;
                }

                if (bytesRead < 13) {
                    truncateWal(walFile, recordStartOffset);
                    break;
                }

                headerBuf.flip();
                int expectedCrc = headerBuf.getInt();
                int keyLen = headerBuf.getInt();
                int valLen = headerBuf.getInt();
                byte type = headerBuf.get();

                if (keyLen < 0 || valLen < 0) {
                    truncateWal(walFile, recordStartOffset);
                    break;
                }

                int payloadLen = keyLen + valLen;
                ByteBuffer payloadBuf = ByteBuffer.allocate(payloadLen);
                int payloadRead = channel.read(payloadBuf);

                if (payloadRead < payloadLen) {
                    truncateWal(walFile, recordStartOffset);
                    break;
                }

                payloadBuf.flip();
                byte[] key = new byte[keyLen];
                byte[] val = new byte[valLen];
                payloadBuf.get(key);
                payloadBuf.get(val);

                CRC32 crc = new CRC32();
                ByteBuffer checkBuf = ByteBuffer.allocate(9 + payloadLen);
                checkBuf.putInt(keyLen);
                checkBuf.putInt(valLen);
                checkBuf.put(type);
                checkBuf.put(key);
                checkBuf.put(val);
                crc.update(checkBuf.array());

                if ((int) crc.getValue() != expectedCrc) {
                    truncateWal(walFile, recordStartOffset);
                    break;
                }

                validRecords.add(new WalRecord(key, val, type == 1));
            }
        }
        return validRecords;
    }

    private static void truncateWal(File walFile, long validLength) throws IOException {
        try (FileChannel outChan = FileChannel.open(walFile.toPath(), StandardOpenOption.WRITE)) {
            outChan.truncate(validLength);
            outChan.force(true);
        }
    }
}
