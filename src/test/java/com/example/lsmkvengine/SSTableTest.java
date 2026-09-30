// SSTable test class
public class SSTableTest {
    @Test
    public void testContainsGet() {
        SSTable sstable = new SSTable("/tmp/lsmkv_engine", new Memtable(100));
        sstable.put("key1", "value1");
        sstable.writeToFile();
        assertEquals("value1", sstable.get("key1");
    }
}
