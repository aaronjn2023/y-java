package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.yjava.structs.AbstractStruct;
import com.classpod.crdt.yjava.structs.ContentType;
import com.classpod.crdt.yjava.structs.Item;
import com.classpod.crdt.yjava.types.AbstractType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Transaction {
    static final Logger logger = LoggerFactory.getLogger(Transaction.class);

    private YDoc doc;
    public Object origin;
    public Boolean local;

    public DeleteSet deleteSet;

    public Map<Long, Long> beforeState;
    public Map<Long, Long> afterState;

    public Map<AbstractType, Set<String>> changed;

    public List<AbstractStruct> _mergeStructs;

    public Map<AbstractType, List<YEvent>> changedParentTypes;

    public Map<String, Object> meta;

    public Set<YDoc> subdocsAdded;

    public Set<YDoc> subdocsRemoved;

    public Set<YDoc> subdocsLoaded;

    public YDoc getDoc() {
        return doc;
    }

    public Transaction(YDoc doc, Object origin, Boolean local) {
        this.doc = doc;
        this.origin = origin;
        this.local = local;
        this.deleteSet = new DeleteSet();

        this.beforeState = StructStore.getStateVector(doc.getStore());
        this.afterState = new HashMap<>();
        this.changed = new HashMap<>();

        this._mergeStructs = new ArrayList<>();
        this.changedParentTypes = new HashMap<>();
        this.meta = new HashMap<>();
        this.subdocsAdded = new HashSet<>();
        this.subdocsRemoved = new HashSet<>();
        this.subdocsLoaded = new HashSet<>();
    }

    public static void transact(YDoc doc,Class<? extends Object> aClass, Method method, Object origin, boolean local, AbstractType parent, int index,
        List<Object> content, int length) throws NoSuchMethodException {
        List<Transaction> transactionCleanups = doc.transactionCleanups;
        boolean initialCall = false;
        if (doc.transaction == null) {
            initialCall = true;
            doc.transaction = new Transaction(doc, origin, local);
            transactionCleanups.add(doc.transaction);
            if (transactionCleanups.size() == 1) {
                doc.notifyObservers("beforeAllTransactions", doc);
            }
            doc.notifyObservers("beforeTransaction", doc.transaction, doc);
        }
        try {
            method.invoke(aClass.newInstance(),doc.transaction, parent, index, content, length);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        } finally {
            if (initialCall && transactionCleanups.get(0) == doc.transaction) {
                try {
                    cleanupTransactions(transactionCleanups, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void transactYjs(YDoc doc,Class<? extends Encoding> aClass, Method method, Object origin, boolean local, IUpdateDecoder decoder){
        List<Transaction> transactionCleanups = doc.transactionCleanups;
        boolean initialCall = false;
        if (doc.transaction == null) {
            initialCall = true;
            doc.transaction = new Transaction(doc, origin, local);
            transactionCleanups.add(doc.transaction);
            if (transactionCleanups.size() == 1) {
                doc.notifyObservers("beforeAllTransactions", doc);
            }
            doc.notifyObservers("beforeTransaction", doc.transaction, doc);
        }
        try {
            Encoding encoding = new Encoding();
            encoding.forceTransaction(doc,doc.transaction,decoder);
            // method.invoke(aClass.newInstance(),doc,doc.transaction,decoder);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (
                   initialCall &&
                            transactionCleanups.get(0).equals(doc.transaction)) {
                try {
                    cleanupTransactions(transactionCleanups, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void transact(YDoc doc,Class<? extends Object> aClass, Method method,Object... args){
        Boolean local = true;
        List<Transaction> transactionCleanups = doc.transactionCleanups;
        boolean initialCall = false;
        if (doc.transaction == null) {
            initialCall = true;
            doc.transaction = new Transaction(doc, null, local);
            transactionCleanups.add(doc.transaction);
            if (transactionCleanups.size() == 1) {
                doc.notifyObservers("beforeAllTransactions", doc);
            }
            doc.notifyObservers("beforeTransaction", doc.transaction, doc);
        }
        try {
                method.invoke(aClass.newInstance(),doc.transaction,args);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }finally {
            if (initialCall && transactionCleanups.get(0) == doc.transaction) {
                try {
                    cleanupTransactions(transactionCleanups, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void cleanupTransactions(List<Transaction> transactionCleanups, int i) throws Exception {
        if (i < transactionCleanups.size()) {
            Transaction transaction = transactionCleanups.get(i);
            YDoc doc = transaction.doc;
            StructStore store = doc.getStore();
            DeleteSet ds = transaction.deleteSet;
            List<AbstractStruct> mergeStructs = transaction._mergeStructs;
            List<Action> actions = new ArrayList<>();
            try {
                DeleteSet.sortAndMergeDeleteSet(ds);
                transaction.afterState = StructStore.getStateVector(store);
                doc.transaction = null;
                doc.notifyObservers("beforeObserverCalls",transaction,doc);
                transaction.changed.forEach((itemType,subs)->{
                    if(null == itemType._item || !itemType._item.deleted()){
                        try {
                            itemType.callObserver(transaction,subs);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                transaction.changedParentTypes.forEach((type,events)->{
                    if(null == type._item || !type._item.deleted()){
                        events = events.stream().filter(yEvent -> null == yEvent.target._item || !yEvent.target._item.deleted()).collect(Collectors.toList());
                        events.forEach(event ->{
                            event.currentTarget = type;
                        });
                        events.stream().sorted((event1,event2)->event1.path().size() - event2.path().size()).collect(Collectors.toList());
                        try {
                            EventHandler.callEventHandlerListeners(EventHandler.createEventHandler(),events,transaction);
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                });
                doc.notifyObservers("afterTransaction",transaction,doc);
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                if(doc.gc){
                    tryGcDeleteSet(ds,store,doc.gcFilter);
                }
                tryMergeDeleteSet(ds,store);
                transaction.afterState.forEach((client,clock) ->{
                    long beforeClock = null == transaction.beforeState.get(client) ? 0 : transaction.beforeState.get(client);
                    if(beforeClock != clock){
                        List<AbstractStruct> structs = store.getClients().get(client);
                        try {
                            int firstChangePos = Math.max(StructStore.findIndexSS(structs,beforeClock),1);
                            for(int t=structs.size() -1;t>= firstChangePos;t--){
                                tryToMergeWithLeft(structs,t);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                for(int m=0;m<mergeStructs.size();m++){
                    Id id = mergeStructs.get(m).getId();
                    List<AbstractStruct> structs = store.getClients().get(id.getClient());
                    int replacedStructPos = StructStore.findIndexSS(structs,id.getClock());
                    if(replacedStructPos + 1 < structs.size()){
                        tryToMergeWithLeft(structs,replacedStructPos + 1);
                    }
                    if(replacedStructPos > 0){
                        tryToMergeWithLeft(structs,replacedStructPos);
                    }
                }

                if(!transaction.local && null != transaction.afterState.get(doc.clientId) && null != transaction.beforeState.get(doc.clientId) && !transaction.afterState.get(doc.clientId).equals(transaction.beforeState.get(doc.clientId))){
                    logger.info("Changed the client-id because another client seems to be using it");
                    doc.clientId = doc.generateNewClientId();
                }
                doc.notifyObservers("afterTransactionCleanup",transaction,doc);
                if(null != doc.obs && doc.obs.containsKey("update")){
                    UpdateEncoderV1 encoder = new UpdateEncoderV1();
                    Boolean hasContent = writeUpdateMessageFromTransaction(encoder,transaction);
                    if(hasContent){
                        doc.notifyObservers("update",encoder.toUint8Array(),transaction.origin,doc,transaction);
                    }
                }
                if(null != doc.obs && doc.obs.containsKey("updateV2")){
                    UpdateEncoderV2 encoder = new UpdateEncoderV2();
                    Boolean hasContent = writeUpdateMessageFromTransaction(encoder,transaction);
                    if(hasContent){
                        doc.notifyObservers("updateV2",encoder.toUint8Array(),transaction.origin,doc,transaction);
                    }
                }
                if(transaction.subdocsAdded.size() > 0 || transaction.subdocsRemoved.size() > 0 || transaction.subdocsLoaded.size() > 0){
                    transaction.subdocsAdded.forEach(subdoc ->{
                        subdoc.clientId = doc.clientId;
                        if(null == subdoc.collectionId){
                            subdoc.collectionId = doc.collectionId;
                        }
                        doc.subdocs.add(subdoc);
                    });
                    transaction.subdocsRemoved.forEach(subdoc ->{
                        doc.subdocs.remove(subdoc);
                    });
                    Map<String,Object> map = new HashMap<>();
                    map.put("loaded",transaction.subdocsLoaded);
                    map.put("added",transaction.subdocsAdded);
                    map.put("removed",transaction.subdocsRemoved);
                    doc.notifyObservers("subdocs",map,doc,transaction);
                    transaction.subdocsRemoved.forEach(subdoc ->{
                        try {
                            subdoc.destroy();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
                if(transactionCleanups.size() <= i + 1){
                    doc.transactionCleanups = new ArrayList<>();
                    doc.notifyObservers("afterAllTransactions",doc,transactionCleanups);
                }else{
                    cleanupTransactions(transactionCleanups,i + 1);
                }
            }
        }
    }

    public static Boolean writeUpdateMessageFromTransaction(IUpdateEncoder encoder,Transaction transaction) throws IOException {
        if(transaction.deleteSet.clients.size() == 0){
            Map<Long,Long> map = transaction.afterState;
            boolean flag = false;
            for (Map.Entry<Long, Long> entry : map.entrySet()) {
                if(transaction.beforeState == null
                        || transaction.beforeState.get(entry.getKey()) == null
                        || !transaction.beforeState.get(entry.getKey()).equals(entry.getValue())){
                    flag =  true;
                    break;
                }
            }
            if(!flag){
                 return false;
            }
        }
        DeleteSet.sortAndMergeDeleteSet(transaction.deleteSet);
        Encoding.writeStructsFromTransaction(encoder,transaction);
        DeleteSet.writeDeleteSet(encoder,transaction.deleteSet);
        return true;
    }

    public static void tryMergeDeleteSet(DeleteSet ds, StructStore store) throws Exception {
        for(Map.Entry<Long, List<DeleteItem>> entry : ds.clients.entrySet()){
            List<AbstractStruct> structs = store.getClients().get(entry.getKey());
            for(int i=entry.getValue().size() -1;i>=0;i--){
                DeleteItem deleteItem = entry.getValue().get(i);
                int mostRightIndexToCheck = Math.min(structs.size() - 1,1 + StructStore.findIndexSS(structs,deleteItem.getClock() + deleteItem.getLength() -1));
                for(AbstractStruct struct = structs.get(mostRightIndexToCheck);mostRightIndexToCheck > 0
                        && struct.getId().getClock() >= deleteItem.getClock();struct = structs.get(--mostRightIndexToCheck)){
                    tryToMergeWithLeft(structs,mostRightIndexToCheck);
                }
            }
        }
    }

    public static void tryToMergeWithLeft(List<AbstractStruct> structs,int pos){
        AbstractStruct left = structs.get(pos -1);
        AbstractStruct right = structs.get(pos);
        if(left.deleted() == right.deleted() && left.getClass() == right.getClass()){
            if(left.mergeWith(right)){
                structs.remove(pos);

            }
            if(right instanceof Item && null != ((Item) right).parentSub){
                AbstractType abstractType = (AbstractType) ((Item) right).parent;
                if(abstractType._map.get(((Item) right).parentSub) == right){
                    abstractType._map.put(((Item) right).parentSub,(Item) left);
                }
            }
        }
    }

    public static void tryGcDeleteSet(DeleteSet ds, StructStore store, Predicate<Item> gcFilter) throws Exception {
        for(Map.Entry<Long, List<DeleteItem>> entry : ds.clients.entrySet()){
            List<AbstractStruct> structs = store.getClients().get(entry.getKey());
            for(int i=entry.getValue().size() -1;i>=0;i--){
                DeleteItem deleteItem = entry.getValue().get(i);
                long endDeleteItemClock = deleteItem.getClock() + deleteItem.getLength();
                long si = StructStore.findIndexSS(structs,deleteItem.getClock());
                for(AbstractStruct struct = structs.get((int)si);
                    si < structs.size() && struct.getId().getClock() < endDeleteItemClock; si++){
                    AbstractStruct currStruct = structs.get((int)si);
                    if(deleteItem.getClock() + deleteItem.getLength() <= currStruct.getId().getClock()){
                        break;
                    }
                    if(currStruct instanceof Item && currStruct.deleted() && !((Item) currStruct).keep()
                            && gcFilter.test((Item)currStruct)){
                        ((Item) currStruct).gc(store,false);
                    }
                }
            }
        }
    }

    public static void addChangedTypeToTransaction(Transaction transaction, AbstractType type, String parentSub) {
        Item item = type._item;
        if (item == null || (
            item.getId().getClock() < (transaction.beforeState.getOrDefault(item.getId().getClient(), 0L))
                && !item.deleted())) {
            if (!transaction.changed.containsKey(type)) {
                transaction.changed.put(type, new HashSet<>());
            }
            transaction.changed.get(type).add(parentSub);
        }
    }

//    public static void splitSnapshotAffectedStructs(Transaction transaction, Snapshot snapshot) throws Exception {
//        Object metaObj = transaction.meta.computeIfAbsent("splitSnapshotAffectedStructs", k -> new HashSet<Snapshot>());
//        HashSet<Snapshot> meta = (HashSet<Snapshot>)metaObj;
//        StructStore store = transaction.getDoc().getStore();
//
//        // Check if we already split for this snapshot.
//        if (!meta.contains(snapshot)) {
//            for (Map.Entry<Long, Long> entry : snapshot.getStateVector().entrySet()) {
//                Long client = entry.getKey();
//                Long clock = entry.getValue();
//                if (clock < store.getState(client)) {
//                    StructStore.getItemCleanStart(transaction, new Id(client, clock));
//                }
//            }
//            snapshot.getDeleteSet().iterateDeletedStructs(transaction, item -> true);
//            meta.add(snapshot);
//        }
//    }

    /// <summary>
    /// Redoes the effect of this operation.
    /// </summary>
    public AbstractStruct redoItem(Item item, Set<Item> redoItems) throws Exception {
        YDoc doc = getDoc();
        StructStore store = doc.getStore();
        long ownClientId = doc.clientId;
        Id redone = item.redone;

        if (redone != null) {
            return StructStore.getItemCleanStart(this, redone);
        }

        Item parentItem = ((AbstractType)item.parent)._item;
        AbstractStruct left;
        AbstractStruct right;

        if (item.parentSub == null) {
            // Is an array item. Insert at the old position.
            left = item.left;
            right = item;
        } else {
            // Is a map item. Insert at current value.
            left = item;
            while (((Item)left).right != null) {
                left = ((Item)left).right;
                if (left.getId().getClient() != ownClientId) {
                    // It is not possible to redo this item because it conflicts with a change from another client.
                    return null;
                }
            }
            if (((Item)left).right != null) {
                left = ((AbstractType)item.parent)._map.get(item.parentSub);
            }
            right = null;
        }

        // Make sure that parent is redone.
        if (parentItem != null && parentItem.deleted() && parentItem.redone == null) {
            // Try to undo parent if it will be undone anyway.
            if (!redoItems.contains(parentItem) || redoItem(parentItem, redoItems) == null) {
                return null;
            }
        }

        if (parentItem != null && parentItem.redone != null) {
            while (parentItem.redone != null) {
                parentItem = StructStore.getItemCleanStart(this, parentItem.redone);
            }

            // Find next cloned_redo items.
            while (left != null) {
                AbstractStruct leftTrace = left;
                while (leftTrace != null && ((AbstractType)((Item)leftTrace).parent)._item != parentItem) {
                    leftTrace = ((Item)leftTrace).redone == null ? null :
                        StructStore.getItemCleanStart(this, ((Item)leftTrace).redone);
                }
                if (leftTrace != null && ((AbstractType)((Item)leftTrace).parent)._item == parentItem) {
                    left = leftTrace;
                    break;
                }
                left = ((Item)left).left;
            }

            while (right != null) {
                AbstractStruct rightTrace = right;
                while (rightTrace != null && ((AbstractType)((Item)rightTrace).parent)._item != parentItem) {
                    rightTrace = ((Item)rightTrace).redone == null ? null :
                        StructStore.getItemCleanStart(this, ((Item)rightTrace).redone);
                }
                if (rightTrace != null && ((AbstractType)((Item)rightTrace).parent)._item == parentItem) {
                    right = rightTrace;
                    break;
                }
                right = ((Item)right).right;
            }
        }

        Long nextClock = StructStore.getState(store,ownClientId);
        Id nextId = new Id(ownClientId, nextClock);

        Item redoneItem = buildRedoneItem(nextId, left, right, parentItem, item);
        item.redone = nextId;
        redoneItem.keepItemAndParents(true);
        redoneItem.integrate(this, 0);
        return redoneItem;
    }

    private Item buildRedoneItem(Id nextId, AbstractStruct left, AbstractStruct right, Item parentItem, Item item) {
        Id leftOrigin = null;
        Id rightOrigin = null;
        Object parent = null;
        if (left instanceof Item) {
            leftOrigin = ((Item)left).lastId();
        }
        if (right != null) {
            rightOrigin = right.getId();
        }
        if (parentItem == null) {
            parent = item.parent;
        } else {
            if (parentItem.content instanceof ContentType) {
                parent = ((ContentType)parentItem.content).type;
            }
        }
        return new Item(nextId, left, leftOrigin, right, rightOrigin, parent, item.parentSub, item.content.copy());
    }

}
