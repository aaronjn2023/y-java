package com.classpod.crdt.yjava.types;

import com.classpod.crdt.yjava.utils.Transaction;
import com.classpod.crdt.yjava.utils.YEvent;

import java.util.Set;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/14 13:31 PM
 **/
public class YMapEvent extends YEvent {

    public Set<String> keysChanged;

    public YMapEvent(YMap map, Transaction transaction, Set<String> subs) {
        super(map, transaction);
        keysChanged = subs;
    }
}
