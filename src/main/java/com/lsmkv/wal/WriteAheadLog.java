package com.lsmkv.wal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;

public class WriteAheadLog implements AutoCloseable {
    private final FileOutputStream fos;
    private final FileChannel channel;
    private final ReentrantLock appendLock = new ReentrantLock();

    public static final byte TYPE_PUT = 0;
    public static final byte TYPE_DELETE = 1;

    public WriteAheadLog(File walFile) throws IOException {
        this.fos = new FileOutputStream(walFile, true);
        this.channel = fos.getChannel();
    }

    public void append(byte[] key, byte[] value, boolean isDelete) throws IOException {
        byte type = isDelete ? TYPE_DELETE : TYPE_PUT;
        int valLen = (value == null) ? 0 : value.length;
        int payloadSize = 4 + 4 + 1 + key.length + valLen;

        ByteBuffer payloadBuffer = ByteBuffer.allocate(payloadSize);
        payloadBuffer.putInt(key.length);
        payloadBuffer.putInt(valLen);
        payloadBuffer.put(type);
        payloadBuffer.put(key);
        if (value != null) {
            payloadBuffer.put(value);
        }
        byte[] payload = payloadBuffer.array();

        // Checksum calculation
        CRC32 crc = new CRC32();
        crc.update(payload);
        int crcValue = (int) crc.getValue();

        ByteBuffer recordBuffer = ByteBuffer.allocate(4 + payloadSize);
        recordBuffer.putInt(crcValue);
        recordBuffer.put(payload);
        recordBuffer.flip();

        appendLock.lock();
        try {
            while (recordBuffer.hasRemaining()) {
                channel.write(recordBuffer);
            }
            // CRITICAL: fsync buffers to physical drive before returning ACK
            channel.force(false);
        } finally {
            appendLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        appendLock.lock();
        try {
            channel.force(true);
            channel.close();
            fos.close();
        } finally {
            appendLock.unlock();
        }
    }
}
