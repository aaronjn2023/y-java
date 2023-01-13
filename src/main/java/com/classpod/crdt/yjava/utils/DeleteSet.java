package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamDecodingExtensions;
import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import com.classpod.crdt.yjava.structs.AbstractStruct;
import com.classpod.crdt.yjava.structs.Item;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class DeleteItem {
    private long clock;
    private long length;

    public DeleteItem(long clock, long length)
    {
        this.clock = clock;
        this.length = length;
    }

    public long getClock() {
        return clock;
    }

    public void setClock(long clock) {
        this.clock = clock;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }
}

public class DeleteSet {
    public Map<Long, List<DeleteItem>> clients;

    public DeleteSet() {
        this.clients = new HashMap<>();
    }

    public DeleteSet(List<DeleteSet> dss) {
        this.clients = mergeDeleteSets(dss).clients;
    }

    public static void iterateDeletedStructs(Transaction transaction,DeleteSet ds, Predicate<AbstractStruct> fun) throws Exception {
        for (Map.Entry<Long, List<DeleteItem>> entry : ds.clients.entrySet()) {
            List<AbstractStruct> structs = transaction.getDoc().getStore().getClients().get(entry.getKey());
            for (DeleteItem del : entry.getValue()) {
                StructStore.iterateStructs(transaction, structs, del.getClock(), del.getLength(), fun);
            }
        }
    }

    public static Integer findIndexDS(List<DeleteItem> dis, long clock) {
        int left = 0;
        int right = dis.size() - 1;
        while (left <= right) {
            int midindex = (left + right) / 2;
            DeleteItem mid = dis.get(midindex);
            long midclock = mid.getClock();
            if (midclock <= clock) {
                if (clock < midclock + mid.getLength()) {
                    return midindex;
                }
                left = midindex + 1;
            } else {
                right = midindex - 1;
            }
        }
        return null;
    }

    public static boolean isDeleted(DeleteSet ds, Id id) {
        List<DeleteItem> dis = ds.clients.get(id.getClient());
        return !dis.isEmpty() && findIndexDS(dis, id.getClock()) != null;
    }

    public static void sortAndMergeDeleteSet(DeleteSet ds) {
        Map<Long,List<DeleteItem>> deleteItemMap = ds.clients;
        deleteItemMap.forEach((key,dels) ->{
            dels.stream().sorted(Comparator.comparing(DeleteItem::getClock).reversed())
                    .collect(Collectors.toList());
            int i, j;
            for (i = 1, j = 1; i < dels.size(); i++) {
                DeleteItem left = dels.get(j - 1);
                DeleteItem right = dels.get(i);
                if (left.getClock() + left.getLength() >= right.getClock()) {
                    left.setLength(Math.max(left.getLength(), right.getClock() + right.getLength() - left.getClock()));
                } else {
                    if (j < i) {
                        dels.set(j,right);
                    }
                    j++;
                }
            }
//            if(j < dels.size()){
//                dels.subList(j,dels.size() - 1);
//            }
//            dels.length = j;
        });
//        for (Long key : ds.clients.keySet()) {
//            ds.clients.get(key).stream().sorted(Comparator.comparing(DeleteItem::getClock).reversed())
//                    .collect(Collectors.toList());
//            int i, j;
//            for (i = 1, j = 1; i < ds.clients.get(key).size(); i++) {
//                DeleteItem left = ds.clients.get(key).get(j - 1);
//                DeleteItem right = ds.clients.get(key).get(i);
//                if (left.getClock() + left.getLength() >= right.getClock()) {
//                    left.setLength(Math.max(left.getLength(), right.getClock() + right.getLength() - left.getClock()));
//                } else {
//                    if (j < i) {
//                        ds.clients.get(key).set(j,right);
//                    }
//                    j++;
//                }
//            }
//            ds.clients.put(key, ds.clients.get(key).subList(0, j - 1));
//        }
    }

    public static DeleteSet mergeDeleteSets(List<DeleteSet> dss) {
        DeleteSet merged = new DeleteSet();
        for (int dssI = 0; dssI < dss.size(); dssI++) {
            for (Long client : dss.get(dssI).clients.keySet()) {
                if (!merged.clients.containsKey(client)) {
                    List<DeleteItem> dels = dss.get(dssI).clients.get(client);
                    for (int i = dssI + 1; i < dss.size(); i++) {
                        dels.addAll(dss.get(i).clients.get(client));
                    }
                    merged.clients.put(client, dels);
                }
            }
        }
        sortAndMergeDeleteSet(merged);
        return merged;
    }

    public static void addToDeleteSet(DeleteSet ds, long client, long clock, long length) {
        if (!ds.clients.containsKey(client)) {
            ds.clients.put(client, new ArrayList<>());
        }
        ds.clients.get(client).add(new DeleteItem(clock, length));
    }

    public static DeleteSet createDeleteSet() {
        return new DeleteSet();
    }

    public static DeleteSet createDeleteSetFromStructStore(StructStore ss) {
        DeleteSet ds = createDeleteSet();
        ss.getClients().forEach((client, structs) -> {
            List<DeleteItem> dsitems = new ArrayList<>();
            for (int i = 0; i < structs.size(); i++) {
                AbstractStruct struct = structs.get(i);
                if (struct.deleted()) {
                    Long clock = struct.getId().getClock();
                    int len = struct.getLength();
                    if (i + 1 < structs.size()) {
                        for (AbstractStruct next = structs.get(i + 1); i + 1 < structs.size() && next.deleted(); i++) {
                            len += next.getLength();
                        }
                    }
                    dsitems.add(new DeleteItem(clock, len));
                }
            }
            if (!dsitems.isEmpty()) {
                ds.clients.put(client, dsitems);
            }
        });
        return ds;
    }

    public static void writeDeleteSet(IDsEncoder encoder, DeleteSet ds) throws IOException {
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),ds.clients.size());
        ds.clients.forEach((client,dsitems)->{
            encoder.resetDsCurVal();
            StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),client);
            long len = dsitems.size();
            System.out.println("[writeDeleteSet] dsitems.size()："+dsitems.size());
            StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),len);
            for(int i=0;i<len;i++){
                DeleteItem item = dsitems.get(i);
                encoder.writeDsClock(item.getClock());
                encoder.writeDsLength(item.getLength());
            }
        });
    }

    public static DeleteSet readDeleteSet(IDsDecoder decoder){
        DeleteSet ds = new DeleteSet();
        long numClients = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
        for (int i = 0; i < numClients; i++) {
            long client = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            long numberOfDeletes = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());;
            if (numberOfDeletes > 0) {
                List<DeleteItem> dsField = new ArrayList<>();
                if (!ds.clients.containsKey(client)) {
                    ds.clients.put(client, dsField);
                }
                for (int j = 0; j < numberOfDeletes; j++) {
                    dsField.add(new DeleteItem(decoder.readDsClock(), decoder.readDsLength()));
                }
            }
        }
        return ds;
    }

    public static byte[] readAndApplyDeleteSet(IDsDecoder decoder, Transaction transaction, StructStore store) throws Exception {
        DeleteSet unappliedDS = new DeleteSet();
        long numClients = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
        for (int i = 0; i < numClients; i++) {
            decoder.resetDsCurval();
            long client = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            long numberOfDeletes = StreamDecodingExtensions.readVarUInt(decoder.restDecoder());
            List<AbstractStruct> structs = store.getClients().getOrDefault(client, new ArrayList<>());
            long state = StructStore.getState(store,client);
            for (int j = 0; j < numberOfDeletes; j++) {
                long clock = decoder.readDsClock();
                long clockEnd = clock + decoder.readDsLength();
                if (clock < state) {
                    if (state < clockEnd) {
                        addToDeleteSet(unappliedDS, client, state, clockEnd - state);
                    }
                    int index = StructStore.findIndexSS(structs, clock);
                    AbstractStruct struct = structs.get(index);
                    if (!struct.deleted() && struct.getId().getClock() < clock) {
                        structs.add(index + 1, ((Item) struct).splitItem(transaction, struct,(int) (clock - struct.getId().getClock())));
                        index++;
                    }
                    while (index < structs.size()) {
                        struct = structs.get(index++);
                        if (struct.getId().getClock() < clockEnd) {
                            if (!struct.deleted()) {
                                if (clockEnd < struct.getId().getClock() + struct.getLength()) {
                                    structs.add(index, ((Item) struct).splitItem(transaction, struct,(int) (clockEnd - struct.getId().getClock())));
                                }
                                ((Item) struct).delete(transaction);
                            }
                        } else {
                            break;
                        }
                    }
                } else {
                    addToDeleteSet(unappliedDS, client, clock, clockEnd - clock);
                }
            }
        }
        if (unappliedDS.clients.size() > 0) {
            UpdateEncoderV2 ds = new UpdateEncoderV2();
            StreamEncodingExtensions.writeVarUint(ds.restEncoder,0L);
            writeDeleteSet(ds, unappliedDS);
            return ds.toUint8Array();
        }
        return null;
    }

    public void write(DsEncoderV2 encoder){
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder,clients.size());
        for(Map.Entry<Long, List<DeleteItem>> entry : clients.entrySet()){
            long client = entry.getKey();
            List<DeleteItem> dsItems = entry.getValue();
            long len = dsItems.size();
            encoder.resetDsCurVal();
            StreamEncodingExtensions.writeVarUint(encoder.restEncoder,client);
            StreamEncodingExtensions.writeVarUint(encoder.restEncoder,len);
            for(int i=0;i<len;i++){
                DeleteItem item = dsItems.get(i);
                encoder.writeDsClock(item.getClock());
                encoder.writeDsLength(item.getLength());
            }
        }
    }

}