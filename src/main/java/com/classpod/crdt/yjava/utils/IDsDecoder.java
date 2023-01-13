package com.classpod.crdt.yjava.utils;

import java.io.ByteArrayInputStream;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/9/16 11:35 AM
 **/
public interface IDsDecoder {
    ByteArrayInputStream restDecoder();
    void resetDsCurval();
    long readDsClock();
    long readDsLength();
}
