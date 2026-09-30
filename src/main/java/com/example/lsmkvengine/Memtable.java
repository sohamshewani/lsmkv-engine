package com.example.lsmkvengine;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.Set;

public class Memtable {
    private final ConcurrentSkipListMap<String, String> map = new ConcurrentSkipListMap<>();

    public void put(String key, String value) {
        map.put(key, value);
    }

    public String get(String key) {
        return map.get(key);
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }

    public Set<String> keySet() {
        return map.keySet();
    }
}
