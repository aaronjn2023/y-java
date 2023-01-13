package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.yjava.utils.IUpdateDecoder;
import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.StructStore;
import com.classpod.crdt.yjava.utils.Transaction;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ContentBinary implements AbstractContent<ContentBinary> {
    public byte[] content;
    public ContentBinary(byte[] content) {
        this.content = content;
    }

    @Override
    public int length() {
        return 1;
    }

    @Override
    public Boolean countable() {
        return true;
    }

    @Override
    public AbstractContent splice(int offset) {
        return null;
    }

    @Override
    public List<Object> getContent() {
        List<Object> ret = new ArrayList<>();
        ret.add(this.content);
        return ret;
    }

    @Override
    public void delete(Transaction transaction) {

    }

    @Override
    public ContentBinary copy() {
        return new ContentBinary(this.content);
    }

    @Override
    public Boolean mergeWith(ContentBinary right) {
        return false;
    }

    @Override
    public void integrate (Transaction transaction, Item item) {

    }

    @Override
    public void gc(StructStore store) {

    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) {
        encoder.writeBuf(this.content);
    }

    @Override
    public int getRef() {
        return 3;
    }

    public static ContentBinary readContentAny(IUpdateDecoder decoder) throws IOException {
        return new ContentBinary(decoder.readBuf());
    }

}
