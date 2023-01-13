package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.yjava.utils.IUpdateDecoder;
import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.StructStore;
import com.classpod.crdt.yjava.utils.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/19 10:42 AM
 **/
public class ContentEmbed implements AbstractContent<ContentEmbed> {

    private Object embed;

    public ContentEmbed(Object embed) {
        this.embed = embed;
    }

    public Object getEmbed() {
        return embed;
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
        return new ContentEmbed(embed);
    }

    @Override
    public AbstractContent splice(int offset) {
        return null;
    }

    @Override
    public List<Object> getContent() {
        ArrayList<Object> objects = new ArrayList<>();
        objects.add(embed);
        return objects;
    }

    @Override
    public Boolean mergeWith(ContentEmbed right) {
        return false;
    }

    @Override
    public void integrate(Transaction transaction, Item item) throws NoSuchMethodException {
        // Do nothing.
    }

    @Override
    public void delete(Transaction transaction) {
        // Do nothing.
    }

    @Override
    public void gc(StructStore store) {
        // Do nothing.
    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) {
        encoder.writeJson((String)this.embed);
    }

    public static ContentEmbed read(IUpdateDecoder decoder) {
        return new ContentEmbed(decoder.readJson());
    }

    @Override
    public int getRef() {
        return 5;
    }
}
