package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.Bit;
import com.classpod.crdt.y.lib.StreamDecodingExtensions;
import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import com.classpod.crdt.yjava.dto.CurrAbstructWriteDto;
import com.classpod.crdt.yjava.dto.LazyStructWriterDto;
import com.classpod.crdt.yjava.structs.AbstractStruct;
import com.classpod.crdt.yjava.structs.Gc;
import com.classpod.crdt.yjava.structs.Item;
import com.classpod.crdt.yjava.structs.Skip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/18 7:52 PM
 **/
public class Updates2 {

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


    /**
     *
     * 功能描述 :  mergeUpdatesV2  逻辑已确认
     *
     * @author:  lvwei
     * @param:  [java.util.List<byte[]>, com.classpod.crdt.yjava.utils.IUpdateDecoder, com.classpod.crdt.yjava.utils.IUpdateEncoder]
     * @return:  byte[]
     * @date:  2022/12/20 23:59
     */
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
        List<LazyStructReader2> lazyStructDecoders = new ArrayList<>();
        for(IUpdateDecoder decoder1 : updateDecoders){
            lazyStructDecoders.add(new LazyStructReader2(decoder1,true));
        }

//        CurrAbstructWriteDto currWrite = new CurrAbstructWriteDto();
        CurrAbstructWriteDto currWrite = null;

        IUpdateEncoder updateEncoder;
        if(null == encoder){
            updateEncoder = new UpdateEncoderV2();
        }else{
            updateEncoder = encoder;
        }

        LazyStructWriter lazyStructEncoder = new LazyStructWriter(updateEncoder);
        while(true){
            lazyStructDecoders = lazyStructDecoders.stream().filter(dec -> dec.curr !=null).collect(Collectors.toList());
            // 按client倒序
            Collections.sort(lazyStructDecoders, new Comparator<LazyStructReader2>() {
                @Override
                public int compare(LazyStructReader2 struct1,LazyStructReader2 struct2){
                    return struct2.curr.getId().getClient().compareTo(struct1.curr.getId().getClient());
                }
            });
            if(lazyStructDecoders.size() == 0){
                break;
            }
            LazyStructReader2 currDecoder = lazyStructDecoders.get(0);
            long firstClient = currDecoder.curr.getId().getClient();
            if(null != currWrite){
                AbstractStruct curr = currDecoder.curr;
                Boolean iterated = false;
                while(null != curr
                        && curr.getId().getClock() + curr.getLength() <= currWrite.getStruct().getId().getClock() + currWrite.getStruct().getLength()
                        && curr.getId().getClient() >= currWrite.getStruct().getId().getClient()){
                    curr = currDecoder.next();
                    iterated = true;
                }

                if(null == curr
                        || curr.getId().getClient() != firstClient
                        || (iterated && curr.getId().getClock() > currWrite.getStruct().getId().getClock() + currWrite.getStruct().getLength())){
                    continue;
                }
                if(firstClient != currWrite.getStruct().getId().getClient()){
                    writeStructToLazyStructWriter(lazyStructEncoder,currWrite.getStruct(), currWrite.getOffset());
                    currWrite = new CurrAbstructWriteDto(curr,0);
                    currDecoder.next();
                }else{
                    if((currWrite.getStruct().getId().getClock() + currWrite.getStruct().getLength()) < curr.getId().getClock()){
                        if(currWrite.getStruct().getClass() == Skip.class){
                            currWrite.getStruct().setLength(Integer.parseInt(
                                    (curr.getId().getClock() + curr.getLength() - currWrite.getStruct().getId().getClock())+""));
                        }else{
                            writeStructToLazyStructWriter(lazyStructEncoder,currWrite.getStruct(), currWrite.getOffset());
                            int diff = (int)(curr.getId().getClock()- currWrite.getStruct().getId().getClock()- currWrite.getStruct().getLength());
                            Skip struct = new Skip(new Id(firstClient,currWrite.getStruct().getId().getClock() + currWrite.getStruct().getLength()),diff);
                            currWrite = new CurrAbstructWriteDto(struct,0);
                        }
                    }else{
                        int diff = (int)(currWrite.getStruct().getId().getClock() + currWrite.getStruct().getLength() - curr.getId().getClock());
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
                            currDecoder.next();
                        }
                    }
                }
            }else{
                currWrite = new CurrAbstructWriteDto(currDecoder.curr,0);
                currDecoder.next();
            }
            for(AbstractStruct next = currDecoder.curr;
                null != next
                        && next.getId().getClient() == firstClient
                        && next.getId().getClock() == currWrite.getStruct().getId().getClock() + currWrite.getStruct().getLength()
                        && next.getClass() != Skip.class;
                next = currDecoder.next()){
                writeStructToLazyStructWriter(lazyStructEncoder,currWrite.getStruct(), currWrite.getOffset());
                currWrite = new CurrAbstructWriteDto(next,0);
            }
        }
        if(null != currWrite){
            writeStructToLazyStructWriter(lazyStructEncoder, currWrite.getStruct(), currWrite.getOffset());
            currWrite = null;
        }
        finishLazyStructWriting(lazyStructEncoder);
        List<DeleteSet> deleteSetList = updateDecoders.stream()
                .map(e ->  DeleteSet.readDeleteSet(e)).collect(Collectors.toList());
        // List<DeleteSet> deleteSetList = new ArrayList<>();
        // for(IUpdateDecoder decoder1 : updateDecoders){
        //     deleteSetList.add(DeleteSet.readDeleteSet(decoder1));
        // }
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
            return new Gc(new Id(left.getId().getClient(),left.getId().getClock() + diff), (int)(left.getLength() - diff));
        }else if(left.getClass() == Skip.class){
            return new Skip(new Id(left.getId().getClient(),left.getId().getClock() + diff),(int)(left.getLength() - diff));
        }else{
            Item leftItem = (Item)left;
            return new Item(new Id(left.getId().getClient(),left.getId().getClock() + diff),
                    null,
                    new Id(left.getId().getClient(),left.getId().getClock() + diff -1),
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


    public static class LazyStructReader2 {
        private StructReaderGenerator gen;
        public AbstractStruct curr;
        private Boolean filterSkips;
        public LazyStructReader2(IUpdateDecoder decoder, Boolean filterSkips) {
            this.gen = new StructReaderGenerator(decoder);
            this.filterSkips = filterSkips;
            this.next();
        }
        public AbstractStruct next(){
            // ignore "Skip" structs
            do {
                if(gen.iterator().hasNext()){
                    this.curr = gen.iterator().next();
                }else{
                    this.curr = null;
                    break;
                }
            } while (this.filterSkips
                    && this.curr != null
                    && this.curr.getClass() == Skip.class);
            return this.curr;
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
        LazyStructReader2 reader = new LazyStructReader2(decoder,false);
        while(null != reader.curr){
            AbstractStruct curr = reader.curr;
            Long currClient = curr.getId().getClient();
            Long svClock = null == state.get(currClient) ? 0L : state.get(currClient);
            if(reader.curr.getClass() == Skip.class){
                reader.next();
                continue;
            }
            if(curr.getId().getClock() + curr.getLength() > svClock){
                writeStructToLazyStructWriter(lazyStructWriter,curr,Math.max(svClock.intValue() - curr.getId().getClock().intValue(),0));
                reader.next();
                while(null != reader.curr
                        && Objects.equals(reader.curr.getId().getClient(), currClient)){
                    writeStructToLazyStructWriter(lazyStructWriter,reader.curr,0);
                    reader.next();
                }
            }else{
                while(null != reader.curr
                        && Objects.equals(reader.curr.getId().getClient(), currClient)
                        && reader.curr.getId().getClock() + reader.curr.getLength() <= svClock){
                    reader.next();
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
        LazyStructReader2 lazyDecoder = new LazyStructReader2(updateDecoder,false);
        IUpdateEncoder updateEncoder = null;
        if(null != encoder){
            updateEncoder = encoder;
        }
        LazyStructWriter lazyWriter = new LazyStructWriter(updateEncoder);
        for(AbstractStruct curr = lazyDecoder.curr;
            null != curr;
            curr = lazyDecoder.next()){
            writeStructToLazyStructWriter(lazyWriter,curr,0);
        }
        finishLazyStructWriting(lazyWriter);
        DeleteSet ds = DeleteSet.readDeleteSet(updateDecoder);
        DeleteSet.writeDeleteSet(updateEncoder,ds);
        return updateEncoder.toUint8Array();
    }
}
