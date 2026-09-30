// SSTable class for on-disk storage
public class SSTable {
    private final Map<String, String> data;
    private final String path;

    public SSTable(String path, Memtable memtable) {
        this.path = path;
        this.data = memtable.map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void writeToFile() {
        // Code to write data to file
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }

    public String get(String key) {
        return data.get(key);
    }
}
