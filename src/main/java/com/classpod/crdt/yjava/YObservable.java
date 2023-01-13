package com.classpod.crdt.yjava;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Vector;

public class YObservable extends Observable {
    private boolean changed = false;
    public Map<String, Vector<YObserver>> obs;

    public YObservable() {
        obs = new HashMap<>();
    }

    public synchronized void addObserver(String eventName, YObserver o) {
        if (o == null) {
            throw new NullPointerException();
        }
        if (!obs.containsKey(eventName)) {
            Vector<YObserver> one = new Vector<>();

            one.add(o);
            obs.put(eventName, one);
        } else {
            obs.get(eventName).add(o);
        }
    }

    public void notifyObserversNull(String eventName) {
        Object[] arrLocal;

        synchronized (this) {
            if (!changed){
                return;
            }
            arrLocal = obs.get(eventName).toArray();
            clearChanged();
        }

        for (int i = arrLocal.length-1; i>=0; i--) {
            ((YObserver) arrLocal[i]).emitNull();
        }
    }

    public void notifyObservers(String eventName, Object... arg) {
        Object[] arrLocal;

        synchronized (this) {
//            if (!changed){
//                return;
//            }


            Vector<YObserver> v = obs.get(eventName);
            if(v == null || v.isEmpty()){
                return;
            }
            arrLocal = v.toArray();
            clearChanged();
        }

        for (int i = arrLocal.length-1; i>=0; i--) {
            ((YObserver) arrLocal[i]).emitArgs(arg);
        }
    }

    protected synchronized void setChanged() {
        changed = true;
    }

    protected synchronized void clearChanged() {
        changed = false;
    }

    public void destroy () throws Exception {
        obs = new HashMap<>();
    }


}
