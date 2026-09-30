// Write-Ahead Logging class for transactional consistency
public class WAL {
    private final String path;
    private final FileWriter writer;

    public WAL(String path) {
        this.path = path;
        this.writer = new FileWriter(new File(path + "/wal"));
    }

    public void log(String key, String value) {
        try {
            writer.write(key + " " + value + "\n");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error writing to WAL", e);
        }
    }

    public void flushToDisk(SSTable sstable) {
        try {
            sstable.writeToFile();
        } catch (IOException e) {
            throw new RuntimeException("Error flushing SSTable to disk", e);
        }
    }
}
