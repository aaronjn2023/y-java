package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.yjava.structs.AbstractStruct;
import com.classpod.crdt.yjava.structs.Item;
import com.classpod.crdt.yjava.types.AbstractType;

import java.util.*;

public class YEvent {
    public AbstractType target;
    public AbstractType currentTarget;
    public Transaction transaction;
    public ChangesCollection changes = null;

    private static final int CHANGE_ACTION_ADD = 1;
    private static final int CHANGE_ACTION_UPDATE = 2;
    private static final int CHANGE_ACTION_DELETE = 3;
    private Object _changes = null;
    private Map<String,ChangeKey> _keys = null;
    private Delta _delta = null;

    public YEvent(AbstractType target, Transaction transaction) {
        this.target = target;
        this.currentTarget = target;
        this.transaction = transaction;
    }

    public Collection<Object> path() {
        return getPathTo(currentTarget, target);
    }

//    public ChangesCollection changes() {
//        return collectChanges();
//    }

    public boolean adds(AbstractStruct str) {
        Id id = str.getId();
        Long v = this.transaction.beforeState.get(id.getClient());
        if (v == null) {
            v = 0L;
        }
        return id.getClock() >= v;
    }

    public boolean deletes(AbstractStruct str) {
        return DeleteSet.isDeleted(transaction.deleteSet, str.getId());
    }

    private void packOp(Delta lastOp, List<Delta> delta) {
        if (lastOp != null) {
            delta.add(lastOp);
        }
    }

    private Map<String,ChangeKey> keys(){
        if(null == this._keys){
            Map<String,ChangeKey> keys = new HashMap<>();
            AbstractType target = this.target;
            Set<String> changed = this.transaction.changed.get(target);
            changed.forEach(key->{
                if(null != key){
                    Item item = target._map.get(key);
                    ChangeKey changeKey = new ChangeKey();
                    if(this.adds(item)){
                        AbstractStruct prev = item.left;
                        while(null != prev && this.adds(prev)){
                            prev = ((Item)prev).left;
                        }
                        if(this.deletes(item)){
                            if(null != prev && this.deletes(prev)){
                                changeKey.setAction(3);
                                Item tempItem = (Item)prev;
                                if(null != tempItem.content && null != tempItem.content.getContent()){
                                    int len = tempItem.content.getContent().size();
                                    changeKey.setOldValue(tempItem.content.getContent().get(len - 1));
                                }else{
                                    return;
                                }
                            }
                        }else{
                            if(null != prev && this.deletes(prev)){
                                changeKey.setAction(2);
                                Item tempItem = (Item)prev;
                                if(null != tempItem.content && null != tempItem.content.getContent()){
                                    int len = tempItem.content.getContent().size();
                                    changeKey.setOldValue(tempItem.content.getContent().get(len - 1));
                                }
                            }else{
                                changeKey.setAction(1);
                                changeKey.setOldValue(null);
                            }
                        }
                    }else{
                        if(this.deletes(item)){
                            changeKey.setAction(3);
                            if(null != item.content && null != item.content.getContent()){
                                int len = item.content.getContent().size();
                                changeKey.setOldValue(item.content.getContent().get(len - 1));
                            }
                        }else{
                            return;
                        }
                    }
                    keys.put(key,changeKey);
                }
            });
            this._keys = keys;
        }
        return this._keys;
    }

    private Object changes(){
        Object changes = this._changes;
        if(null == changes){
            AbstractType target = this.target;
            Set added = new HashSet();
            Set deleted = new HashSet();
            List<Delta> delta = new ArrayList<>();
            YEventChanges tempChanges = new YEventChanges(this.keys());
            Set<String> changed = this.transaction.changed.get(target);
            if(changed.contains(null)){
                Delta lastOp = null;
                for(Item item = target._start;null != item;item = (Item) item.right){
                    if(item.deleted()){
                        if(this.deletes(item) && !this.adds(item)){
                            if(null == lastOp || lastOp.delete == null){
                                packOp(lastOp,delta);
                                lastOp = new Delta();
                                lastOp.delete = 0;
                            }
                            lastOp.delete += item.getLength();
                            deleted.add(item);
                            tempChanges.setDeleted(deleted);
                        }
                    }else{
                        if (adds(item)) {
                            if (lastOp == null || lastOp.insert == null) {
                                packOp(lastOp, delta);
                                lastOp = new Delta();
                                lastOp.insert = new ArrayList<>(1);
                            }
                            lastOp.insert = ((ArrayList<Object>)lastOp.insert).addAll(item.content.getContent());
                            added.add(item);
                            tempChanges.setAdded(added);
                        } else {
                            if (lastOp == null || lastOp.retain == null) {
                                packOp(lastOp, delta);
                                lastOp = new Delta();
                                lastOp.retain = 0;
                            }
                            lastOp.retain += item.getLength();
                        }
                    }
                }
                if (lastOp != null && lastOp.retain == null) {
                    packOp(lastOp, delta);
                    tempChanges.setDelta(delta);
                }
            }
            this._changes = tempChanges;
            changes = tempChanges;
        }
        return changes;
    }

    private ChangesCollection collectChanges() {
        if (this.changes == null) {
            AbstractType tempTarget = this.target;
            HashSet<Item> added = new HashSet<>();
            HashSet<Item> deleted = new HashSet<>();
            ArrayList<Delta> delta = new ArrayList<>();
            HashMap<String, ChangeKey> keys = new HashMap<>();
            this.changes = new ChangesCollection(added, deleted, delta, keys);
            Set<String> changed = this.transaction.changed.computeIfAbsent(tempTarget, k -> new HashSet<>());
            if (changed.contains(null)) {
                Delta lastOp = null;
                for (Item item = this.target._start; item != null; item = (Item)item.right) {
                    if (item.deleted()) {
                        if (deletes(item) && !adds(item)) {
                            if (lastOp == null || lastOp.delete == null) {
                                packOp(lastOp, delta);
                                lastOp = new Delta();
                                lastOp.delete = 0;
                            }
                            lastOp.delete += item.getLength();
                            deleted.add(item);
                        } else {
                            // Do nothing.
                        }
                    } else {
                        if (adds(item)) {
                            if (lastOp == null || lastOp.insert == null) {
                                packOp(lastOp, delta);
                                lastOp = new Delta();
                                lastOp.insert = new ArrayList<>(1);
                            }
                            ((ArrayList<Object>)lastOp.insert).addAll(item.content.getContent());
                            added.add(item);
                        } else {
                            if (lastOp == null || lastOp.retain == null) {
                                packOp(lastOp, delta);
                                lastOp = new Delta();
                                lastOp.retain = 0;
                            }
                            lastOp.retain += item.getLength();
                        }
                    }
                }
                if (lastOp != null && lastOp.retain == null) {
                    packOp(lastOp, delta);
                }
            }
            for (String key : changed) {
                if (key != null) {
                    int action;
                    Object oldValue;
                    Item item = tempTarget._map.get(key);
                    if (adds(item)) {
                        AbstractStruct prev = item.left;
                        while (prev != null && adds(prev)) {
                            prev = ((Item)prev).left;
                        }
                        if (deletes(item)) {
                            if (prev != null && deletes(prev)) {
                                action = CHANGE_ACTION_DELETE;
                                List<Object> list = ((Item)prev).content.getContent();
                                oldValue = list.get(list.size() - 1);
                            } else {
                                break;
                            }
                        } else {
                            if (prev != null && deletes(prev)) {
                                action = CHANGE_ACTION_UPDATE;
                                List<Object> list = ((Item)prev).content.getContent();
                                oldValue = list.get(list.size() - 1);
                            } else {
                                action = CHANGE_ACTION_ADD;
                                oldValue = null;
                            }
                        }
                    } else {
                        if (deletes(item)) {
                            action = CHANGE_ACTION_DELETE;
                            List<Object> list = item.content.getContent();
                            oldValue = list.get(list.size() - 1);
                        } else {
                            break;
                        }
                    }
                    keys.put(key, new ChangeKey(action, oldValue));
                }
            }
        }
        return changes;
    }

    /// <summary>
    /// Compute the path from this type to the specified target.
    /// </summary>
    private Collection<Object> getPathTo(AbstractType parent, AbstractType child) {
        ArrayDeque<Object> path = new ArrayDeque<>();
        while (child._item != null && child != parent) {
            String parentSub = child._item.parentSub;
            if (parentSub != null && parentSub.length() != 0) {
                // Parent is map-ish.
                path.push(parentSub);
            } else {
                // Parent is array-ish.
                int i = 0;
                AbstractStruct c = ((AbstractType)child._item.parent)._start;
                while (c != child._item && c != null) {
                    if (!c.deleted()) {
                        i++;
                    }
                    c = ((Item)c).right;
                }
                path.push(i);
            }
            child = (AbstractType)child._item.parent;
        }
        return path;
    }

}
