// WAL test class
public class WALTest {
    @Test
    public void testLog() {
        WAL wal = new WAL("/tmp/lsmkv_engine");
        wal.log("key1", "value1");
        // Verify log file contents
    }
}
