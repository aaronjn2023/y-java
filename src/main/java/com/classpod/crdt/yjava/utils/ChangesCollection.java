package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.yjava.structs.Item;

import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/21 15:36 PM
 **/
public class ChangesCollection {

    public Set<Item> added;
    public Set<Item> deleted;
    public List<Delta> delta;
    public Map<String, ChangeKey> Keys;

    public ChangesCollection(Set<Item> added, Set<Item> deleted, List<Delta> delta, Map<String, ChangeKey> keys) {
        this.added = added;
        this.deleted = deleted;
        this.delta = delta;
        Keys = keys;
    }
}
