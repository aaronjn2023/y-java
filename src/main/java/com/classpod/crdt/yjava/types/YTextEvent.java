package com.classpod.crdt.yjava.types;

import com.classpod.crdt.yjava.structs.*;
import com.classpod.crdt.yjava.utils.*;

import java.util.*;
/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/16 15:58 PM
 **/
public class YTextEvent extends YEvent {

    private static final int DELETE = 1;
    private static final int INSERT = 2;
    private static final int RETAIN = 3;

    private Set<String> _subs;

    private List<Delta> _delta = null;

    public Set<String> keysChanged;

    public Boolean childListChanged;

    public YTextEvent(YText arr, Transaction transaction, Set<String> subs) {
        super(arr, transaction);
        this._subs = subs;
        this.keysChanged = new HashSet<>();
        if (this._subs != null && !this._subs.isEmpty()) {
            for (String sub : this._subs) {
                if (sub == null) {
                    this.childListChanged = true;
                } else {
                    this.keysChanged.add(sub);
                }
            }
        }

    }

    private void addOp(MutableInteger action, MutableInteger deleteLen, MutableInteger retain, Object insert,
        Map<String, Object> currentAttributes, Map<String, Object> attributes, List<Delta> delta) {
        Delta op;
        switch (action.get()) {
            case DELETE:
                op = new Delta();
                op.delete = deleteLen.get();
                deleteLen.set(0);
                break;
            case INSERT:
                op = new Delta();
                op.insert = insert;
                if (!currentAttributes.isEmpty()) {
                    op.attributes = new HashMap<>();
                    currentAttributes.forEach((k, v) -> {
                        if (v != null) {
                            op.attributes.put(k, v);
                        }
                    });
                }
                insert = "";
                break;
            case RETAIN:
                op = new Delta();
                op.retain = retain.get();
                if (!attributes.isEmpty()) {
                    op.attributes = new HashMap<>();
                    op.attributes.putAll(attributes);
                }
                retain.set(0);
                break;
            default:
                return;
        }
        delta.add(op);
        action.set(0);
    }

    public List<Delta> delta() throws Exception {
        if (_delta == null) {
            YDoc doc = target.doc;
            List<Delta> delta = new ArrayList<>();
            doc.transact(transaction -> {
//                List<Delta> delta = _delta;
                // Saves all current attributes for insert.
                Map<String, Object> currentAttributes = new HashMap<>();
                Map<String, Object> oldAttributes = new HashMap<>();
                Item item = target._start;
                MutableInteger action = new MutableInteger();
                Map<String, Object> attributes = new HashMap<>();
                Object insert = "";
                MutableInteger retain = new MutableInteger();
                MutableInteger deleteLen = new MutableInteger();
                while (item != null) {
                    if (item.content instanceof ContentEmbed) {
                        if (adds(item)) {
                            if (!deletes(item)) {
                                addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                                action.set(INSERT);
                                insert = item.content.getContent().get(0);
                                addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                            }
                        } else if (deletes(item)) {
                            if (action.get() != DELETE) {
                                addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                                action.set(DELETE);
                            }
                            deleteLen.incr();
                        } else if (!item.deleted()) {
                            if (action.get() != RETAIN) {
                                addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                                action.set(RETAIN);
                            }
                            retain.incr();
                        }
                    } else if (item.content instanceof ContentString) {
                        if (adds(item)) {
                            if (!deletes(item)) {
                                if (action.get() != INSERT) {
                                    addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                                    action.set(INSERT);
                                }
                                insert += ((ContentString)item.content).str;
                            }
                        } else if (deletes(item)) {
                            if (action.get() != DELETE) {
                                addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                                action.set(DELETE);
                            }
                            deleteLen.incr(item.getLength());
                        } else if (!item.deleted()) {
                            if (action.get() != RETAIN) {
                                addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                                action.set(RETAIN);
                            }
                            retain.incr(item.getLength());
                        }
                    } else if (item.content instanceof ContentFormat) {
                        ContentFormat cf = (ContentFormat)item.content;
                        if (adds(item)) {
                            if (!deletes(item)) {
                                Object curVal = currentAttributes.get(cf.getKey());
                                if (!YText.equalAttrs(cf.getKey(), curVal)) {
                                    if (action.get() == RETAIN) {
                                        addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                                    }
                                    Object oldVal = oldAttributes.get(cf.getKey());
                                    if (YText.equalAttrs(cf.getValue(), oldVal)) {
                                        attributes.remove(cf.getKey());
                                    } else {
                                        attributes.put(cf.getKey(), cf.getValue());
                                    }
                                } else if(null != cf.getValue()) {
                                    item.delete(transaction);
                                }
                            }
                        } else if (deletes(item)) {
                            oldAttributes.put(cf.getKey(), cf.getValue());
                            Object curVal = currentAttributes.get(cf.getKey());
                            if (!YText.equalAttrs(curVal, cf.getValue())) {
                                if (action.get() == RETAIN) {
                                    addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                                }
                                attributes.put(cf.getKey(), curVal);
                            }
                        } else if (!item.deleted()) {
                            oldAttributes.put(cf.getKey(), cf.getValue());
                            if (attributes.containsKey(cf.getKey())) {
                                Object attr = attributes.get(cf.getKey());
                                if (!YText.equalAttrs(attr, cf.getValue())) {
                                    if (action.get() == RETAIN) {
                                        addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                                    }
                                    if (cf.getValue() == null) {
                                        attributes.remove(cf.getKey());
                                    } else {
                                        attributes.put(cf.getKey(), cf.getValue());
                                    }
                                } else if(null != attr){
                                    item.delete(transaction);
                                }
                            }
                        }
                        if (!item.deleted()) {
                            if (action.get() == INSERT) {
                                addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                            }
                            YText.updateCurrentAttributes(currentAttributes, (ContentFormat)item.content);
                        }
                    }
                    item = (Item)item.right;
                }
                addOp(action, deleteLen, retain, insert, currentAttributes, attributes, delta);
                while (delta.size() > 0) {
                    Delta lastOp = delta.get(delta.size() - 1);
                    if (lastOp.retain != null && lastOp.attributes == null) {
                        // Retain delta's if they don't assign attributes.
                        delta.remove(delta.size() - 1);
                    } else {
                        break;
                    }
                }
            }, null, true);
            this._delta = delta;
        }
        return this._delta;
    }
}




