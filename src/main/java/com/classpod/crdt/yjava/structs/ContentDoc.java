package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.yjava.utils.*;

import java.util.ArrayList;
import java.util.List;

public class ContentDoc implements AbstractContent<ContentDoc>{
    public YDoc doc;
    public YDocOptions opts;
    public ContentDoc(YDoc doc) {
        if(null != doc._item){
            throw new RuntimeException("This document was already integrated as a sub-document. You should create a second instance instead with the same guid.");
        }
        this.doc = doc;
        this.opts = new YDocOptions();
        if (!doc.gc) {
            opts.gc = false;
        }
        if (doc.autoLoad) {
            opts.autoLoad = true;
        }
        if (doc.meta != null) {
            opts.meta = doc.meta;
        }
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
    public AbstractContent copy() {
        return new ContentDoc(createDocFromOpts(this.doc.guid, this.opts));
    }

    @Override
    public AbstractContent splice(int offset) {
        return null;
    }

    @Override
    public List<Object> getContent() {
        List<Object> ret = new ArrayList<>();
        ret.add(this.doc);
        return ret;
    }

    @Override
    public Boolean mergeWith(ContentDoc right) {
        return false;
    }

    @Override
    public void integrate(Transaction transaction, Item item) throws NoSuchMethodException {
        this.doc._item = item;
        transaction.subdocsAdded.add(this.doc);
        if (this.doc.shouldLoad) {
            transaction.subdocsLoaded.add(this.doc);
        }
    }

    @Override
    public void delete(Transaction transaction) {
        if (transaction.subdocsAdded.contains(this.doc)) {
            transaction.subdocsAdded.remove(this.doc);
        } else {
            transaction.subdocsRemoved.add(this.doc);
        }
    }

    @Override
    public void gc(StructStore store) {

    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) {
        encoder.writeString(this.doc.guid);
        this.opts.write(encoder, offset);
    }

    @Override
    public int getRef() {
        return 9;
    }

    public static ContentDoc readContentDoc(IUpdateDecoder decoder) {
        return new ContentDoc(createDocFromOpts(decoder.readString(), YDocOptions.read(decoder)));
    }

    public static YDoc createDocFromOpts(String guid, YDocOptions opts) {
        YDocOptions newY = new YDocOptions();
        newY.guid = guid;
        newY.shouldLoad = opts.shouldLoad || opts.autoLoad;
        return new YDoc(newY);
    }

}
