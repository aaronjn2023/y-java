package com.classpod.crdt.yjava.types;

import com.classpod.crdt.yjava.structs.Item;
import com.classpod.crdt.yjava.utils.IUpdateDecoder;
import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.Transaction;
import com.classpod.crdt.yjava.utils.YDoc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class YArray extends AbstractType {
    private List<Object> prelimContent;
    private List<ArraySearchMarker> searchMarker;
    private AbstractType abstractType = new AbstractType();
    public YArray() {
        super();
        this.prelimContent = new ArrayList<>();
        this.searchMarker = new ArrayList<>();
    }

    public static YArray from(List<Object> items) throws NoSuchMethodException {
        YArray a = new YArray();
        a.push(items);
        return a;
    }

    @Override
    public void integrate (YDoc doc, Item item) throws Exception {
        super.integrate(doc, item);
        this.insert(0, this.prelimContent);
        this.prelimContent = new ArrayList<>();
    }

    @Override
    public YArray copy() {
        return new YArray();
    }

    @Override
    public YArray clone() {
        YArray arr = new YArray();
        List<Object> one = this.toArray();
        for(int i=0; i<one.size();i++) {
            Object k = one.get(0);
            if (k instanceof AbstractType) {
                one.set(i, ((AbstractType) k).clone());
            }
        }
        try {
            arr.insert(0, one);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return arr;
    }

    @Override
    public void callObserver(Transaction transaction, Set<String> parentSubs) throws Exception {
        super.callObserver(transaction, parentSubs);
        callTypeObservers(this, transaction, new YArrayEvent(this, transaction));
    }

    public void insert (int index, List<Object> content) throws NoSuchMethodException {
        if (this.doc != null) {
            Method method= abstractType.getClass().getMethod("typeListInsertGenerics",Transaction.class,AbstractType.class, Integer.class,List.class,Integer.class);
            Transaction.transact(this.doc, abstractType.getClass(),method, null, true, this, index, content, 0);
        } else {
            this.prelimContent.addAll(index, content);
        }
    }

    public void push(List<Object> content) throws NoSuchMethodException {
        if (this.doc != null) {
            Method method= abstractType.getClass().getMethod("typeListPushGenerics",Transaction.class,AbstractType.class, Integer.class,List.class,Integer.class);
            Transaction.transact(this.doc, abstractType.getClass(), method, null, true, this, 0, content, 0);
        } else {
            this.prelimContent.addAll(content);
        }
    }

    public void unshift(List<Object> content) throws NoSuchMethodException {
        this.insert(0, content);
    }
    public void clearSearchMarkers() {
        searchMarker.clear();
    }

    public void delete(int index, int length) throws NoSuchMethodException {
        if (this.doc != null) {
            Method method= abstractType.getClass().getMethod("typeListDelete",Transaction.class,AbstractType.class, Integer.class,List.class,Integer.class);
            Transaction.transact(this.doc, abstractType.getClass(),method, null, true, this, index, null, length);
        } else {
             this.prelimContent.add(index, length);
        }
    }

    public Object get(int index) {
        return typeListGet(this, index);
    }

    public List<Object> toArray () {
        return typeListToArray(this);
    }

    public List<Object> slice(int start, int end) {
        return typeListSlice(this, start, end);
    }
    @Override
    public List<Object> toJSON(){
        List<Object> result = new ArrayList<>();
        int index = 0;
        Item n = super._start;
        while(null != n){
            if(n.countable() && !n.deleted()){
                List<Object> c = n.content.getContent();
                for(int i=0;i<c.size();i++){
                    result.add(c);
                }
            }
            n = (Item)n.right;
        }
        return result;
//        List<Object> json = new ArrayList<>();
//        Item n = this._start;
//        while(null != n){
//            if(n.countable() && !n.deleted()){
//                List<Object> c = n.content.getContent();
//                for(int i =0;i< c.size();i++){
//                    if(c.get(i) instanceof AbstractType){
//                        json.add(JSONUtil.parseObj(c.get(i)));
//                    }else{
//                        json.add(c.get(i));
//                    }
//                }
//            }
//        }
//        return json;
    }
    public void forEach (Method method) throws InvocationTargetException, IllegalAccessException {
        typeListForEach(this, method);
    }

    @Override
    public int length() {
        return this.prelimContent == null ? this._length : this.prelimContent.size();
    }

    @Override
    public void write (IUpdateEncoder encoder) {
        encoder.writeTypeRef(0);
    }

    public static YArray readYArray(IUpdateDecoder decoder) {
        return new YArray();
    }
}
