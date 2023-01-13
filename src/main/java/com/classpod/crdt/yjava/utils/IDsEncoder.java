package com.classpod.crdt.yjava.utils;

import java.io.ByteArrayOutputStream;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/9/15 5:42 PM
 **/
public interface IDsEncoder {
    ByteArrayOutputStream restEncoder();
    byte[] toUint8Array();
    void resetDsCurVal();
    void writeDsClock(long clock);
    void writeDsLength(long length);
}
