package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamDecodingExtensions;

import java.io.ByteArrayInputStream;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/9/16 1:35 PM
 **/
public class DsDecoderV2 implements IDsDecoder{
    private long dsCurVal;

    public ByteArrayInputStream restDecoder;
    public DsDecoderV2(ByteArrayInputStream input){
        this.restDecoder = input;
        this.dsCurVal = 0;
    }

    @Override
    public ByteArrayInputStream restDecoder() {
        return restDecoder;
    }

    @Override
    public void resetDsCurval() {
        dsCurVal = 0;
    }

    @Override
    public long readDsClock() {
        this.dsCurVal += StreamDecodingExtensions.readVarUInt(this.restDecoder);
        return this.dsCurVal;
    }

    @Override
    public long readDsLength() {
        long diff = StreamDecodingExtensions.readVarUInt(this.restDecoder) + 1;
        dsCurVal += diff;
        return diff;
    }
}
