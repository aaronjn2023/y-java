package com.classpod.crdt.yjava.types;

import com.classpod.crdt.yjava.structs.Item;
import com.classpod.crdt.yjava.utils.IUpdateDecoder;
import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.Transaction;
import com.classpod.crdt.yjava.utils.YDoc;

import java.util.*;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/14 13:31 PM
 **/
public class YMap extends AbstractType {

    private Map<String, Object> prelimContent;

    public YMap() {
        super();
        this.prelimContent = new HashMap<>();
    }

    public YMap(Map<String, Object> entries) {
        super();
        this.prelimContent = entries != null ? new HashMap<>(entries) : new HashMap<>();
    }

    @Override
    public void integrate(YDoc doc,Item item) throws Exception {
        super.integrate(doc,item);
        this.prelimContent.forEach((key,value) ->{
            try {
                this.set(key,value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        this.prelimContent = new HashMap<>();
    }

    @Override
    public YMap copy(){
        return new YMap();
    }

    public int size() {
        return prelimContent == null ? typeMapEnumerate().size() : prelimContent.size();
    }

    public Object get(String key) {
        return typeMapGet(this,key);
    }

    public Object set(String key, Object value) throws Exception {
        if (doc != null) {
            doc.transact(transaction -> typeMapSet(transaction,this, key, value), null, true);
        } else {
            prelimContent.put(key, value);
        }
        return value;
    }

    public void delete(String key) throws Exception {
        if (doc != null) {
            doc.transact(tr -> typeMapDelete(tr,this, key), null, true);
        } else {
            prelimContent.remove(key);
        }
    }

    public Boolean has(String key){
        return typeMapHas(this,key);
    }

    public boolean containsKey(String key) {
        Item val = _map.get(key);
        return val != null && !val.deleted();
    }

    public Set<String> keys() {
        return typeMapEnumerate().keySet();
    }

    public Collection<Item> values() {
        return typeMapEnumerate().values();
    }

    @Override
    public void callObserver(Transaction transaction,Set<String> parentSubs) throws Exception {
        callTypeObservers(this,transaction,new YMapEvent(this,transaction,parentSubs));
    }

    @Override
    public Object toJSON(){
        Map<String,Object> map = new HashMap<>();
        this._map.forEach((key,item) ->{
            if(!item.deleted()){
                Object v = item.content.getContent().get(item.getLength() - 1);
                map.put(key,v instanceof AbstractType ? ((AbstractType) v).toJSON() : v);
            }
        });
        return map;
    }

    @Override
    public void write (IUpdateEncoder encoder) {
        encoder.writeTypeRef(1);
    }

    @Override
    public YMap clone() {
        YMap newMap = new YMap();
        for (Map.Entry<String, Item> entry : typeMapEnumerate().entrySet()) {
            try {
                Item value = entry.getValue();
                if(AbstractType.class.isAssignableFrom(value.getClass())){
                    // TODO 这个地方可能有些问题 value.clone();
                    newMap.set(entry.getKey(),value);
                }else{
                    newMap.set(entry.getKey(),value);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return newMap;
    }

    public void clear() throws Exception {
        if(null != this.doc){
            doc.transact(tr -> {
                typeMapEnumerate().forEach((key,value) -> {
                    typeMapDelete(tr,this,key);
                });
            }, null, true);
        }else{
            this.prelimContent.clear();
        }
    }

    public static YMap readYMap(IUpdateDecoder decoder) {
        return new YMap();
    }



}
