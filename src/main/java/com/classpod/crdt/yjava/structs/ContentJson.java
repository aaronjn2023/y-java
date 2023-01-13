package com.classpod.crdt.yjava.structs;

import cn.hutool.json.JSONUtil;
import com.classpod.crdt.yjava.utils.IUpdateDecoder;
import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.StructStore;
import com.classpod.crdt.yjava.utils.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/12/9 2:16 PM
 **/
public class ContentJson implements AbstractContent<ContentJson>{

    private List<Object> arr;

    public ContentJson(List<Object> arr){
        this.arr = arr;
    }

    @Override
    public int length() {
        return this.arr.size();
    }

    @Override
    public Boolean countable() {
        return true;
    }

    @Override
    public AbstractContent copy() {
        return new ContentJson(this.arr);
    }

    @Override
    public AbstractContent splice(int offset) {
        ContentJson right = new ContentJson(this.arr.subList(offset,length()));
        this.arr = this.arr.subList(0,offset);
        return right;
    }

    @Override
    public List<Object> getContent() {
        return this.arr;
    }

    @Override
    public Boolean mergeWith(ContentJson right){
        this.arr = Collections.singletonList(this.arr.addAll(right.arr));
        return true;
    }

    public static ContentJson readContentJson(IUpdateDecoder decoder){
        long len = decoder.readLen();
        List<Object> cs = new ArrayList<>();
        for(int i = 0;i<len;i++){
            String c = decoder.readString();
            if(c == null){
                cs.add(null);
            }else{
                cs.add(JSONUtil.toJsonStr(c));
            }
        }
        return new ContentJson(cs);
    }

    @Override
    public void integrate(Transaction transaction, Item item) throws Exception {

    }

    @Override
    public void delete(Transaction transaction) {

    }

    @Override
    public void gc(StructStore store) {

    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) {
        int len = this.arr.size();
        encoder.writeLen(len - offset);
        for(int i=offset;i<len;i++){
            Object c = this.arr.get(i);
            encoder.writeString(null == c ? null : JSONUtil.toJsonStr(c));
        }
    }

    @Override
    public int getRef() {
        return 2;
    }
}
