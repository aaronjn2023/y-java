package com.classpod.crdt.yjava.types;

import com.classpod.crdt.yjava.structs.Item;

public class ArraySearchMarker {
    public static Long globalSearchMarkerTimestamp = 0L;
    public Item p;
    public Integer index;
    public Long timestamp;
    public ArraySearchMarker (Item p, Integer index) {
        p.setMarker(true);
        this.p = p;
        this.index = index;
        this.timestamp = globalSearchMarkerTimestamp++;
    }

    public Integer getIndex() {
        return index;
    }

    public Long getTimestamp() {
        return timestamp;
    }
}
