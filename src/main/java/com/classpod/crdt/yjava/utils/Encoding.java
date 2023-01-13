package com.classpod.crdt.yjava.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.classpod.crdt.y.lib.Bit;
import com.classpod.crdt.y.lib.Bits;
import com.classpod.crdt.y.lib.StreamDecodingExtensions;
import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import com.classpod.crdt.yjava.dto.PendingStructsDto;
import com.classpod.crdt.yjava.structs.*;
import com.classpod.crdt.yjava.types.AbstractType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/11 7:09 PM
 **/
public class Encoding {
    final Logger logger = LoggerFactory.getLogger(Encoding.class);
    private static AtomicInteger keyCount = new AtomicInteger(0);

    public static void applyUpdate(YDoc doc,byte[] update,Object transactionOrigin){
        applyUpdateV2(doc,update,transactionOrigin, UpdateDecoderV1.class);
    }

    public static void applyUpdateV2(YDoc doc, byte[] update,Object transactionOrigin,Class cls){
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(update);
            if(null == cls || cls == UpdateDecoderV1.class){
                readUpdateV2(inputStream,doc,transactionOrigin,new UpdateDecoderV1(inputStream));
            }else if(UpdateDecoderV2.class == cls){
                readUpdateV2(inputStream,doc,transactionOrigin,new UpdateDecoderV2(inputStream));
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public static void readUpdateV2(ByteArrayInputStream input,YDoc doc,Object transactionOrigin,IUpdateDecoder decoder) throws NoSuchMethodException {
        Encoding encoding = new Encoding();
        Method method= encoding.getClass().getMethod("forceTransaction",YDoc.class,Transaction.class,IUpdateDecoder.class);
        Transaction.transactYjs(doc,encoding.getClass(),method,transactionOrigin,false,decoder);
    }

    public void forceTransaction(YDoc doc,Transaction transaction,IUpdateDecoder decoder) throws Exception {
        transaction.local = false;
        Boolean retry = false;
        StructStore store = doc.getStore();
        Map<Long,StructRefs> ss = readClientsStructRefs2(decoder,doc);
        PendingStructsDto restStructs = integrateStructs2(transaction,store,ss);//store xinzeng
        PendingStructsDto pending = store.pendingStructs;
        if(null != pending){
            for(Map.Entry<Long,Long> entry : pending.getMissingSV().entrySet()){
                if(entry.getValue() < getState(store,entry.getKey())){
                    retry = true;
                    break;
                }
            }
            if(null != restStructs){
                for(Map.Entry<Long,Long> entry : restStructs.getMissingSV().entrySet()){
                    Long mclock = pending.getMissingSV().get(entry.getKey());
                    if(null == mclock || mclock > entry.getValue()){
                        pending.getMissingSV().put(entry.getKey(),entry.getValue());
                    }
                }

                List<byte[]> list = new ArrayList<>();
                list.add(pending.getUint8Array());
                list.add(restStructs.getUint8Array());
                byte[] unit8Array = Updates2.mergeUpdatesV2(list,null,null);
                pending.setUint8Array(unit8Array);
            }
        }else{
            store.pendingStructs = restStructs;
        }
        byte[] dsRest = DeleteSet.readAndApplyDeleteSet(decoder,transaction,store);
        if(null != store.pendingDs){
            ByteArrayInputStream inputStream = new ByteArrayInputStream(store.pendingDs);
            UpdateDecoderV2 pendingDSUpdate = new UpdateDecoderV2(inputStream);
            StreamDecodingExtensions.readVarUInt(pendingDSUpdate.restDecoder);
            byte[] dsRest2 = DeleteSet.readAndApplyDeleteSet(pendingDSUpdate,transaction,store);
            if(null != dsRest && null != dsRest2){
                List<byte[]> list = new ArrayList<>();
                list.add(dsRest);
                list.add(dsRest2);
                store.pendingDs = Updates2.mergeUpdatesV2(list,null,null);
            }else{
                if(null != dsRest){
                    store.pendingDs = dsRest;
                }else if(null != dsRest2){
                    store.pendingDs = dsRest2;
                }else if(null == dsRest && null == dsRest2){
                    store.pendingDs = null;
                }
            }
        }else{
            store.pendingDs = dsRest;
        }
        if(retry){
            byte[] update = store.pendingStructs.getUint8Array();
            store.pendingStructs = null;
            ByteArrayInputStream inputStream = new ByteArrayInputStream(update);
            applyUpdateV2(doc,update,null,UpdateDecoderV2.class);
        }
    }

    public List<Map<Long,Map<Integer,List<AbstractStruct>>>> readClientsStructRefs(IUpdateDecoder decoder,YDoc doc){
        List<Map<Long,Map<Integer,List<AbstractStruct>>>> clientRefs = new ArrayList<>();
        long numOfStateUpdates = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
        Map<Integer,List<AbstractStruct>> refMap = new HashMap<>();
        for(int i=0;i<numOfStateUpdates;i++){
            long numberOfStructs = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            List<AbstractStruct> refs = new ArrayList<>((int)numberOfStructs);
            long client = decoder.readClient();
            long clock = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            for(int j=0;j<numberOfStructs;j++){
                int info = decoder.readInfo();
                switch(Bits.Bits5 & info){
                    case 0:
                        long len = decoder.readLen();
                        refs.add(new Gc(new Id(client,clock),(int)len));
                        clock += len;
                        break;
                    case 10:
                        long currLen = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
                        refs.add(new Skip(new Id(client,clock),(int)currLen));
                        clock += currLen;
                        break;
                    default:
                        Boolean cantCopyParentInfo = (info & (Bit.Bit7 | Bit.Bit8)) == 0;
                        Id leftOrigin = (info & Bit.Bit8) == Bit.Bit8 ? decoder.readLeftID() : null;
                        Id rightOrigin = (info & Bit.Bit7) == Bit.Bit7 ? decoder.readRightID() : null;
                        // TODO doc.get()需要传入具体的类型，所以在这里应该还有一步获取当前类型的操作 当前默认父类
                        Item str = null;
                        try {
                            str = new Item(new Id(client,clock),null,leftOrigin,null,rightOrigin,
                                    cantCopyParentInfo ? decoder.readParentInfo() ? doc.get(decoder.readString(),new AbstractType().getClass()) : decoder.readLeftID() : null,
                                    cantCopyParentInfo && (info & Bit.Bit6) == Bit.Bit6 ? decoder.readString() : null,
                                    readItemContent(decoder,info));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        refs.add(str);
                        clock += str.getLength();

                }
                refMap.put(j,refs);
            }
            Map<Long,Map<Integer,List<AbstractStruct>>> reMap = new HashMap<>();
            reMap.put(client,refMap);
            clientRefs.add(reMap);
        }
        return clientRefs;
    }


    public class StructRefs{
        private Integer i;
        private List<AbstractStruct> refs;

        public StructRefs(){
            this.i = 0;
            this.refs =new ArrayList<>();
        }

        public StructRefs(Integer i,List<AbstractStruct> refs){
            this.i = i;
            this.refs =refs;
        }

        public Integer getI() {
            return i;
        }
        public void setI(Integer i) {
            this.i = i;
        }

        public List<AbstractStruct> getRefs() {
            return refs;
        }

        public void setRefs(List<AbstractStruct> refs) {
            this.refs = refs;
        }
    }


    public Map<Long,StructRefs> readClientsStructRefs2(IUpdateDecoder decoder, YDoc doc){
        Map<Long,StructRefs> clientRefs = new HashMap<>();
        long numOfStateUpdates = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
//        Map<Integer,List<AbstractStruct>> refMap = new HashMap<>();
        for(int i=0;i<numOfStateUpdates;i++){
            long numberOfStructs = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            List<AbstractStruct> refs = new ArrayList<>((int)numberOfStructs);
            long client = decoder.readClient();
            long clock = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            clientRefs.put(client,new StructRefs(0,refs));

            for(int j=0;j<numberOfStructs;j++){
                int info = decoder.readInfo();
                switch(Bits.Bits5 & info){
                    case 0:
                        long len = decoder.readLen();
                        refs.add(new Gc(new Id(client,clock),(int)len));
                        clock += len;
                        break;
                    case 10:
                        long currLen = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
                        refs.add(new Skip(new Id(client,clock),(int)currLen));
                        clock += currLen;
                        break;
                    default:
                        Boolean cantCopyParentInfo = (info & (Bit.Bit7 | Bit.Bit8)) == 0;
                        Id leftOrigin = (info & Bit.Bit8) == Bit.Bit8 ? decoder.readLeftID() : null;
                        Id rightOrigin = (info & Bit.Bit7) == Bit.Bit7 ? decoder.readRightID() : null;
                        // TODO doc.get()需要传入具体的类型，所以在这里应该还有一步获取当前类型的操作 当前默认父类
                        Item str = null;
                        try {
                            str = new Item(new Id(client,clock),
                                    null,leftOrigin,
                                    null,rightOrigin,
                                    cantCopyParentInfo ? decoder.readParentInfo() ? doc.get(decoder.readString(),new AbstractType().getClass()) : decoder.readLeftID() : null,
                                    cantCopyParentInfo && (info & Bit.Bit6) == Bit.Bit6 ? decoder.readString() : null,
                                    readItemContent(decoder,info));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        refs.add(str);
                        clock += str.getLength();

                }
            }
        }
        return clientRefs;
    }




    public PendingStructsDto integrateStructs(Transaction transaction, StructStore store, List<Map<Long,Map<Integer,List<AbstractStruct>>>> clientsStructRefs){
        Stack<AbstractStruct> stack = new Stack<>();
        // 按map的key倒序
        for(int i = 0;i<clientsStructRefs.size();i++){
            Map<Long, Map<Integer,List<AbstractStruct>>> clientsStructRefsMap = clientsStructRefs.get(i).entrySet().stream().sorted(Map.Entry.<Long, Map<Integer,List<AbstractStruct>>>comparingByKey().reversed())
                    .collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue,(oldValue,newValue)->oldValue,HashMap::new));
            List<Long> clientsStructRefsIds = clientsStructRefsMap.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toList());
            if(clientsStructRefsIds.size() == 0){
                return null;
            }

            // 获取下一个struct
            Map<Integer,List<AbstractStruct>> curStructsTarget = getNextStructsTarget(clientsStructRefsIds,clientsStructRefs);
            if(null == curStructsTarget && stack.size() == 0){
                return null;
            }
            StructStore restStructs = new StructStore();
            Map<Long,Long> missingSv = new HashMap<>();
            Map<Long,Long> state = new HashMap<>();
            for(Map.Entry<Integer, List<AbstractStruct>> entry: curStructsTarget.entrySet()){
                keyCount.getAndSet(entry.getKey());
                AbstractStruct stackHead = null;
                try{
                    stackHead = entry.getValue().get(keyCount.get());
                    keyCount.getAndIncrement();
                }catch(Exception e){
                    // do nothing  get(i+1)会抛出异常，返回null
                }
                a:while(true){
                    if(null != stackHead && stackHead.getClass() != Skip.class){
                        Long localClock = getState(store,stackHead.getId().getClient());
                        Long offset = localClock - stackHead.getId().getClock();
                        if(offset < 0){
                            stack.add(stackHead);
                            updateMissingSv(stackHead.getId().getClient(),stackHead.getId().getClock() -1,missingSv);
                            // hid a dead wall, add all items from stack to restSS
                            addStackToRestSS(restStructs,stack,clientsStructRefs,clientsStructRefsIds);
                            stack = new Stack<>();
                        }else{
                            Long missing = stackHead.getMissing(transaction,store);
                            if(null != missing){
                                stack.add(stackHead);
                                Map<Integer,List<AbstractStruct>> structRefs = clientsStructRefs.get(i).get(missing);
                                for(Map.Entry<Integer,List<AbstractStruct>> entrys : structRefs.entrySet()){
                                    if(entrys.getValue().size() == entrys.getKey()){
                                        updateMissingSv(missing,getState(store,missing),missingSv);
                                        addStackToRestSS(restStructs,stack,clientsStructRefs,clientsStructRefsIds);
                                        stack = new Stack<>();
                                    }else{
                                        try{
                                            stackHead = entrys.getValue().get(i++);
                                        }catch (Exception e){
                                            stackHead = null;
                                        }
                                        break a;
                                    }
                                }
                            }else if(offset == 0 || offset < stackHead.getLength()){
                                stackHead.integrate(transaction,offset.intValue());
                                state.put(stackHead.getId().getClient(),stackHead.getId().getClock() + stackHead.getLength());
                            }
                        }
                    }
                    if(stack.size() > 0){
                        stackHead = stack.pop();
                    }else if(null != curStructsTarget && keyCount.get() < entry.getValue().size()){
                        // stackHead = /** @type {GC|Item} */ (curStructsTarget.refs[curStructsTarget.i++])
                        try{
                            stackHead = entry.getValue().get(keyCount.getAndIncrement());
                        }catch (Exception e){
                            stackHead = null;
                        }
                    }else{
                        curStructsTarget = getNextStructsTarget(clientsStructRefsIds,clientsStructRefs);
                        if(null == curStructsTarget){
                            break;
                        }else{
                            try{
                                stackHead = entry.getValue().get(keyCount.getAndIncrement());
                            }catch (Exception e){
                                stackHead = null;
                            }
                        }
                    }
                }
            }

            if(restStructs.getClients().size() > 0){
                UpdateEncoderV2 encoder = new UpdateEncoderV2();
                writeClientsStructs(encoder,restStructs,new HashMap<>());
                StreamEncodingExtensions.writeVarUint(encoder.restEncoder,0L);
                PendingStructsDto integrateStructsDto = new PendingStructsDto();
                integrateStructsDto.setMissingSV(missingSv);
                integrateStructsDto.setUint8Array(encoder.toUint8Array());
                return integrateStructsDto;
            }
        }
        return null;
    }


    public PendingStructsDto integrateStructs2(Transaction transaction, StructStore store, Map<Long,StructRefs> clientsStructRefs){
        Stack<AbstractStruct> stack = new Stack<>();
        List<Long> clientsStructRefsIds = new ArrayList<>(clientsStructRefs.keySet());
        StructRefs curStructsTarget = getNextStructsTarget2(clientsStructRefsIds,clientsStructRefs);
        if (curStructsTarget == null && stack.isEmpty()) {
            return null;
        }

        StructStore restStructs = new StructStore();
        Map<Long,Long> missingSv = new HashMap<>();

        AbstractStruct stackHead = (curStructsTarget).refs.get((curStructsTarget).i++);
        Map<Long,Long> state = new HashMap<>();

        while (true) {
            if(null != stackHead && stackHead.getClass() != Skip.class) {
                state.putIfAbsent(stackHead.getId().getClient(),getState(store, stackHead.getId().getClient()));
                Long localClock = state.get(stackHead.getId().getClient());
                Long offset = localClock - stackHead.getId().getClock();
                if(offset < 0){
                    stack.add(stackHead);
                    updateMissingSv(stackHead.getId().getClient(),stackHead.getId().getClock() -1,missingSv);
                    // hid a dead wall, add all items from stack to restSS
                    addStackToRestSS2(restStructs,stack,clientsStructRefs,clientsStructRefsIds);
                    stack = new Stack<>();
                }else{
                    Long missing = stackHead.getMissing(transaction,store);
                    if(null != missing){
                        stack.add(stackHead);
                        StructRefs structRefs = clientsStructRefs.get(missing);
                        if(structRefs == null){
                            structRefs = new StructRefs();
                        }
                        if(structRefs.getRefs().size() == structRefs.getI()){
                            updateMissingSv(missing,getState(store,missing),missingSv);
                            addStackToRestSS2(restStructs,stack,clientsStructRefs,clientsStructRefsIds);
                            stack = new Stack<>();
                        }else{
                            stackHead = structRefs.getRefs().get(structRefs.getI());
                            structRefs.setI(structRefs.getI()+1);
                            continue;
                        }
                    }else if(offset == 0 || offset < stackHead.getLength()){
                        stackHead.integrate(transaction,offset.intValue());
                        state.put(stackHead.getId().getClient(),stackHead.getId().getClock() + stackHead.getLength());
                    }
                }
            }
            if(stack.size() > 0){
                stackHead = stack.pop();
            }else if(null != curStructsTarget && curStructsTarget.getI() < curStructsTarget.getRefs().size()){
                stackHead = curStructsTarget.getRefs().get(curStructsTarget.i++);
            }else{
                curStructsTarget = getNextStructsTarget2(clientsStructRefsIds,clientsStructRefs);
                if(null == curStructsTarget){
                    break;
                }else{
                    stackHead =  curStructsTarget.getRefs().get(curStructsTarget.i++);
                }
            }

        }
        if(restStructs.getClients().size() > 0){
            UpdateEncoderV2 encoder = new UpdateEncoderV2();
            writeClientsStructs(encoder,restStructs,new HashMap<>());
            StreamEncodingExtensions.writeVarUint(encoder.restEncoder,0L);
            PendingStructsDto integrateStructsDto = new PendingStructsDto();
            integrateStructsDto.setMissingSV(missingSv);
            integrateStructsDto.setUint8Array(encoder.toUint8Array());
            return integrateStructsDto;
        }
        return null;
    }

    public static void writeClientsStructs(IUpdateEncoder encoder,StructStore store,Map<Long,Long> _sm){
        Map<Long,Long> sm = new HashMap<>();
        _sm.forEach((client,clock) ->{
            if(getState(store,client) > clock){
                sm.put(client,clock);
            }
        });
        getStateVector(store).forEach((client,clock)->{
            if(!_sm.containsKey(client)){
                sm.put(client,0L);
            }
        });
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),sm.size());
        // 按client降序
        Map<Long, Long> sortedMap = sm.entrySet().stream().sorted(Map.Entry.<Long, Long>comparingByKey().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue,(oldValue,newValue)->oldValue,HashMap::new));
        sortedMap.forEach((client,clock) ->{
            try {
                writeStructs(encoder,store.getClients().get(client),client,clock);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void writeStructs(IUpdateEncoder encoder,List<AbstractStruct> structs,long client,long clock) throws Exception {
        Long maxClock = Math.max(clock,structs.get(0).getId().getClock());
        int startNewStructs = StructStore.findIndexSS(structs,maxClock);
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),structs.size() - startNewStructs);
        encoder.writeClient(client);
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),maxClock);
        AbstractStruct firstStruct = structs.get(startNewStructs);
        firstStruct.write(encoder,(int)(maxClock - firstStruct.getId().getClock()));
        for(int i= startNewStructs +1;i<structs.size();i++){
            structs.get(i).write(encoder,0);
        }
    }

    public static Map<Long,Long> getStateVector(StructStore store){
        Map<Long,Long> sm = new HashMap<>();
        store.getClients().forEach((aLong, structs) -> {
            AbstractStruct struct = structs.get(structs.size() - 1);
            sm.put(aLong,struct.getId().getClock() + struct.getLength());
        });
        return sm;
    }

    public static Long getState(StructStore store,Long client){
        List<AbstractStruct> structs = store.getClients().get(client);
        if(CollectionUtil.isEmpty(structs)){
            return 0L;
        }
        AbstractStruct lastStruct = structs.get(structs.size() - 1);
        return lastStruct.getId().getClock() + lastStruct.getLength();
    }

    public void addStackToRestSS(StructStore restStructs,Stack<AbstractStruct> stack,List<Map<Long,Map<Integer,List<AbstractStruct>>>> clientsStructRefs,List<Long> clientsStructRefsIds){
        for(AbstractStruct item : stack){
            long client = item.getId().getClient();
            for(int i=clientsStructRefs.size() - 1;i>=0;i--){
                Map<Integer,List<AbstractStruct>> unapplicableItems = clientsStructRefs.get(i).get(client);
                if(null != unapplicableItems && unapplicableItems.size() > 0){
                    for(Map.Entry<Integer,List<AbstractStruct>> entry : unapplicableItems.entrySet()){
                        Integer cur = entry.getKey() -1 < 0 ? 0 : entry.getKey() -1;
                        List<AbstractStruct> curAbs = entry.getValue();
                        // 截取curAbsList 加入restStructs
                        restStructs.getClients().put(client,curAbs.subList(cur,curAbs.size()));
                        // 移除当前client
                        clientsStructRefs.get(i).remove(client);
                    }
                }else{
                    List<AbstractStruct> list = new ArrayList<>();
                    list.add(item);
                    restStructs.getClients().put(client,list);
                }
                // 移除当前client
                clientsStructRefsIds.remove(client);
            }
        }
    }



    public void addStackToRestSS2(StructStore restStructs, Stack<AbstractStruct> stack,
                                  Map<Long,StructRefs> clientsStructRefs, List<Long> clientsStructRefsIds){
        for(AbstractStruct item : stack){
            long client = item.getId().getClient();
            StructRefs unapplicableItems = clientsStructRefs.get(client);
            if(null != unapplicableItems){
                unapplicableItems.setI(unapplicableItems.getI()-1);
                restStructs.getClients().put(client,Arrays.asList(unapplicableItems.getRefs().get(unapplicableItems.getI())));
                clientsStructRefs.remove(client);
                unapplicableItems.setI(0);
                unapplicableItems.setRefs(new ArrayList<>());
            }else{
                restStructs.getClients().put(client,Arrays.asList(item));
            }
            // 移除当前client
            clientsStructRefsIds.remove(client);
        }
    }

    public Map<Long,Long> updateMissingSv(Long client,Long clock,Map<Long,Long> missingSv){
        Long mclock = missingSv.get(client);
        if(null == mclock || mclock > clock){
            missingSv.put(client,clock);
        }
        return missingSv;
    }

    public Map<Integer,List<AbstractStruct>> getNextStructsTarget(List<Long> clientsStructRefsIds,List<Map<Long,Map<Integer,List<AbstractStruct>>>> clientsStructRefs){
        // 从list后面开始取值 即client的最小值开始
        if(clientsStructRefsIds.size() == 0){
            return null;
        }
        Map<Integer,List<AbstractStruct>> nextStructsTarget = new HashMap<>();
        List<Long> removeClientIds = new ArrayList<>();
        Long currClientId = clientsStructRefsIds.get(clientsStructRefsIds.size() - 1);
        for(int i=0;i<clientsStructRefs.size();i++){
            nextStructsTarget = clientsStructRefs.get(i).get(currClientId);
            for(Map.Entry<Integer,List<AbstractStruct>> entry : nextStructsTarget.entrySet()){
                if(entry.getValue().size() == keyCount.get()){
                    removeClientIds.add(currClientId);
                    //  取差集
                    clientsStructRefsIds.removeAll(removeClientIds);
                    if(clientsStructRefsIds.size() > 0){
                        nextStructsTarget = clientsStructRefs.get(i).get(currClientId);
                    }else{
                        return null;
                    }
                }
            }
        }
        return nextStructsTarget;
    }

    public StructRefs getNextStructsTarget2(List<Long> clientsStructRefsIds,
                                            Map<Long,StructRefs> clientsStructRefs){
        if(clientsStructRefsIds.isEmpty()){
            return null;
        }
        // 按map的key倒序
        clientsStructRefsIds.sort(Comparator.reverseOrder());
        StructRefs nextStructsTarget = /** @type {{i:number,refs:Array<GC|Item>}} */ (clientsStructRefs.get(clientsStructRefsIds.get(clientsStructRefsIds.size() - 1)));
        while (nextStructsTarget.refs.size() == nextStructsTarget.i) {
            clientsStructRefsIds.remove(clientsStructRefsIds.size()-1);
            if (clientsStructRefsIds.size() > 0) {
                nextStructsTarget = /** @type {{i:number,refs:Array<GC|Item>}} */ (clientsStructRefs.get(clientsStructRefsIds.get(clientsStructRefsIds.size() - 1)));
            } else {
                return null;
            }
        }
        return nextStructsTarget;
    }


    public static byte[] encodeStateAsUpdate(YDoc doc,byte[] encodedTargetStateVector){
        return encodeStateAsUpdateV2(doc,encodedTargetStateVector,new UpdateEncoderV1());
    }

    public static byte[] encodeStateAsUpdateV2(YDoc doc,byte[] encodedTargetStateVector,IUpdateEncoder encoder){
        List<byte[]> updates = new ArrayList<>();
        try {
            if(null == encoder){
                encoder = new UpdateEncoderV2();
            }
            Map<Long,Long> targetStateVector = decodeStateVector(encodedTargetStateVector);
            writeStateAsUpdate(encoder,doc,targetStateVector);
            updates.add(encoder.toUint8Array());
            if(null != doc.getStore().pendingDs){
                updates.add(doc.getStore().pendingDs);
            }
            if(null != doc.getStore().pendingStructs){
                updates.add(Updates2.diffUpdateV2(doc.getStore().pendingStructs.getUint8Array(),encodedTargetStateVector,null,null));
            }
            if(updates.size() > 1){
                if(encoder.getClass() == UpdateEncoderV1.class){
                    List<byte[]> list = new ArrayList<>();
                    for(int i=0;i< updates.size();i++){
                        if(i == 0){
                            list.add(updates.get(i));
                        }else{
                            list.add(Updates2.convertUpdateFormatV2ToV1(updates.get(i)));
                        }
                    }
                    return Updates2.mergeUpdates(list,null,null);
                }else if(encoder.getClass() == UpdateEncoderV2.class){
                    return Updates2.mergeUpdatesV2(updates,null,null);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return updates.get(0);
    }

    public static void writeStateAsUpdate(IUpdateEncoder encoder,YDoc doc,Map<Long,Long> targetStateVector) throws IOException {
        writeClientsStructs(encoder,doc.getStore(),targetStateVector);
        DeleteSet.writeDeleteSet(encoder,DeleteSet.createDeleteSetFromStructStore(doc.getStore()));
    }

    public static Map<Long,Long> decodeStateVector(byte[] decodedState){
        ByteArrayInputStream inputStream = new ByteArrayInputStream(decodedState);
        return readStateVector(new DsDecoderV1(inputStream));
    }

    public static Map<Long,Long> readStateVector(IDsDecoder decoder){
        Map<Long,Long> ss = new HashMap<>();
        long ssLength = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
        for(int i=0;i<ssLength;i++){
            Long client = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            Long clock = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            ss.put(client,clock);
        }
        return ss;
    }

    public static byte[] encodeStateVector(YDoc doc){
        return encodeStateVectorV2(doc,new DsEncoderV1());
    }

    public static byte[] encodeStateVectorV2(YDoc doc,IDsEncoder encoder){
        IDsEncoder returnEncoder;
        if(doc instanceof Map){
            Map<Long,Long> map = (Map)doc;
            returnEncoder = writeStateVector(encoder,map);
        }else{
            returnEncoder = writeDocumentStateVector(encoder,doc);
        }
        return returnEncoder.toUint8Array();
    }

    public static IDsEncoder writeStateVector(IDsEncoder encoder,Map<Long,Long> sv){
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),sv.size());
        Map<Long, Long> sortedMap = sv.entrySet().stream().sorted(Map.Entry.<Long, Long>comparingByKey().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue,(oldValue,newValue)->oldValue,HashMap::new));
        sortedMap.forEach((client,clock)->{
            StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),client);
            StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),clock);
        });
        return encoder;
    }

    public static IDsEncoder writeDocumentStateVector(IDsEncoder encoder,YDoc doc){
        return writeStateVector(encoder,getStateVector(doc.getStore()));
    }

    public static void writeStructsFromTransaction(IUpdateEncoder encoder,Transaction transaction){
        writeClientsStructs(encoder,transaction.getDoc().getStore(), transaction.beforeState);
    }

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

}
