package com.classpod.crdt.yjava.types;

import com.classpod.crdt.yjava.utils.Transaction;
import com.classpod.crdt.yjava.utils.YEvent;

public class YArrayEvent extends YEvent {
    private Transaction _transaction;
    public YArrayEvent(AbstractType target, Transaction transaction) {
        super(target, transaction);
        this._transaction = transaction;
    }
}
