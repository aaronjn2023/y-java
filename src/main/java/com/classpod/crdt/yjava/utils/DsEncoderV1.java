package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamEncodingExtensions;

import java.io.ByteArrayOutputStream;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/16 11:25 AM
 **/
public class DsEncoderV1 implements IDsEncoder{
    public ByteArrayOutputStream restEncoder;
    public DsEncoderV1(){
        restEncoder = new ByteArrayOutputStream();
    }

    @Override
    public void resetDsCurVal(){
    }

    @Override
    public void writeDsClock(long clock) {
        StreamEncodingExtensions.writeVarUint(this.restEncoder,clock);
    }

    @Override
    public void writeDsLength(long length) {
        StreamEncodingExtensions.writeVarUint(this.restEncoder,length);
    }

    @Override
    public ByteArrayOutputStream restEncoder() {
        return restEncoder;
    }

    @Override
    public byte[] toUint8Array(){
        return StreamEncodingExtensions.toUint8Array(this.restEncoder);
    }
}
