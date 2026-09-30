// Memtable class for in-memory storage
public class Memtable {
    private final Map<String, String> map;
    private final int capacity;

    public Memtable(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>(capacity);
    }

    public void put(String key, String value) {
        if (map.size() < capacity) {
            map.put(key, value);
        } else {
            throw new IllegalStateException("Memtable is full");
        }
    }

    public boolean contains(String key) {
        return map.containsKey(key);
    }

    public String get(String key) {
        return map.get(key);
    }

    public int size() {
        return map.size();
    }
}
