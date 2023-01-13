package com.classpod.crdt.yjava.utils;

import cn.hutool.json.JSONUtil;
import com.classpod.crdt.y.lib.StreamDecodingExtensions;

import java.io.ByteArrayInputStream;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/16 10:49 AM
 **/
public class UpdateDecoderV1 extends DsDecoderV1 implements IUpdateDecoder{
    public UpdateDecoderV1(ByteArrayInputStream inputSteam) {
        super(inputSteam);
    }

    @Override
    public ByteArrayInputStream restDecoder() {
        return restDecoder;
    }

    @Override
    public Id readLeftID(){
        return new Id((long) StreamDecodingExtensions.readVarUInt(this.restDecoder),(long)StreamDecodingExtensions.readVarUInt(this.restDecoder));
    }

    @Override
    public Id readRightID(){
        return new Id((long) StreamDecodingExtensions.readVarUInt(this.restDecoder),(long)StreamDecodingExtensions.readVarUInt(this.restDecoder));
    }

    @Override
    public long readClient(){
        long value = StreamDecodingExtensions.readVarUInt(this.restDecoder);
        return value;
    }

    @Override
    public int readInfo(){
        return StreamDecodingExtensions.readUint8(this.restDecoder);
    }

    @Override
    public String readString(){
        return StreamDecodingExtensions.readVarString(this.restDecoder);
    }

    @Override
    public boolean readParentInfo() {
        return StreamDecodingExtensions.readVarUInt(this.restDecoder) == 1L;
    }

    @Override
    public long readTypeRef() {
        return StreamDecodingExtensions.readVarUInt(this.restDecoder);
    }

    @Override
    public long readLen() {
        return StreamDecodingExtensions.readVarUInt(this.restDecoder);
    }

    @Override
    public Object readAny() {
        return StreamDecodingExtensions.readAny(this.restDecoder);
    }

    @Override
    public byte[] readBuf() {
        return StreamDecodingExtensions.readVarUint8Array(this.restDecoder);
    }

    @Override
    public String readKey() {
        return StreamDecodingExtensions.readVarString(this.restDecoder);
    }

    @Override
    public Object readJson() {
        return JSONUtil.parseObj(StreamDecodingExtensions.readVarString(this.restDecoder));
    }
}
