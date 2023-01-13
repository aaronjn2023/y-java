package com.classpod.crdt.yjava.utils;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/21 11:03 AM
 **/
@FunctionalInterface
public interface ComputeYChangeFun<N, T, R> {

    R compute(N n, T t);
}
