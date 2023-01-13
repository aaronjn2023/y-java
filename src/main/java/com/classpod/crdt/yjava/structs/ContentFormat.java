package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.yjava.types.AbstractType;
import com.classpod.crdt.yjava.utils.IUpdateDecoder;
import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.StructStore;
import com.classpod.crdt.yjava.utils.Transaction;

import java.util.List;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/19 15:31 PM
 **/
public class ContentFormat implements AbstractContent<ContentFormat> {

    private String key;

    private Object value;

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public ContentFormat(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public int length() {
        return 1;
    }

    @Override
    public Boolean countable() {
        return false;
    }

    @Override
    public AbstractContent copy() {
        return new ContentFormat(this.key, this.value);
    }

    @Override
    public AbstractContent splice(int offset) {
        return null;
    }

    @Override
    public List<Object> getContent() {
        return null;
    }

    @Override
    public Boolean mergeWith(ContentFormat right) {
        return false;
    }

    @Override
    public void integrate(Transaction transaction, Item item) throws NoSuchMethodException {
        //TODO
        // Search markers are currently unsupported for rich text documents.
        // (item.Parent as YArrayBase)?.ClearSearchMarkers();
        ((AbstractType)item.parent)._searchMarker = null;
    }

    @Override
    public void delete(Transaction transaction) {

    }

    @Override
    public void gc(StructStore store) {

    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) {
        encoder.writeKey(this.key);
        encoder.writeJson((String)this.value);
    }

    public static ContentFormat read(IUpdateDecoder decoder) {
        return new ContentFormat(decoder.readKey(), decoder.readJson());
    }

    @Override
    public int getRef() {
        return 6;
    }
}
