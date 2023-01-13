package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.yjava.dto.PendingStructsDto;
import com.classpod.crdt.yjava.structs.AbstractStruct;
import com.classpod.crdt.yjava.structs.Gc;
import com.classpod.crdt.yjava.structs.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class StructStore {

    private Map<Long, List<AbstractStruct>> clients;
    private List<DsDecoderV2> pendingDeleteReaders = new ArrayList<>();
    public PendingStructsDto pendingStructs;
    public byte[] pendingDs;

    public StructStore() {
        this.clients = new HashMap<>();
        this.pendingStructs = null;
        this.pendingDs = null;
    }

    public Map<Long, List<AbstractStruct>> getClients() {
        return clients;
    }

    public static Map<Long, Long> getStateVector(StructStore store) {
        Map<Long, Long> result = new HashMap<>(store.clients.size());
        for (Map.Entry<Long,List<AbstractStruct>> entry: store.clients.entrySet()) {
            AbstractStruct str = entry.getValue().get(entry.getValue().size() - 1);
            result.put(entry.getKey(), str.getId().getClock() + str.getLength());
        }
        return result;
    }

    public static Long getState(StructStore store,Long client){
        List<AbstractStruct> structs = store.clients.get(client);
        if (structs == null) {
            return 0L;
        }
        AbstractStruct lastStruct = structs.get(structs.size() -1);
        return lastStruct.getId().getClock() + lastStruct.getLength();
    }

    public void integretyCheck(StructStore store){
        store.clients.forEach((key,structs) ->{
            for(int i=1;i<structs.size();i++){
                AbstractStruct l = structs.get(i -1);
                AbstractStruct r = structs.get(i);
                if(l.getId().getClock() + l.getLength() != r.getId().getClock()){
                    throw new RuntimeException("StructStore failed integrety check");
                }
            }
        });
    }

    /**
     * 二分查找
     * @param structs
     * @param clock
     * @return
     */
    public static int findIndexSS(List<AbstractStruct> structs, long clock) throws Exception {
        int left = 0;
        int right = structs.size() - 1;
        AbstractStruct mid = structs.get(right);
        long midClock = mid.getId().getClock();

        if (midClock == clock) {
            return right;
        }
        int midIndex = (int)((clock * right) / (midClock + mid.getLength() - 1));
        while (left <= right) {
            mid = structs.get(midIndex);
            midClock = mid.getId().getClock();
            if (midClock <= clock) {
                if (clock < midClock + mid.getLength()) {
                    return midIndex;
                }
                left = midIndex + 1;
            }
            else {
                right = midIndex - 1;
            }
            midIndex = (left + right) / 2;
        }
        throw new Exception("Unexpected");
    }


    public static int findIndexCleanStart(Transaction transaction, List<AbstractStruct> structs, long clock) throws Exception {
        int index = findIndexSS(structs, clock);
        AbstractStruct str = structs.get(index);
        if (str.getId().getClock() < clock && str instanceof Item)
        {
            Item item = (Item) str;
            structs.add(index + 1, item.splitItem(transaction,str, (int)(clock - str.getId().getClock())));
            return index + 1;
        }

        return index;
    }

    public static void replaceStruct(StructStore store, AbstractStruct struct, AbstractStruct newStruct) throws Exception {
        List<AbstractStruct> structs = store.clients.get(struct.getId().getClient());
        structs.add(findIndexSS(structs, struct.getId().getClock()), newStruct);
    }

    public static void iterateStructs(Transaction transaction, List<AbstractStruct> structs, long clockStart, long length, Predicate<AbstractStruct> fun) throws Exception {
        if (length <= 0) {
            return;
        }
        long clockEnd = clockStart + length;
        int index = findIndexCleanStart(transaction, structs, clockStart);
        AbstractStruct str;
        do {
            str = structs.get(index);
            if (clockEnd < str.getId().getClock() + str.getLength()) {
                findIndexCleanStart(transaction, structs, clockEnd);
            }
            if (!fun.test(str)) {
                break;
            }
            index++;
        } while (index < structs.size() && structs.get(index).getId().getClock() < clockEnd);
    }

    public static Item getItemCleanStart(Transaction transaction, Id id) throws Exception {
        List<AbstractStruct> structs = transaction.getDoc().getStore().clients.get(id.getClient());
        return (Item) structs.get(findIndexCleanStart(transaction, structs, id.getClock()));
    }

    public static Item getItemCleanStart(StructStore store, Transaction transaction, Id id) throws Exception {
        List<AbstractStruct> structs = store.clients.get(id.getClient());
        return (Item)structs.get(findIndexCleanStart(transaction, structs, id.getClock()));
    }

    public static Item getItemCleanEnd(Transaction transaction,StructStore store,Id id) throws Exception {
        List<AbstractStruct> structs = store.clients.get(id.getClient());
        int index = findIndexSS(structs, id.getClock());
        AbstractStruct struct = structs.get(index);
        if(id.getClock() != struct.getId().getClock() + struct.getLength() - 1 && !(struct instanceof Gc)){
            Item item = (Item) struct;
            structs.add(index + 1,item.splitItem(transaction,struct, (id.getClock()) - struct.getId().getClock() + 1));
        }
        return (Item)struct;
    }

    public static void addStruct(StructStore store, AbstractStruct struct) {
        List<AbstractStruct> structs = store.clients.get(struct.getId().getClient());
        if (structs == null) {
            structs = new ArrayList<>();
            store.clients.put(struct.getId().getClient(), structs);
        } else {
            AbstractStruct lastStruct = structs.get(structs.size() - 1);
            if (lastStruct.getId().getClock() + lastStruct.getLength() != struct.getId().getClock()) {
                return;
            }
        }
        structs.add(struct);
        // 将struct加入store.clients
        store.clients.put(struct.getId().getClient(),structs);
    }

    public Object[] followRedone(Id id) throws Exception {
        Id nextId = id;
        int diff = 0;
        AbstractStruct item;

        do {
            if (diff > 0) {
                nextId = new Id(nextId.getClient(), nextId.getClock() + diff);
            }
            item = find(this,nextId);
            diff = (int)(nextId.getClock() - item.getId().getClock());
            nextId = ((Item)item).redone;
        } while (nextId != null && item instanceof Item);
        return new Object[] {diff, item};
    }

    public AbstractStruct find(StructStore store,Id id) throws Exception {
        if (!store.clients.containsKey(id.getClient())) {
            throw new Exception("No structs for client: " + id.getClient());
        }
        List<AbstractStruct> structs = clients.get(id.getClient());
        int index = findIndexSS(structs, id.getClock());
        if (index < 0 || index >= structs.size()) {
            throw new Exception("Invalid struct index: " + index + ", max: " + structs.size());
        }
        return structs.get(index);
    }
}
