package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import com.classpod.crdt.yjava.structs.AbstractStruct;
import com.classpod.crdt.yjava.structs.Item;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/20 18:35 PM
 **/
public class Snapshot {

    private static final String SNAPSHOT_MAP_KEY = "snapshot_map_key";

    private DeleteSet deleteSet;

    private Map<Long, Long> stateVector;

    public DeleteSet getDeleteSet() {
        return deleteSet;
    }

    public Map<Long, Long> getStateVector() {
        return stateVector;
    }

    public Snapshot(DeleteSet deleteSet, Map<Long, Long> stateVector) {
        this.deleteSet = deleteSet;
        this.stateVector = stateVector;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Snapshot)) {
            return false;
        }
        Snapshot snapshot = (Snapshot)o;
        return Objects.equals(deleteSet, snapshot.deleteSet) && Objects.equals(stateVector, snapshot.stateVector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deleteSet, stateVector);
    }

    public byte[] encodeSnapshotV2(Snapshot snapshot,IDsEncoder encoder) throws IOException {
        if(null == encoder){
            encoder = new DsEncoderV2();
        }
        DeleteSet.writeDeleteSet(encoder,snapshot.getDeleteSet());
        Encoding.writeStateVector(encoder,snapshot.getStateVector());
        return encoder.toUint8Array();
    }

    public byte[] encodeSnapshot(Snapshot snapshot) throws IOException {
        return encodeSnapshotV2(snapshot,new DsEncoderV1());
    }

    public Snapshot decodeSnapshotV2(byte[] buf,IDsDecoder decoder){
        ByteArrayInputStream inputStream = new ByteArrayInputStream(buf);
        if(null == decoder){
            decoder = new DsDecoderV2(inputStream);
        }
        return new Snapshot(DeleteSet.readDeleteSet(decoder),Encoding.readStateVector(decoder));
    }

    public Snapshot decodeSnapshot(byte[] buf){
        return decodeSnapshotV2(buf,new DsDecoderV1(new ByteArrayInputStream(buf)));
    }

    public Snapshot createSnapshot(DeleteSet ds,Map<Long,Long> sm){
        return new Snapshot(ds,sm);
    }

    public Snapshot emptySnapshot(DeleteSet ds){
        return createSnapshot(ds,new HashMap<>());
    }

    public Snapshot createSnapshot(YDoc doc){
        return createSnapshot(DeleteSet.createDeleteSetFromStructStore(doc.getStore()),Encoding.getStateVector(doc.getStore()));
    }

    public static Boolean isVisible(Item item,Snapshot snapshot){
        return null == snapshot ? !item.deleted() : snapshot.getStateVector().containsKey(item.getId().getClient())
                && snapshot.getStateVector().get(item.getId().getClient()) > item.getId().getClock()
                && !DeleteSet.isDeleted(snapshot.getDeleteSet(),item.getId());
    }

    public static void splitSnapshotAffectedStructs(Transaction transaction,Snapshot snapshot){
        Map<String,Object> meta = null == transaction.meta ? new HashMap<>() : transaction.meta;
        StructStore store = transaction.getDoc().getStore();
        try {
            if(!meta.containsKey(SNAPSHOT_MAP_KEY)) {
                snapshot.getStateVector().forEach((client, clock) -> {
                    if (clock < StructStore.getState(store, client)) {
                        try {
                            StructStore.getItemCleanStart(transaction, new Id(client, clock));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                DeleteSet.iterateDeletedStructs(transaction, snapshot.getDeleteSet(), null);
                meta.put(SNAPSHOT_MAP_KEY,snapshot);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public YDoc createDocFromSnapshot(YDoc originDoc,Snapshot snapshot,YDoc newDoc) throws Exception{
        if(null == newDoc){
            newDoc = new YDoc();
        }
        if(originDoc.gc){
            throw new RuntimeException("originDoc must not be garbage collected");
        }

        Map<Long,Long> sv = snapshot.getStateVector();
        UpdateEncoderV2 encoder = new UpdateEncoderV2();
        originDoc.transact(transaction -> {
            int size = 0;
            for(Map.Entry<Long,Long> entry : sv.entrySet()){
                if(entry.getValue() > 0){
                    size += 1;
                }
            }
            StreamEncodingExtensions.writeVarUint(encoder.restEncoder,size);
            for(Map.Entry<Long,Long> entry : sv.entrySet()){
                Long client = entry.getKey();
                Long clock = entry.getValue();
                if(clock == 0L){
                    continue;
                }
                if(clock < StructStore.getState(originDoc.getStore(),client)){
                    StructStore.getItemCleanStart(transaction,new Id(client,clock));
                }
                List<AbstractStruct> structs = originDoc.getStore().getClients().get(client);
                int lastStructIndex = StructStore.findIndexSS(structs,clock - 1);
                StreamEncodingExtensions.writeVarUint(encoder.restEncoder,lastStructIndex - 1);
                encoder.writeClient(client);
                StreamEncodingExtensions.writeVarUint(encoder.restEncoder,0L);
                for(int i = 0;i<= lastStructIndex;i++){
                    structs.get(i).write(encoder,0);
                }
            }
            DeleteSet.writeDeleteSet(encoder,getDeleteSet());
        },null,true);
        Encoding.applyUpdateV2(newDoc,encoder.toUint8Array(),"snapshot",null);
        return newDoc;
    }

}
