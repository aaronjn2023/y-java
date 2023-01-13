package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.yjava.YObservable;
import com.classpod.crdt.yjava.structs.ContentDoc;
import com.classpod.crdt.yjava.structs.Item;
import com.classpod.crdt.yjava.types.AbstractType;
import com.classpod.crdt.yjava.types.YArray;
import com.classpod.crdt.yjava.types.YMap;
import com.classpod.crdt.yjava.types.YText;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Predicate;

public class YDoc extends YObservable {
    public boolean gc;
    public Predicate<Item> gcFilter;
    public String guid;
    private StructStore store;

    public long clientId;

    public String collectionId;

    public Map<String, AbstractType> share;

    public Transaction transaction;

    public List<Transaction> transactionCleanups;

    public Set<YDoc> subdocs;

    public Item _item;

    public boolean shouldLoad;

    public boolean autoLoad;

    public boolean isLoad;

    public Map<String, String> meta;

    public  EventHandler afterTransaction;

    public StructStore getStore() {
        return store;
    }

    public YDoc(){
        this.guid = UUID.randomUUID().toString();
        this.collectionId = null;
        this.gc = true;
        this.gcFilter = YDocOptions.defaultPredicate;
        this.share = new HashMap<>();
        this.meta = null;
        this.autoLoad = false;
        this.shouldLoad = true;
    }

    public YDoc(YDocOptions yDocOptions) {
        super();
        this.gc = yDocOptions.gc;
        this.gcFilter = yDocOptions.gcFilter;
        this.clientId = generateNewClientId();
        this.guid = yDocOptions.guid;
        this.collectionId = yDocOptions.collectionId;
        this.share = new HashMap<>();
        this.store = new StructStore();
        this.transaction = null;
        this.transactionCleanups = new ArrayList<>();
        this.subdocs = new HashSet<>();
        this._item = null;
        this.shouldLoad = yDocOptions.shouldLoad;
        this.autoLoad = yDocOptions.autoLoad;
        this.meta = yDocOptions.meta;
        this.isLoad = false;
    }

    public static int generateNewClientId()
    {
        int clientId = Math.abs(new Random().nextInt());
        System.out.println("clientId :" + clientId);
        return clientId;
    }

    public void load() throws Exception {
        Item item = this._item;
        if (item != null && !this.shouldLoad) {
            AbstractType at = (AbstractType)(item.parent);
            transact(at.doc,transaction -> transaction.subdocsLoaded.add(this), null, true);
        }
        this.shouldLoad = true;
    }

    public Set<YDoc> getSubdocs() {
        return this.subdocs;
    }

    public Set<String> getSubdocGuids() {
        HashSet<String> result = new HashSet<>();
        for(YDoc t: this.subdocs) {
            result.add(t.guid);
        }
        return result;
    }

    public void transact(ActionFunInterface<Transaction> fun, Object o, boolean b) throws Exception {
        this.transact(this,fun,null,true);
    }

    public void transact(YDoc doc,ActionFunInterface<Transaction> fun, Object origin, Boolean local) throws Exception {
        List<Transaction> transactionCleanups = doc.transactionCleanups;
        boolean initialCall = false;
        if (local == null) {
            local = true;
        }
        if (doc.transaction == null) {
            initialCall = true;
            doc.transaction = new Transaction(this, origin, local);
            transactionCleanups.add(doc.transaction);
            if (transactionCleanups.size() == 1) {
                notifyObservers("beforeAllTransactions", this);
            }
            notifyObservers("beforeTransaction", doc.transaction, this);
        }
        try {
            fun.action(doc.transaction);
        } finally {
            if (initialCall && transactionCleanups.get(0) == doc.transaction) {
                Transaction.cleanupTransactions(transactionCleanups, 0);
            }
        }
    }

    public <T> T get(String name, Class<? extends AbstractType> tt) throws Exception {
        AbstractType type = null;
        if(AbstractType.class.isAssignableFrom(tt.newInstance().getClass()) && tt.newInstance().getClass().equals(YText.class)){
            Constructor constructor = tt.getDeclaredConstructor(String.class);
            type = (AbstractType) constructor.newInstance(name);
        }else{
            type = tt.newInstance();
        }
        if (!this.share.containsKey(name)) {
            type.integrate(this, null);
            this.share.put(name, type);
        } else {
            type = this.share.get(name);
        }
        if (tt != AbstractType.class && !tt.isAssignableFrom(type.getClass())) {
            if (type.getClass() == AbstractType.class) {
                AbstractType t = tt.newInstance();
                t._map = type._map;
                for (Item value : type._map.values()) {
                    for (; value != null; value = (Item) value.left) {
                        value.parent = t;
                    }
                }
                t._start = type._start;
                for (Item n = t._start; n != null; n = (Item) n.right) {
                    n.parent = t;
                }
                t._length = type._length;
                this.share.put(name, t);
                t.integrate(this, null);
                return (T)t;
            } else {
                throw new Exception("Type with the name has already been defined with a different constructor");
            }
        }
        return (T)type;
    }

    public YArray getArray(String name) throws Exception {
        return this.get(name, YArray.class);
    }

    public YText getText(String name) throws Exception {
        return this.get(name, YText.class);
    }

    public YMap getMap(String name) throws Exception {
        return this.get(name, YMap.class);
    }

    public Map<String,Object> toJSON(){
        Map<String,Object> doc = new HashMap<>();
        this.share.forEach((key,value) ->{
            doc.put(key, value.toJSON());
        });
        return doc;
    }

    @Override
    public void destroy() throws Exception {
        for(YDoc t: this.subdocs) {
            t.destroy();
        }
        Item item = this._item;
        if (item != null) {
            this._item = null;
            ContentDoc content = (ContentDoc) (item.content);
            content.opts.guid = this.guid;
            content.opts.shouldLoad = false;
            content.doc = new YDoc(content.opts);
            content.doc._item = item;
            transact(((AbstractType)(item.parent)).doc,transaction -> {
                YDoc doc = content.doc;
                if (!item.deleted()) {
                    transaction.subdocsAdded.add(doc);
                }
                transaction.subdocsRemoved.add(this);
            }, null, true);
        }
        notifyObservers("destroyed", true);
        notifyObservers("destroy", true);
        super.destroy();
    }
}
