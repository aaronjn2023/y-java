package com.classpod.crdt.yjava.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/21 17:20 PM
 **/
public class StackItem {

    public Map<Long, Long> beforeState;
    public Map<Long, Long> afterState;
    // Use this to save and restore metadata like selection range.
    public Map<String, Object> meta;
    public DeleteSet deleteSet;

    public StackItem(DeleteSet ds,Map<Long, Long> beforeState, Map<Long, Long> afterState) {
        this.deleteSet = ds;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.meta = new HashMap<>();
    }
}
