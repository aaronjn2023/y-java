package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.Bit;
import com.classpod.crdt.y.lib.StreamDecodingExtensions;
import com.classpod.crdt.yjava.structs.AbstractStruct;
import com.classpod.crdt.yjava.structs.Gc;
import com.classpod.crdt.yjava.structs.Item;
import com.classpod.crdt.yjava.structs.Skip;

import java.util.Iterator;

/**
 * AbstractStruct 迭代生成器
 *
 * @Author jiquanwei
 * @Date 2022/12/20 22:47 PM
 **/
public class StructReaderGenerator implements Iterable<AbstractStruct> {

    private int _current;
    private int _count;

    private long numOfStateUpdates;
    private long numberOfStructs;

    private IUpdateDecoder decoder;
    private long _client;
    private long _clock;
    private long _len;

    public StructReaderGenerator(IUpdateDecoder decoder) {
        this._current = 0;
        this._len = 0;
        this.numOfStateUpdates = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
        this.numberOfStructs = -1;
        this.decoder = decoder;
    }


    @Override
    public Iterator<AbstractStruct> iterator() {
        return new Iterator<AbstractStruct>() {

            @Override
            public boolean hasNext() {
                boolean flag;
                if(numberOfStructs == -1){
                    flag = _current < numOfStateUpdates;
                }else {
                    flag = _current < numberOfStructs;
                }
                if(flag && numberOfStructs == -1){
                    numberOfStructs = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
                    _client = decoder.readClient();
                    _clock = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
                }
                return flag;
            }

            @Override
            public AbstractStruct next() {
                for (int i = _current; i < numOfStateUpdates; i++) {
                    for (i = _current; i < numberOfStructs; i++) {
                        System.out.println("[loop] "+i);
                        if(i != 0){
                            _clock += _len;
                        }
                        if((i+1) == numberOfStructs){
                            System.out.println("init numberOfStructs");
                            numberOfStructs = -1;
                        }
                        int info = decoder.readInfo();
                        if (info == 10) {// skip
                            long len = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
                            _len = len;
                            _count ++;
                            _current ++;
                            System.out.println("[loop count] :"+_count);
                            return new Skip(new Id(_client, _clock), (int)len);
                        } else if ((Bit.Bit5 & info) != 0) {
                            Boolean cantCopyParentInfo = (info & (Bit.Bit7 | Bit.Bit8)) == 0;
                            Id leftOrigin = (info & Bit.Bit8) == Bit.Bit8 ? decoder.readLeftID() : null;
                            Id rightOrigin = (info & Bit.Bit7) == Bit.Bit7 ? decoder.readRightID() : null;
                            Item str = null;
                            try {
                                str = new Item(new Id(_client, _clock), null, leftOrigin, null, rightOrigin,
                                        cantCopyParentInfo ? (decoder.readParentInfo() ? decoder.readString() : decoder.readLeftID()) : null,
                                        cantCopyParentInfo && (info & Bit.Bit6) == Bit.Bit6 ? decoder.readString() : null,
                                        Encoding.readItemContent(decoder, info));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            _len = str.getLength();
                            _count ++;
                            _current ++;
                            System.out.println("[loop count] :"+_count);
                            return str;
                        } else {
                            long len = decoder.readLen();
                            Gc gc = new Gc(new Id(_client, _clock), (int)len);
                            _len = len;
                            _count ++;
                            _current ++;
                            System.out.println("[loop count] :"+_count);
                            return gc;
                        }
                    }
                    System.out.println("[loop count] :"+_count);
                    _count ++;
                    _current ++;
                }
                return null;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}