package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.Bit;
import com.classpod.crdt.y.lib.StreamDecodingExtensions;
import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import com.classpod.crdt.yjava.dto.CurrAbstructWriteDto;
import com.classpod.crdt.yjava.dto.LazyStructWriterDto;
import com.classpod.crdt.yjava.structs.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/18 7:52 PM
 **/
public class Updates {

    public final static String PREFIX_CACHE_STRUCT = "prefix_cache_struct_";

    public static byte[] mergeUpdates(List<byte[]> updates,IUpdateDecoder decoder,IUpdateEncoder encoder){
        try {
            if(null == decoder){
                decoder = new UpdateDecoderV1(new ByteArrayInputStream(updates.get(0)));
            }
            if(null == encoder){
                encoder = new UpdateEncoderV1();
            }
            return mergeUpdatesV2(updates,decoder,encoder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] mergeUpdatesV2(List<byte[]> updates, IUpdateDecoder decoder, IUpdateEncoder encoder) throws IOException {
        if (updates.size() == 1) {
            return updates.get(0);
        }

        List<IUpdateDecoder> updateDecoders = new ArrayList<>();
        for (byte[] bytes : updates) {
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            if(null == decoder){
                UpdateDecoderV2 update2 = new UpdateDecoderV2(input);
                updateDecoders.add(update2);
            }else{
                UpdateDecoderV1 update1 = new UpdateDecoderV1(input);
                updateDecoders.add(update1);
            }

        }
        List<AbstractStruct> lazyStructDecoders = new ArrayList<>();
        for(IUpdateDecoder decoder1 : updateDecoders){
            lazyStructDecoders.add(new LazyStructReader(decoder1,true).gen);
        }

        CurrAbstructWriteDto currWrite = new CurrAbstructWriteDto();
        IUpdateEncoder updateEncoder;
        if(null == encoder){
            updateEncoder = new UpdateEncoderV2();
        }else{
            updateEncoder = encoder;
        }

        LazyStructWriter lazyStructEncoder = new LazyStructWriter(updateEncoder);
        while(true){
            // 按client倒序
            Collections.sort(lazyStructDecoders, new Comparator<AbstractStruct>() {
                @Override
                public int compare(AbstractStruct struct1,AbstractStruct struct2){
                    return struct2.getId().getClient().compareTo(struct1.getId().getClient());
                }
            });
            if(lazyStructDecoders.size() == 0){
                break;
            }
            AbstractStruct currDecoder = lazyStructDecoders.get(0);
            long firstClient = currDecoder.getId().getClient();
            if(null != currWrite){
                AbstractStruct curr = currDecoder;
                Boolean iterated = false;
                if(null != curr && curr.getId().getClock() + curr.getLength() <= currWrite.getStruct().getId().getClock() + currWrite.getStruct().getLength()
                        && curr.getId().getClient() >= currWrite.getStruct().getId().getClient()){
                    curr = Updates.LazyStructReader.next();
                    iterated = true;
                }

                if(null == curr || curr.getId().getClient() != firstClient || iterated
                        && curr.getId().getClock() > currWrite.getStruct().getId().getClock() + currWrite.getStruct().getLength()){
                    continue;
                }
                if(firstClient != currWrite.getStruct().getId().getClient()){
                    writeStructToLazyStructWriter(lazyStructEncoder,currWrite.getStruct(), currWrite.getOffset());
                    currWrite = new CurrAbstructWriteDto(curr,0);
                    Updates.LazyStructReader.next();
                }else{
                    if(currWrite.getStruct().getId().getClock() + currWrite.getStruct().getLength() < curr.getId().getClock()){
                        if(currWrite.getStruct().getClass() == Skip.class){
                            currWrite.getStruct().setLength(curr.getId().getClock().intValue() + curr.getLength() - currWrite.getStruct().getId().getClock().intValue());
                        }else{
                            writeStructToLazyStructWriter(lazyStructEncoder,currWrite.getStruct(), currWrite.getOffset());
                            int diff = curr.getId().getClock().intValue() - currWrite.getStruct().getId().getClock().intValue() - currWrite.getStruct().getLength();
                            Skip struct = new Skip(new Id(firstClient,currWrite.getStruct().getId().getClock() + currWrite.getStruct().getLength()),diff);
                            currWrite.setStruct(struct);
                            currWrite.setOffset(0);
                        }
                    }else{
                        int diff = currWrite.getStruct().getId().getClock().intValue() + currWrite.getStruct().getLength() - curr.getId().getClock().intValue();
                        if(diff > 0){
                            if(currWrite.getStruct().getClass() == Skip.class){
                                currWrite.getStruct().setLength(currWrite.getStruct().getLength() - diff); ;
                            }else{
                                curr = sliceStruct(curr,diff);
                            }
                        }
                        if(!currWrite.getStruct().mergeWith(curr)){
                            writeStructToLazyStructWriter(lazyStructEncoder,currWrite.getStruct(), currWrite.getOffset());
                            currWrite = new CurrAbstructWriteDto(curr,0);
                            Updates.LazyStructReader.next();
                        }
                    }
                }
            }else{
                currWrite = new CurrAbstructWriteDto(currDecoder,0);
                Updates.LazyStructReader.next();
            }
            for(AbstractStruct next = Updates.LazyStructReader.next();null != next && next.getId().getClient() == firstClient
                    && next.getId().getClock() == currWrite.getStruct().getId().getClock() + (long)currWrite.getStruct().getLength()
                    && next.getClass() != Skip.class;next = Updates.LazyStructReader.next()){
                writeStructToLazyStructWriter(lazyStructEncoder,currWrite.getStruct(), currWrite.getOffset());
                currWrite = new CurrAbstructWriteDto(next,0);
            }
        }
        if(null != currWrite){
            writeStructToLazyStructWriter(lazyStructEncoder, currWrite.getStruct(), currWrite.getOffset());
            currWrite = null;
        }
        finishLazyStructWriting(lazyStructEncoder);
        List<DeleteSet> deleteSetList = new ArrayList<>();
        for(IUpdateDecoder decoder1 : updateDecoders){
            deleteSetList.add(DeleteSet.readDeleteSet(decoder1));
        }
        DeleteSet ds = DeleteSet.mergeDeleteSets(deleteSetList);
        DeleteSet.writeDeleteSet(updateEncoder,ds);
        return updateEncoder.toUint8Array();
    }

    public static void finishLazyStructWriting(LazyStructWriter lazyWriter){
        flushLazyStructWriter(lazyWriter);
        ByteArrayOutputStream restEncoder = lazyWriter.encoder.restEncoder();
        StreamEncodingExtensions.writeVarUint(restEncoder,lazyWriter.clientsStructs.size());
        for(int i=0;i<lazyWriter.clientsStructs.size();i++){
            LazyStructWriterDto partStructs = lazyWriter.clientsStructs.get(i);
            StreamEncodingExtensions.writeVarUint(restEncoder,partStructs.getWritten());
            StreamEncodingExtensions.writeVarUint8Array(restEncoder,partStructs.getRestEncoder());
        }
    }

    public static AbstractStruct sliceStruct(AbstractStruct left,long diff){
        if(left.getClass() == Gc.class){
            return new Gc(new Id(left.getId().getClient(),left.getId().getClock().intValue() + diff),left.getLength() - (int)diff);
        }else if(left.getClass() == Skip.class){
            return new Skip(new Id(left.getId().getClient(),left.getId().getClock().intValue() + diff),left.getLength() - (int)diff);
        }else{
            Item leftItem = (Item)left;
            return new Item(new Id(left.getId().getClient(),left.getId().getClock().intValue() + diff),
                    null,
                    new Id(left.getId().getClient(),left.getId().getClock().intValue() + diff -1),
                    null,
                    leftItem.rightOrigin,
                    leftItem.parent,
                    leftItem.parentSub,
                    leftItem.content.splice((int)diff));
        }
    }

    public static void writeStructToLazyStructWriter(LazyStructWriter lazyWriter,AbstractStruct struct,int offset){
        if(lazyWriter.written > 0 && !lazyWriter.currClient.equals(struct.getId().getClient())){
            flushLazyStructWriter(lazyWriter);
        }
        if(lazyWriter.written == 0){
            lazyWriter.currClient = struct.getId().getClient();
            lazyWriter.encoder.writeClient(struct.getId().getClient());
            StreamEncodingExtensions.writeVarUint(lazyWriter.encoder.restEncoder(),struct.getId().getClock() + offset);
        }
        try {
            struct.write(lazyWriter.encoder,offset);
        } catch (IOException e) {
            e.printStackTrace();
        }
        lazyWriter.written++;
    }

    public static void flushLazyStructWriter(LazyStructWriter lazyWriter){
        if(lazyWriter.written > 0){
            LazyStructWriterDto lazyStructWriterDto = new LazyStructWriterDto();
            lazyStructWriterDto.setWritten(lazyWriter.written);
            lazyStructWriterDto.setRestEncoder(StreamEncodingExtensions.toUint8Array(lazyWriter.encoder.restEncoder()));
            lazyWriter.clientsStructs.add(lazyStructWriterDto);
            try {
                lazyWriter.encoder.restEncoder().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            lazyWriter.written = 0;
        }
    }

    public static class LazyStructReader {
        public AbstractStruct gen;
        private Boolean filterSkips;
        private static IUpdateDecoder updateDecoder;
        public LazyStructReader(IUpdateDecoder decoder, Boolean filterSkips) {
            this.gen = lazyStructReaderGenerator(decoder);
            this.filterSkips = filterSkips;
            updateDecoder = decoder;
        }

        public static AbstractStruct next(){
            return lazyStructReaderGenerator(updateDecoder);
        }
    }

    public static class LazyStructWriter{
        Long currClient;
        Long startClock;
        Integer written;
        IUpdateEncoder encoder;
        List<LazyStructWriterDto> clientsStructs;
        public LazyStructWriter(IUpdateEncoder encoder){
            this.currClient = 0L;
            this.startClock = 0L;
            this.written = 0;
            this.encoder = encoder;
            this.clientsStructs = new ArrayList<>();
        }
    }

    public static AbstractStruct lazyStructReaderGenerator(IUpdateDecoder decoder) {
        long numberOfStateUpdates = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
        for (int i = 0; i < numberOfStateUpdates; i++) {
            long numberOfStructs = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            long client = decoder.readClient();
            long clock = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            for (int j = 0; j < numberOfStructs; j++) {
                int info = decoder.readInfo();
                if (info == 10) {// skip
                    long len = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
                    clock += len;
                    ThreadLocalUtil.setCache(PREFIX_CACHE_STRUCT + client,clock);
                    return new Skip(new Id(client, clock), (int)len);
                } else if ((Bit.Bit5 & info) != 0) {
                    Boolean cantCopyParentInfo = (info & (Bit.Bit7 | Bit.Bit8)) == 0;
                    Id leftOrigin = (info & Bit.Bit8) == Bit.Bit8 ? decoder.readLeftID() : null;
                    Id rightOrigin = (info & Bit.Bit7) == Bit.Bit7 ? decoder.readRightID() : null;
                    Item str = null;
                    try {
                        Long cacheStruct = null == ThreadLocalUtil.getCache(PREFIX_CACHE_STRUCT + client) ? 0L : Long.parseLong(String.valueOf(ThreadLocalUtil.getCache(PREFIX_CACHE_STRUCT + client))) ;
                        if(null != cacheStruct){
                            clock += cacheStruct;
                        }
                        str = new Item(new Id(client, clock), null, leftOrigin, null, rightOrigin,
                                cantCopyParentInfo ? (decoder.readParentInfo() ? decoder.readString() : decoder.readLeftID()) : null,
                                cantCopyParentInfo && (info & Bit.Bit6) == Bit.Bit6 ? decoder.readString() : null,
                                Encoding.readItemContent(decoder, info));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    clock += str.getLength();
                    return str;
                } else {
                    long len = decoder.readLen();
                    Long cacheStruct = null == ThreadLocalUtil.getCache(PREFIX_CACHE_STRUCT + client) ? 0L : Long.parseLong(String.valueOf(ThreadLocalUtil.getCache(PREFIX_CACHE_STRUCT + client))) ;
                    if(null != cacheStruct){
                        clock += cacheStruct;
                    }
                    Gc gc = null;
                    gc = new Gc(new Id(client, clock), (int)len);
                    clock += len;
                    return gc;
                }
            }
        }
        return null;
    }

    public static byte[] diffUpdateV2(byte[] update,byte[] sv,IUpdateDecoder decoder,IUpdateEncoder encoder) throws IOException {
        Map<Long,Long> state = Encoding.decodeStateVector(sv);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(update);
        if(null == decoder){
            decoder = new UpdateDecoderV2(inputStream);
        }
        if(null == encoder){
            encoder = new UpdateEncoderV2();
        }
        LazyStructWriter lazyStructWriter = new LazyStructWriter(encoder);
        LazyStructReader reader = new LazyStructReader(decoder,false);
        while(null != reader.gen){
            AbstractStruct curr = reader.gen;
            Long currClient = curr.getId().getClient();
            Long svClock = null == state.get(currClient) ? 0L : state.get(currClient);
            if(curr.getClass() == Skip.class){
                Updates.LazyStructReader.next();
                continue;
            }
            if(curr.getId().getClock() + curr.getLength() > svClock){
                writeStructToLazyStructWriter(lazyStructWriter,curr,Math.max(svClock.intValue() - curr.getId().getClock().intValue(),0));
                Updates.LazyStructReader.next();
                while(null != reader.gen && reader.gen.getId().getClient().longValue() == currClient.longValue()){
                    writeStructToLazyStructWriter(lazyStructWriter,reader.gen,0);
                    Updates.LazyStructReader.next();
                }
            }else{
                while(null != reader.gen && reader.gen.getId().getClient().longValue() == currClient.longValue()
                        && reader.gen.getId().getClock() + reader.gen.getLength() <= svClock){
                    Updates.LazyStructReader.next();
                }
            }
        }
        finishLazyStructWriting(lazyStructWriter);
        DeleteSet ds = DeleteSet.readDeleteSet(decoder);
        DeleteSet.writeDeleteSet(encoder,ds);
        return encoder.toUint8Array();
    }

    public static byte[] convertUpdateFormatV2ToV1(byte[] update){
        try {
            return convertUpdateFormat(update,new UpdateDecoderV2(new ByteArrayInputStream(update)),new UpdateEncoderV1());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] convertUpdateFormat(byte[] update,IUpdateDecoder decoder,IUpdateEncoder encoder) throws IOException {
        IUpdateDecoder updateDecoder = null;
        if(null != decoder){
            updateDecoder = new UpdateDecoderV2(new ByteArrayInputStream(update));
        }
        LazyStructReader lazyDecoder = new LazyStructReader(updateDecoder,false);
        IUpdateEncoder updateEncoder = null;
        if(null != encoder){
            updateEncoder = encoder;
        }
        LazyStructWriter lazyWriter = new LazyStructWriter(updateEncoder);
        for(AbstractStruct curr = lazyDecoder.gen;null != curr;curr = Updates.LazyStructReader.next()){
            writeStructToLazyStructWriter(lazyWriter,curr,0);
        }
        finishLazyStructWriting(lazyWriter);
        DeleteSet ds = DeleteSet.readDeleteSet(updateDecoder);
        DeleteSet.writeDeleteSet(updateEncoder,ds);
        return updateEncoder.toUint8Array();
    }
}
