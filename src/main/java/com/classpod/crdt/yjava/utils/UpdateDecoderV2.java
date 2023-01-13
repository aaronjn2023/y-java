package com.classpod.crdt.yjava.utils;

import cn.hutool.json.JSONUtil;
import com.classpod.crdt.y.lib.StreamDecodingExtensions;
import com.classpod.crdt.y.lib.decoding.IntDiffOptRleDecoder;
import com.classpod.crdt.y.lib.decoding.RleDecoder;
import com.classpod.crdt.y.lib.decoding.StringDecoder;
import com.classpod.crdt.y.lib.decoding.UintOptRleDecoder;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/9/16 1:42 PM
 **/
public class UpdateDecoderV2 extends DsDecoderV2 implements IUpdateDecoder{

    private List<String> keys;
    private IntDiffOptRleDecoder keyClockDecoder;
    private UintOptRleDecoder clientDecoder;
    private IntDiffOptRleDecoder leftClockDecoder;
    private IntDiffOptRleDecoder rightClockDecoder;
    private RleDecoder infoDecoder;
    private StringDecoder stringDecoder;
    private RleDecoder parentInfoDecoder;
    private UintOptRleDecoder typeRefDecoder;
    private UintOptRleDecoder lengthDecoder;

    public UpdateDecoderV2(ByteArrayInputStream input) {
        super(input);
        keys = new ArrayList<>();
        // read feature flag currently unused
        StreamDecodingExtensions.readVarUInt(input);
        keyClockDecoder = new IntDiffOptRleDecoder(StreamDecodingExtensions.readVarUint8Array(input));
        clientDecoder = new UintOptRleDecoder(StreamDecodingExtensions.readVarUint8Array(input));
        leftClockDecoder = new IntDiffOptRleDecoder(StreamDecodingExtensions.readVarUint8Array(input));
        rightClockDecoder = new IntDiffOptRleDecoder(StreamDecodingExtensions.readVarUint8Array(input));
        infoDecoder = new RleDecoder(StreamDecodingExtensions.readVarUint8Array(input),input);
        stringDecoder = new StringDecoder(StreamDecodingExtensions.readVarUint8Array(input));
        parentInfoDecoder = new RleDecoder(StreamDecodingExtensions.readVarUint8Array(input),input);
        typeRefDecoder = new UintOptRleDecoder(StreamDecodingExtensions.readVarUint8Array(input));
        lengthDecoder = new UintOptRleDecoder(StreamDecodingExtensions.readVarUint8Array(input));
    }

    @Override
    public ByteArrayInputStream restDecoder() {
        return restDecoder;
    }

    @Override
    public Id readLeftID() {
        return new Id(Long.parseLong(String.valueOf(clientDecoder.read())),leftClockDecoder.reads());
    }

    @Override
    public Id readRightID() {
        return new Id(Long.parseLong(String.valueOf(clientDecoder.read())),rightClockDecoder.reads());
    }

    @Override
    public long readClient() {
        return Long.parseLong(String.valueOf(clientDecoder.reads()));
    }

    @Override
    public int readInfo() {
        return infoDecoder.reads().intValue();
    }

    @Override
    public String readString() {
        return stringDecoder.reads();
    }

    @Override
    public boolean readParentInfo() {
        return parentInfoDecoder.reads() == 1;
    }

    @Override
    public long readTypeRef() {
        return typeRefDecoder.reads();
    }

    @Override
    public long readLen() {
        return lengthDecoder.reads();
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
        long keyClock = keyClockDecoder.reads();
        if(keyClock < keys.size()){
            return this.keys.get((int)keyClock);
        }else{
            String key = stringDecoder.reads();
            keys.add(key);
            return key;
        }
    }

    @Override
    public Object readJson() {
        return JSONUtil.parseObj(StreamDecodingExtensions.readAny(this.restDecoder));
    }
}
