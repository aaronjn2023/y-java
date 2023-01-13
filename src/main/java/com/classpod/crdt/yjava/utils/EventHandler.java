package com.classpod.crdt.yjava.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EventHandler {
    public List<Method> function;
    public EventHandler(){
        this.function = new ArrayList<>();
    }

    public static EventHandler createEventHandler() {
        return new EventHandler();
    }

    public void addEventHandlerListener(Method method) {
        this.function.add(method);
    }

    public void removeEventHandlerListener(Method method) {
        int len = this.function.size();
        this.function = function.stream().filter((Method b) -> b != method).collect(Collectors.toList());
    }

    public void removeAllEventHandlerListeners() {
        this.function = new ArrayList<>();
    }

    public static void callEventHandlerListeners(EventHandler eventHandler, Object obj, Object arg1) throws InvocationTargetException, IllegalAccessException {
        for (Method method : eventHandler.function) {
            method.invoke(obj, arg1);
        }
    }



}