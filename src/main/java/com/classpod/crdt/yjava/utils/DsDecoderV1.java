package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamDecodingExtensions;

import java.io.ByteArrayInputStream;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/16 10:43 AM
 **/
public class DsDecoderV1 implements IDsDecoder{
    public ByteArrayInputStream restDecoder;
    public DsDecoderV1(ByteArrayInputStream inputSteam){
        this.restDecoder = inputSteam;
    }

    @Override
    public ByteArrayInputStream restDecoder() {
        return restDecoder;
    }

    @Override
    public void resetDsCurval() {
        // do nothing
    }

    @Override
    public long readDsClock(){
        return StreamDecodingExtensions.readVarUInt(this.restDecoder);
    }

    @Override
    public long readDsLength() {
        return StreamDecodingExtensions.readVarUInt(this.restDecoder);
    }
}
