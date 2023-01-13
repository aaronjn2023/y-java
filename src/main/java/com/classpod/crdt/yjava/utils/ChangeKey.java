package com.classpod.crdt.yjava.utils;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/21 15:38 PM
 **/
public class ChangeKey {

    //Add:1 Update:2 Delete:3
    public Integer action;
    public Object oldValue;

    public ChangeKey(){

    }

    public ChangeKey(Integer action, Object oldValue) {
        this.action = action;
        this.oldValue = oldValue;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public void setOldValue(Object oldValue) {
        this.oldValue = oldValue;
    }
}
