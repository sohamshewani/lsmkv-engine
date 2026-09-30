// Engine test class
public class EngineTest {
    @Test
    public void testPutGet() {
        Engine engine = new Engine("/tmp/lsmkv_engine" {
        engine.put("key1", "value1");
        assertEquals("value1", engine.get("key1");
        engine.put("key2", "value2");
        assertEquals("value2", engine.get("key2");
    }
}
