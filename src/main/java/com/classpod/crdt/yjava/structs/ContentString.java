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
 * @Date 2022/12/12 2:53 PM
 **/
public class ContentString implements AbstractContent<ContentString>{
    public String str;

    public ContentString(String str){
        this.str = str;
    }

    @Override
    public int length() {
        return this.str.length();
    }

    @Override
    public Boolean countable() {
        return true;
    }

    @Override
    public AbstractContent copy() {
        return new ContentString(this.str);
    }

    @Override
    public AbstractContent splice(int offset) {
        ContentString right = new ContentString(this.str.substring(offset));
        this.str = this.str.substring(0,offset);
        char firstCharCode = this.str.charAt(offset - 1);
        if(firstCharCode >= 0xD800 && firstCharCode <= 0xDBFF){
            this.str = this.str.substring(0,offset - 1) + '�';
            right.str =  '�' + right.str.substring(1);
        }
        return right;
    }

    @Override
    public Boolean mergeWith(ContentString right){
        this.str += right.str;
        return true;
    }

    @Override
    public List<Object> getContent() {
        List<Object> list = new ArrayList<>();
        String[] temStr = this.str.split("''");
        for(int i = 0;i< temStr.length;i++){
            list.add(temStr[i]);
        }
        return list;
    }

    public String getString(){
        return this.str;
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

    public static ContentString readContentString(IUpdateDecoder decoder){
        return new ContentString(decoder.readString());
    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) {
        encoder.writeString(0 == offset ? this.str : this.str.substring(offset));
    }

    @Override
    public int getRef() {
        return 4;
    }
}
