package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.yjava.utils.IUpdateDecoder;
import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.StructStore;
import com.classpod.crdt.yjava.utils.Transaction;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ContentAny implements AbstractContent<ContentAny> {
    private List<Object> content;

    public ContentAny(List<Object> content) {
        this.content = content;
    }

    @Override
    public int length() {
        return this.content.size();
    }

    @Override
    public Boolean countable() {
        return true;
    }

    @Override
    public AbstractContent splice(int offset) {
        ContentAny right = new ContentAny(this.content.subList(offset, length()));
        this.content = this.content.subList(0, offset);
        return right;
    }

    @Override
    public List<Object> getContent() {
        return this.content;
    }

    @Override
    public void delete(Transaction transaction) {
    }

    @Override
    public ContentAny copy() {
        return new ContentAny(this.content);
    }

    @Override
    public Boolean mergeWith(ContentAny right) {
        this.content.addAll(right.getContent());
        return true;
    }

    @Override
    public void integrate (Transaction transaction, Item item) {

    }

    @Override
    public void gc(StructStore store) {

    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) {
        int len = this.content.size();
        encoder.writeLen(len - offset);
        for (int i = offset; i < len; i++) {
            Object c = this.content.get(i);
            encoder.writeAny(c);
        }
    }

    @Override
    public int getRef() {
        return 8;
    }


    public static ContentAny readContentAny(IUpdateDecoder decoder) throws IOException {
        long len = decoder.readLen();
        List<Object> cs = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            cs.add(decoder.readAny());
        }
        return new ContentAny(cs);
    }

}
