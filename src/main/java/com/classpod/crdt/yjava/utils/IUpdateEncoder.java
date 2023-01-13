package com.classpod.crdt.yjava.utils;

import java.io.ByteArrayOutputStream;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/9/15 5:01 PM
 **/
public interface IUpdateEncoder extends IDsEncoder{
    ByteArrayOutputStream restEncoder();
    void writeLeftID(Id id);
    void writeRightID(Id id);
    void writeClient(long client);
    void writeInfo(int info);
    void writeString(String str);
    void writeParentInfo(boolean isYKey);
    void writeTypeRef(long info);
    void writeLen(long len);
    void writeAny(Object obj);
    void writeBuf(byte[] buf);
    void writeKey(String key);
    void writeJson(String json);
}
