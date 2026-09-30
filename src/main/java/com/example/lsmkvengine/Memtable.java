package com.example.lsmkvengine;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

public class Memtable {
    private final ConcurrentSkipListMap<String, String> map = new ConcurrentSkipListMap<>();

    public void put(String key, String value) {
        map.put(key, value);
    }

    public String get(String key) {
        return map.get(key);
    }

    public Map<String, String> getMap() {
        return Collections.unmodifiableMap(map);
    }

    public void clear() {
        map.clear();
    }

    public int size() {
        return map.size();
    }
}
