// BloomFilter test class
public class BloomFilterTest {
    @Test
    public void testPutMightContain() {
        BloomFilter bloomFilter = new BloomFilter("/tmp/lsmkv_engine");
        bloomFilter.put("key1");
        assertTrue(bloomFilter.mightContain("key1");
    }
}
