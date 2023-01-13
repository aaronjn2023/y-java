package com.classpod.crdt.yjava;

import java.lang.reflect.Method;

public class YObserver {
    private Object sender;

    private String callback;

    public YObserver(Object sender, String callback){

        this.sender = sender;

        this.callback = callback;
    }

    public void emitNull(){
        Class senderType = this.sender.getClass();
        try {
            Method method = senderType.getMethod(this.callback);
            method.invoke(this.sender);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    public void emitArgs(Object... args){
        Class senderType = this.sender.getClass();
        try {
            Method method = senderType.getMethod(this.callback);
            method.invoke(this.sender, args);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }
}
