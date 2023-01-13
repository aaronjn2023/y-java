package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.yjava.structs.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class YDocOptions {
    public static Predicate<Item> defaultPredicate = (item) -> true;

    public boolean gc = true;

    public Predicate<Item> gcFilter = defaultPredicate;

    public String guid = UUID.randomUUID().toString();

    public Map<String, String> meta;

    public boolean autoLoad = false;

    public boolean shouldLoad = true;

    public String collectionId;

    public YDocOptions() {};

    public YDocOptions(boolean gc, Predicate<Item> gcFilter, String guid, Map<String, String> meta, boolean autoLoad, boolean shouldLoad, String collectionId) {
        this.gc = gc;
        this.gcFilter = gcFilter;
        this.guid = guid;
        this.meta = meta;
        this.autoLoad = autoLoad;
        this.shouldLoad = shouldLoad;
        this.collectionId = collectionId;
    }

    public YDocOptions clone() {
        return new YDocOptions(this.gc,this.gcFilter, this.guid, this.meta, this.autoLoad, this.shouldLoad, this.collectionId);
    }

    public void write(IUpdateEncoder encoder, int offset) {
        Map<String, Object> dict = new HashMap<>();
        dict.put("gc", this.gc);
        dict.put("guid", this.guid);
        dict.put("autoLoad", this.autoLoad);
        dict.put("shouldLoad", this.shouldLoad);
        if (this.collectionId != null) {
            dict.put("collectionId", this.collectionId);
        }
        if (this.meta != null) {
            dict.put("meta", this.meta);
        }
        encoder.writeAny(dict);
    }

    public static YDocOptions read(IUpdateDecoder decoder) {
        Map<String, Object> dict = (Map<String, Object>)decoder.readAny();
        YDocOptions result = new YDocOptions();
        result.gc = !dict.containsKey("gc") || (boolean) dict.get("gc");
        result.guid = dict.containsKey("guid") ? (String)dict.get("guid") : UUID.randomUUID().toString();
        result.meta = dict.containsKey("meta") ? (Map<String, String>)dict.get("meta") : null;
        result.autoLoad = dict.containsKey("autoLoad") && (boolean) dict.get("autoLoad");
        result.autoLoad = !dict.containsKey("autoLoad") || (boolean) dict.get("autoLoad");
        result.collectionId = (String)dict.getOrDefault("collectionId", null);
        return result;
    }
}
