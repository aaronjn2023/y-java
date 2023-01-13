package com.classpod.crdt.yjava.utils;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/15 18:39 PM
 **/
@FunctionalInterface
public interface ActionFunInterface<T> {

    /**
     * Performs this operation on the given argument.
     * @param t the input argument
     */
    void action(T t) throws Exception;

}
