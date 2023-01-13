package com.classpod.crdt.yjava.utils;

import java.io.ByteArrayInputStream;
/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/9/16 1:30 PM
 **/
public interface IUpdateDecoder extends IDsDecoder{
    ByteArrayInputStream restDecoder();
    Id readLeftID();
    Id readRightID();
    long readClient();
    int readInfo();
    String readString();
    boolean readParentInfo();
    long readTypeRef();
    long readLen();
    Object readAny();
    byte[] readBuf();
    String readKey();
    Object readJson();
}
