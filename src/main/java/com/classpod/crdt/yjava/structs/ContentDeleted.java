package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.yjava.utils.*;


import java.util.ArrayList;
import java.util.List;

public class ContentDeleted implements AbstractContent<ContentDeleted>{
    public long len;

    public ContentDeleted(long len) {
        this.len = len;
    }

    @Override
    public int length() {
        return (int)this.len;
    }

    @Override
    public Boolean countable() {
        return false;
    }

    @Override
    public AbstractContent copy() {
        return new ContentDeleted(this.len);
    }

    @Override
    public Boolean mergeWith(ContentDeleted right) {
        this.len += right.len;
        return true;
    }

    @Override
    public AbstractContent splice(int offset) {
        ContentDeleted right = new ContentDeleted(this.len - offset);
        this.len = offset;
        return right;
    }

    @Override
    public List<Object> getContent() {
        return new ArrayList<>();
    }

    @Override
    public void integrate(Transaction transaction, Item item) throws NoSuchMethodException {
        DeleteSet.addToDeleteSet(transaction.deleteSet, item.getId().getClient(), item.getId().getClock(), this.len);
        item.markDeleted();
    }

    @Override
    public void delete(Transaction transaction) {

    }

    @Override
    public void gc(StructStore store) {

    }

    @Override
    public void write(IUpdateEncoder encoder , int offset) {
        encoder.writeLen(this.len - offset);
    }

    @Override
    public int getRef() {
        return 1;
    }

    public static ContentDeleted readContentDeleted(IUpdateDecoder decoder) {
        return new ContentDeleted(decoder.readLen());
    }
}
