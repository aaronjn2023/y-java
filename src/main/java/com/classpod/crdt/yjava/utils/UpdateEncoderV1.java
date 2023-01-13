package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamEncodingExtensions;

import java.io.ByteArrayOutputStream;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/16 11:33 AM
 **/
public class UpdateEncoderV1 extends DsEncoderV1 implements IUpdateEncoder{

    @Override
    public ByteArrayOutputStream restEncoder() {
        return restEncoder;
    }

    @Override
    public void writeLeftID(Id id) {
        StreamEncodingExtensions.writeVarUint(this.restEncoder,id.getClient());
        StreamEncodingExtensions.writeVarUint(this.restEncoder,id.getClock());
    }

    @Override
    public void writeRightID(Id id) {
        StreamEncodingExtensions.writeVarUint(this.restEncoder,id.getClient());
        StreamEncodingExtensions.writeVarUint(this.restEncoder,id.getClock());
    }

    @Override
    public void writeClient(long client) {
        StreamEncodingExtensions.writeVarUint(this.restEncoder,client);
    }

    @Override
    public void writeInfo(int info) {
        StreamEncodingExtensions.writeUint8(this.restEncoder,info);
    }

    @Override
    public void writeString(String str) {
        StreamEncodingExtensions.writeVarString(this.restEncoder,str);
    }

    @Override
    public void writeParentInfo(boolean isYKey) {
        StreamEncodingExtensions.writeVarUint(this.restEncoder,(isYKey ? 1L : 0L));
    }

    @Override
    public void writeTypeRef(long info) {
        StreamEncodingExtensions.writeVarUint(this.restEncoder,info);
    }

    @Override
    public void writeLen(long len) {
        StreamEncodingExtensions.writeVarUint(this.restEncoder,len);
    }

    @Override
    public void writeAny(Object obj) {
        StreamEncodingExtensions.writeAny(this.restEncoder,obj);
    }

    @Override
    public void writeBuf(byte[] buf) {
        StreamEncodingExtensions.writeVarUint8Array(this.restEncoder,buf);
    }

    @Override
    public void writeKey(String key) {
        StreamEncodingExtensions.writeVarString(this.restEncoder,key);
    }

    @Override
    public void writeJson(String json) {
        StreamEncodingExtensions.writeVarString(this.restEncoder,json);
    }
}
