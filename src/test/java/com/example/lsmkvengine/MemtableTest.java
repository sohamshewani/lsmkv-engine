// Memtable test class
public class MemtableTest {
    @Test
    public void testPutGet() {
        Memtable memtable = new Memtable(100);
        memtable.put("key1", "value1");
        assertEquals("value1", memtable.get("key1");
    }
}
