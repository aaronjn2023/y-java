package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import java.io.ByteArrayOutputStream;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/9/15 5:05 PM
 **/
public class DsEncoderV2 implements IDsEncoder{
    private long dsCurVal;
    public ByteArrayOutputStream restEncoder;
    public DsEncoderV2(){
        dsCurVal = 0;
        restEncoder = new ByteArrayOutputStream();
    }

    @Override
    public ByteArrayOutputStream restEncoder() {
        return restEncoder;
    }

    @Override
    public byte[] toUint8Array() {
        return StreamEncodingExtensions.toUint8Array(this.restEncoder);
    }

    @Override
    public void resetDsCurVal() {
        dsCurVal = 0;
    }

    @Override
    public void writeDsClock(long clock) {
        long diff = clock - this.dsCurVal;
        this.dsCurVal = clock;
        StreamEncodingExtensions.writeVarUint(this.restEncoder,diff);
    }

    @Override
    public void writeDsLength(long length) {
        if(length == 0){
            throw new RuntimeException("argument outOf range.");
        }
        StreamEncodingExtensions.writeVarUint(this.restEncoder,length -1L);
        this.dsCurVal += length;
    }
}
