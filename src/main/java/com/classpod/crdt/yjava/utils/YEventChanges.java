package com.classpod.crdt.yjava.utils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/12/26 3:47 PM
 **/
public class YEventChanges {
    private Set<String> added;
    private Set<String> deleted;
    private List<Delta> delta;
    private Map<String,ChangeKey> keys;

    public YEventChanges(Map<String,ChangeKey> keys){
        this.keys = keys;
    }

    public Set<String> getAdded() {
        return added;
    }

    public void setAdded(Set<String> added) {
        this.added = added;
    }

    public Set<String> getDeleted() {
        return deleted;
    }

    public void setDeleted(Set<String> deleted) {
        this.deleted = deleted;
    }

    public List<Delta> getDelta() {
        return delta;
    }

    public void setDelta(List<Delta> delta) {
        this.delta = delta;
    }

    public Map<String, ChangeKey> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, ChangeKey> keys) {
        this.keys = keys;
    }
}
