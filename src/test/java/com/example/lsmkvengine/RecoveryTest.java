// Recovery test class
public class RecoveryTest {
    @Test
    public void testRecover() {
        Engine engine = new Engine("/tmp/lsmkv_engine");
        engine.put("key1", "value1");
        engine.recover();
        assertEquals("value1", engine.get("key1");
    }
}
