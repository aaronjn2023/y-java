package com.classpod.crdt.yjava.utils;

import cn.hutool.core.lang.Assert;
import com.classpod.crdt.y.lib.*;
import com.classpod.crdt.yjava.structs.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/9/22 4:16 PM
 **/
public class EncodingUtils {
    public static AbstractContent readItemContent(IUpdateDecoder decoder, int info) throws IOException {
        switch(info & Bits.Bits5){
            case 0: // gc
                throw new RuntimeException("gc is not itemContent");
            case 1:
                return ContentDeleted.readContentDeleted(decoder);
            case 2:
                return ContentJson.readContentJson(decoder);
            case 3:
                return ContentBinary.readContentAny(decoder);
            case 4:
                return ContentString.readContentString(decoder);
            case 5:
                return ContentEmbed.read(decoder);
            case 6:
                return ContentFormat.read(decoder);
            case 7:
                try {
                    return ContentType.readContentType(decoder);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            case 8:
                return ContentAny.readContentAny(decoder);
            case 9:
                return ContentDoc.readContentDoc(decoder);
            default:
                throw new RuntimeException("content type not recognized:" + info);
        }
    }

    public static void writeStructs(IUpdateEncoder encoder,List<AbstractStruct> structs,long client,long clock) throws Exception {
        int startNewStructs = StructStore.findIndexSS(structs,clock);
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),structs.size() - startNewStructs);
        encoder.writeClient(client);
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),clock);
        AbstractStruct firstStruct = structs.get(startNewStructs);
        firstStruct.write(encoder,(int)(clock - firstStruct.getId().getClock()));
        for(int i= startNewStructs +1;i<structs.size();i++){
            structs.get(i).write(encoder,0);
        }
    }

    public static void writeClientsStructs(IUpdateEncoder encoder,StructStore store,Map<Long,Long> _sm){
        Map<Long,Long> sm = new HashMap<>();
        for(Map.Entry<Long,Long> entry : _sm.entrySet()){
            long client = entry.getKey();
            long clock = entry.getValue();
            if(StructStore.getState(store,client) > clock){
                sm.put(client,clock);
            }
        }
        Map<Long,Long> stateVectorMap = StructStore.getStateVector(store);
        for(Map.Entry<Long,Long> entry : stateVectorMap.entrySet()){
            long client = entry.getKey();
            if(!sm.containsKey(client)){
                sm.put(client,0L);
            }
        }
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),sm.size());
        // 倒序
        List<Long> sortedClients = sm.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        sortedClients.forEach(client ->{
            try {
                writeStructs(encoder,store.getClients().get(client),client,sm.get(client));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static Map<Long, List<AbstractStruct>> readClientStructRefs(IUpdateDecoder decoder,YDoc doc) throws IOException {
        Map<Long,List<AbstractStruct>> clientRefs = new HashMap<>();
        long numOfStateUpdates = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
        for(int i=0;i<numOfStateUpdates;i++){
            long numberOfStructs = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            Assert.isTrue(numberOfStructs >= 0);
            List<AbstractStruct> refs = new ArrayList<>((int)numberOfStructs);
            long client = decoder.readClient();
            long clock = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            clientRefs.put(client,refs);
            for(int j=0;j<numberOfStructs;j++){
                int info = decoder.readInfo();
                if((Bits.Bits5 & info) != 0){
                    Id leftOrigin = (info & Bit.Bit8) == Bit.Bit8 ? decoder.readLeftID() : null;
                    Id rightOrigin = (info & Bit.Bit7) == Bit.Bit7 ? decoder.readRightID() : null;
                    Boolean cantCopyParentInfo = (info & (Bit.Bit7 | Bit.Bit8)) == 0;
                    Boolean hasParentYKey = cantCopyParentInfo ? decoder.readParentInfo() : false;
                    String parentYKey = cantCopyParentInfo && hasParentYKey ? decoder.readString() : null;
                    // TODO (parentYKey != null ? (object)doc.get(parentYKey)
                    Item str = new Item(new Id(client,clock),null,leftOrigin,null,rightOrigin,
                            cantCopyParentInfo && !hasParentYKey ? decoder.readLeftID() : (parentYKey != null ? null : null),
                            cantCopyParentInfo && (info & Bit.Bit6) == Bit.Bit6 ? decoder.readString() : null,
                            readItemContent(decoder, (byte)info));
                    refs.add(str);
                    clock += str.getLength();
                }else{
                    long length = decoder.readLen();
                    refs.add(new Gc(new Id(client,clock),(int)length));
                    clock += length;
                }
            }
        }
        return clientRefs;
    }

    public static void writeStateVector(IUpdateEncoder encoder,Map<Long,Long> sv){
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),sv.size());
        for(Map.Entry<Long,Long> entry : sv.entrySet()){
            long client = entry.getKey();
            long clock = entry.getValue();
            StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),client);
            StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),clock);
        }
    }

    public static Map<Long,Long> readStateVector(IUpdateDecoder decoder){
        long ssLength = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
        Map<Long,Long> ss = new HashMap<>((int)ssLength);
        for(Map.Entry<Long,Long> entry : ss.entrySet()){
            long client = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            long clock = client;
            ss.put(client,clock);
        }
        return ss;
    }

    public static Map<Long,Long> decodeStateVector(ByteArrayInputStream input){
//        return readStateVector(new UpdateDecoderV2(input));
//        return readStateVector(new DsDecoderV2(input));
        return null;
    }
}
