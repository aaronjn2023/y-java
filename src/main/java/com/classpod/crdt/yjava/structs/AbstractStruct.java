package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.Id;
import com.classpod.crdt.yjava.utils.StructStore;
import com.classpod.crdt.yjava.utils.Transaction;

import java.io.IOException;

public abstract class AbstractStruct {
    private Id id;

    private Integer length;

    public AbstractStruct(Id id, Integer length) {
        this.id = id;
        this.length = length;
    }

    public Id getId() {
        return id;
    }

    public void setId(Id id) {
        this.id = id;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public abstract boolean mergeWith(AbstractStruct right);
    public abstract boolean deleted();
    public abstract void integrate(Transaction transaction, int offset);
    public abstract void write(IUpdateEncoder encoder, int offset) throws IOException;

    public abstract Long getMissing(Transaction transaction, StructStore store);
}
