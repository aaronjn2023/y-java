package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import com.classpod.crdt.y.lib.encoding.IntDiffOptRleEncoder;
import com.classpod.crdt.y.lib.encoding.RleEncoder;
import com.classpod.crdt.y.lib.encoding.StringEncoder;
import com.classpod.crdt.y.lib.encoding.UintOptRleEncoder;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/9/15 7:07 PM
 **/
public class UpdateEncoderV2 extends DsEncoderV2 implements IUpdateEncoder{
    private int keyClock;
    private Map<String,Long> keyMap;
    private IntDiffOptRleEncoder keyClockEncoder;
    private UintOptRleEncoder clientEncoder;
    private IntDiffOptRleEncoder leftClockEncoder;
    private IntDiffOptRleEncoder rightClockEncoder;
    private RleEncoder infoEncoder;
    private StringEncoder stringEncoder;
    private RleEncoder parentInfoEncoder;
    private UintOptRleEncoder typeRefEncoder;
    private UintOptRleEncoder lengthEncoder;

    public UpdateEncoderV2(){
        super();
        keyClock = 0;
        keyMap = new HashMap<>();
        keyClockEncoder = new IntDiffOptRleEncoder();
        clientEncoder = new UintOptRleEncoder();
        leftClockEncoder = new IntDiffOptRleEncoder();
        rightClockEncoder = new IntDiffOptRleEncoder();
        infoEncoder = new RleEncoder();
        stringEncoder = new StringEncoder();
        parentInfoEncoder = new RleEncoder();
        typeRefEncoder = new UintOptRleEncoder();
        lengthEncoder = new UintOptRleEncoder();
    }

    @Override
    public byte[] toUint8Array(){
        ByteArrayOutputStream encoder = new ByteArrayOutputStream();
        StreamEncodingExtensions.writeVarUint(encoder,0L);
        StreamEncodingExtensions.writeVarUint8Array(encoder,keyClockEncoder.toUint8Array());
        StreamEncodingExtensions.writeVarUint8Array(encoder,clientEncoder.toUint8Array());
        StreamEncodingExtensions.writeVarUint8Array(encoder,leftClockEncoder.toUint8Array());
        StreamEncodingExtensions.writeVarUint8Array(encoder,rightClockEncoder.toUint8Array());
        StreamEncodingExtensions.writeVarUint8Array(encoder,StreamEncodingExtensions.toUint8Array(this.infoEncoder));
        StreamEncodingExtensions.writeVarUint8Array(encoder,stringEncoder.toUint8Array());
        StreamEncodingExtensions.writeVarUint8Array(encoder,StreamEncodingExtensions.toUint8Array(this.parentInfoEncoder));
        StreamEncodingExtensions.writeVarUint8Array(encoder,typeRefEncoder.toUint8Array());
        StreamEncodingExtensions.writeVarUint8Array(encoder,lengthEncoder.toUint8Array());
        StreamEncodingExtensions.writeVarUint8Array(encoder,StreamEncodingExtensions.toUint8Array(this.restEncoder));
        return StreamEncodingExtensions.toUint8Array(encoder);
    }

    @Override
    public ByteArrayOutputStream restEncoder() {
        return restEncoder;
    }

    @Override
    public void writeLeftID(Id id) {
        clientEncoder.write(id.getClient());
        leftClockEncoder.write(id.getClock());
    }

    @Override
    public void writeRightID(Id id) {
        clientEncoder.write(id.getClient());
        rightClockEncoder.write(id.getClock());
    }

    @Override
    public void writeClient(long client) {
        clientEncoder.write(client);
    }

    @Override
    public void writeInfo(int info) {
        infoEncoder.write(info);
    }

    @Override
    public void writeString(String str) {
        stringEncoder.write(str);
    }

    @Override
    public void writeParentInfo(boolean isYKey) {
        parentInfoEncoder.write(isYKey ? 1L : 0L);
    }

    @Override
    public void writeTypeRef(long info) {
        typeRefEncoder.write(info);
    }

    @Override
    public void writeLen(long len) {
        lengthEncoder.write(len);
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
        Long clock = this.keyMap.get(key);
        if(null == clock){
            this.keyClockEncoder.write(this.keyClock++);
            this.stringEncoder.write(key);
        }else{
            this.keyClockEncoder.write(clock);
        }
    }

    @Override
    public void writeJson(String json) {
        StreamEncodingExtensions.writeVarString(this.restEncoder,json);
    }
}
