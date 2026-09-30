// Engine class for LSMKV storage
public class Engine {
    private static final int DEFAULT_MEMTABLE_SIZE = 10000;
    private static final int DEFAULT_SSTABLE_SIZE = 10000;
    private Memtable memtable;
    private List<SSTable> sstables;
    private WAL wal;
    private BloomFilter bloomFilter;
    private String dataDir;

    public Engine(String dataDir) {
        this.dataDir = dataDir;
        this.memtable = new Memtable(DEFAULT_MEMTABLE_SIZE);
        this.sstables = new ArrayList<>();
        this.wal = new WAL(dataDir);
        this.bloomFilter = new BloomFilter(dataDir);
    }

    public void put(String key, String value) {
        memtable.put(key, value);
        wal.log(key, value);
        if (memtable.size() >= DEFAULT_MEMTABLE_SIZE) {
            flushMemtableToSSTable();
        }
    }

    public String get(String key) {
        if (bloomFilter.mightContain(key)) {
            for (SSTable sstable : sstables) {
                if (sstable.contains(key)) {
                    return sstable.get(key);
                }
            }
        }
        return null;
    }

    public void flushMemtableToSSTable() {
        SSTable sstable = new SSTable(dataDir, memtable);
        sstables.add(sstable);
        memtable = new Memtable(DEFAULT_MEMTABLE_SIZE);
        wal.flushToDisk(sstable);
    }

    public void recover() {
        Recovery.recover(this);
    }
}
