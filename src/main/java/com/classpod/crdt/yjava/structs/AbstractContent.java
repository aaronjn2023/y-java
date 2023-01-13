package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.StructStore;
import com.classpod.crdt.yjava.utils.Transaction;

import java.util.List;

public interface AbstractContent<T> {
    int length();
    Boolean countable();
    AbstractContent copy();
    AbstractContent splice(int offset);
    List<Object> getContent();
    void integrate(Transaction transaction, Item item) throws Exception;
    void delete (Transaction transaction);
    void gc(StructStore store);
    void write(IUpdateEncoder encoder, int offset);
    int getRef();
    Boolean mergeWith(T right);
}
