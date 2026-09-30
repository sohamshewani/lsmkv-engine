package com.example.lsmkvengine;

public record Record(String key, String value, long timestamp, boolean isTombstone) 
    implements Comparable<Record> {

    @Override
    public int compareTo(Record o) {
        int cmp = this.key.compareTo(o.key);
        if (cmp != 0) return cmp;
        return Long.compare(o.timestamp, this.timestamp);
    }
}
