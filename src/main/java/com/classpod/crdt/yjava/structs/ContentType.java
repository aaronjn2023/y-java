package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.yjava.types.AbstractType;
import com.classpod.crdt.yjava.types.YArray;
import com.classpod.crdt.yjava.types.YMap;
import com.classpod.crdt.yjava.types.YText;
import com.classpod.crdt.yjava.utils.IUpdateDecoder;
import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.StructStore;
import com.classpod.crdt.yjava.utils.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ContentType implements AbstractContent<ContentType> {
    public AbstractType type;

    public ContentType(AbstractType type) {
        this.type = type;
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
        ret.add(this.type);
        return ret;
    }

    @Override
    public void delete(Transaction transaction) {
        Item item = this.type._start;
        while (item != null) {
            if (!item.deleted()) {
                item.delete(transaction);
            } else {
                transaction._mergeStructs.add(item);
            }
            item = (Item) item.right;
        }
        this.type._map.forEach((key, value) -> {
            if (!value.deleted()) {
                value.delete(transaction);
            } else {
                transaction._mergeStructs.add(value);
            }
        });
        transaction.changed.remove(this.type);
    }

    @Override
    public ContentType copy() {
        return new ContentType(this.type.copy());
    }

    @Override
    public Boolean mergeWith(ContentType right) {
        return false;
    }

    @Override
    public void integrate(Transaction transaction, Item item) throws Exception {
        this.type.integrate(transaction.getDoc(), item);
    }

    @Override
    public void gc(StructStore store) {
        Item item = this.type._start;
        while (item != null) {
            item.gc(store, true);
            item = (Item) item.right;
        }
        this.type._start = null;
        this.type._map.forEach((key, value) -> {
            while (value != null) {
                value.gc(store, true);
                value = (Item) value.left;
            }
        });
        this.type._map = new HashMap<>();
    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) {
        this.type.write(encoder);
    }

    @Override
    public int getRef() {
        return 7;
    }

    public static ContentType readContentType(IUpdateDecoder decoder) throws Exception {
        long typeRef = decoder.readTypeRef();
        switch ((int)typeRef)
        {
            case 0:
                YArray arr = YArray.readYArray(decoder);
                return new ContentType(arr);
            case 1:
                YMap map = YMap.readYMap(decoder);
                return new ContentType(map);
            case 2:
                YText text = YText.readText(decoder);
                return new ContentType(text);
            default:
                throw new Exception("not imp");
        }

    }
}
