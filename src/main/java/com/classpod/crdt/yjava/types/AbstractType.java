package com.classpod.crdt.yjava.types;

import com.classpod.crdt.yjava.structs.*;
import com.classpod.crdt.yjava.utils.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class AbstractType{
    public Item _item;
    public Item _start;
    public Map<String, Item> _map;

    public YDoc doc;

    public Integer _length;

    private EventHandler _eH;

    private EventHandler _dEH;

    public List<ArraySearchMarker> _searchMarker;

    private static final int maxSearchMarker = 80;


    public AbstractType() {
        this._item = null;
        this._map = new HashMap<>();
        this._start = null;
        this.doc = null;
        this._length = 0;
        this._eH = new EventHandler();
        this._dEH = new EventHandler();
        this._searchMarker = new ArrayList<>();
    }

    public AbstractType parent(){
        return null != this._item ? (AbstractType) this._item.parent : null;
    }

    public int length() {
        return this._length;
    }

    public AbstractType copy() {
        return null;
    }

    @Override
    public AbstractType clone () {
        return new AbstractType();
    }

    public void write (IUpdateEncoder encoder) {
        // do nothing
    }

    public Item first(){
        Item n = this._start;
        while(null != n && n.deleted()){
            n = (Item)n.right;
        }
        return n;
    }

    public void callObserver (Transaction transaction, Set<String> parentSubs) throws Exception {
        if(!transaction.local && null == this._searchMarker){
            this._searchMarker = new ArrayList<>();
        }
    }

    public Object toJSON(){
        return null;
    }

    public void observe(Method method) {
        this._eH.addEventHandlerListener(method);
    }

    public void observeDeep(Method method){this._dEH.addEventHandlerListener(method);}

    public void unObserve(Method method){this._eH.removeEventHandlerListener(method);}

    public void unObserveDeep(Method method){
        this._dEH.removeEventHandlerListener(method);
    }

    public static List<Item> getTypeChildren(AbstractType t){
        Item s = t._start;
        List<Item> arr = new ArrayList<>();
        while(null != s){
            arr.add(s);
            s = (Item)s.right;
        }
        return arr;
    }

   public static void callTypeObservers(AbstractType type, Transaction transaction, YEvent event) throws InvocationTargetException, IllegalAccessException {
       AbstractType changedType = type;
       Map<AbstractType, List<YEvent>> changedParentTypes = transaction.changedParentTypes;
        while (true) {
            if (!changedParentTypes.containsKey(type)) {
                List<YEvent> values = new ArrayList<>();
                changedParentTypes.put(type, values);
            }
            changedParentTypes.get(type).add(event);
            if (type._item == null) {
                break;
            }
            type = (AbstractType) (type._item.parent);
        }
        EventHandler.callEventHandlerListeners(changedType._eH, event, transaction);
    }

    public void integrate(YDoc doc, Item item) throws Exception {
        this.doc = doc;
        this._item = item;
    }

    public void callObserver(Transaction transaction, String parentSubs) {
        //do nothing
    }

    public static void typeListInsertGenerics(Transaction transaction, AbstractType parent, Integer index, List<Object> content, Integer length) throws Exception {
        if (index > parent._length) {
            throw new Exception("Length over");
        }
        if (index == 0) {
            if (parent._searchMarker != null) {
                updateMarkerChanges(parent._searchMarker, index, content.size());
            }
            typeListInsertGenericsAfter(transaction, parent, null, content);
            return;
        }
        int startIndex = index;
        ArraySearchMarker marker = findMarker(parent, index);
        Item n = parent._start;
        if (marker != null) {
            n = marker.p;
            index -= marker.index;
            if (index == 0) {
                n = n.getPrev();
                index += (n != null && n.countable() && !n.deleted()) ? n.getLength() : 0;
            }
        }
        for (; n != null; n = (Item)n.right) {
            if (!n.deleted() && n.countable()) {
                if (index <= n.getLength()) {
                    if (index < n.getLength()) {
                        StructStore.getItemCleanStart(transaction, new Id(n.getId().getClient(), n.getId().getClock() + index));
                    }
                    break;
                }
                index -= n.getLength();
            }
        }
        if (!parent._searchMarker.isEmpty()) {
            updateMarkerChanges(parent._searchMarker, startIndex, content.size());
        }
        typeListInsertGenericsAfter(transaction, parent, n, content);
    }

    public static void updateMarkerChanges(List<ArraySearchMarker> searchMarker, int index, int len) {
        for (int i = searchMarker.size() - 1; i >= 0; i--) {
            ArraySearchMarker m = searchMarker.get(i);
            if (len > 0) {
                Item p = m.p;
                p.setMarker(false);
                while (p != null && (p.deleted() || !p.countable())) {
                    p = (Item)p.left;
                    if (p != null && !p.deleted() && p.countable()) {
                        m.index -= p.getLength();
                    }
                }
                if (p == null || p.marker()) {
                    searchMarker.remove(i);
                    continue;
                }
                m.p = p;
                p.setMarker(true);
            }
            if (index < m.index || (len > 0 && index == m.index)) {
                m.index = Math.max(index, m.index + len);
            }
        }
    }

    public static void typeListInsertGenericsAfter(Transaction transaction, AbstractType parent, Item referenceItem, List<Object> content) {
        Item left = referenceItem;
        YDoc doc = transaction.getDoc();
        Long ownClientId = doc.clientId;
        StructStore store = doc.getStore();
        Item right = referenceItem == null ? parent._start : (Item) referenceItem.right;
        List<Object> jsonContent = new ArrayList<>();
        for (Object c : content) {
            if (c instanceof byte[]) {
                byte[] arr = (byte[]) c;
                packJsonContent(jsonContent, ownClientId, store, transaction, left, right, parent);
                left = new Item(new Id(ownClientId, StructStore.getState(store,ownClientId)), left, left == null ? null : left.lastId(), right, right == null ? null : right.getId(), parent, null, new ContentBinary(arr));
                left.integrate(transaction, 0);
            } else if (c instanceof YDoc) {
                YDoc d = (YDoc) c;
                packJsonContent(jsonContent, ownClientId, store, transaction, left, right, parent);
                left = new Item(new Id(ownClientId, StructStore.getState(store,ownClientId)), left, left == null ? null : left.lastId(), right, right == null ? null : right.getId(), parent, null, new ContentDoc(d));
                left.integrate(transaction, 0);
            } else if (c instanceof AbstractType) {
                AbstractType at = (AbstractType) c;
                packJsonContent(jsonContent, ownClientId, store, transaction, left, right, parent);
                left = new Item(new Id(ownClientId, StructStore.getState(store,ownClientId)), left, left == null ? null : left.lastId(), right, right == null ? null : right.getId(), parent, null, new ContentType(at));
                left.integrate(transaction, 0);
            } else {
                jsonContent.add(c);
            }
        }
        packJsonContent(jsonContent, ownClientId, store, transaction, left, right, parent);
    }

    public static void typeListPushGenerics(Transaction transaction, AbstractType parent, Integer index, List<Object> content, Integer length) {
        List<ArraySearchMarker> markerList = parent._searchMarker.isEmpty() ? new ArrayList<>() : parent._searchMarker;
        Item n = null;
        if(null != markerList && markerList.size() > 0) {
            ArraySearchMarker marker = markerList.stream().max(Comparator.comparing(ArraySearchMarker::getIndex)).get();
            n = marker.p;
            if (n != null) {
                while (n.right != null) {
                    n = (Item) n.right;
                }
            }
        }
        typeListInsertGenericsAfter(transaction, parent, n, content);
        return;
    }

    public static void typeListDelete(Transaction transaction, AbstractType parent, Integer index,List<Object> content, Integer length) throws Exception {
        if (length == 0) { return; }
        int startIndex = index;
        int startLength = length;
        ArraySearchMarker marker = findMarker(parent, index);
        Item n = parent._start;
        if (marker != null) {
            n = marker.p;
            index -= marker.index;
        }
        for (; n != null && index > 0; n = (Item)n.right) {
            if (!n.deleted() && n.countable()) {
                if (index < n.getLength()) {
                   StructStore.getItemCleanStart(transaction, new Id(n.getId().getClient(), n.getId().getClock() + index));
                }
                index -= n.getLength();
            }
        }
        while (length > 0 && n != null) {
            if (!n.deleted()) {
                if (length < n.getLength()) {
                    StructStore.getItemCleanStart(transaction, new Id(n.getId().getClient(), n.getId().getClock() + length));
                }
                n.delete(transaction);
                length -= n.getLength();
            }
            n = (Item)n.right;
        }
        if (length > 0) {
            throw new Exception("lengthExceeded");
        }
        if (!parent._searchMarker.isEmpty()) {
            updateMarkerChanges(parent._searchMarker, startIndex, -startLength + length);
        }
    }

    public static void packJsonContent(List<Object> jsonContent, Long ownClientId, StructStore store, Transaction transaction, Item left, Item right, AbstractType parent) {
        if (!jsonContent.isEmpty()) {
            left = new Item(new Id(ownClientId, StructStore.getState(store,ownClientId)), left, left == null ? null : left.lastId(), right, right == null ? null : right.getId(), parent, null, new ContentAny(jsonContent));
            left.integrate(transaction, 0);
            jsonContent = new ArrayList<>();
        }
    }


    private static ArraySearchMarker findNearBy(List<ArraySearchMarker> list, int index) {
        if (list.isEmpty()) {
            return null;
        }
        ArraySearchMarker marker = list.get(0);
        int min = Math.abs(index - marker.index);
        int old = 0;
        for (int i=0; i<list.size(); i++) {
            ArraySearchMarker searchMarker = list.get(i);
            if (min > Math.abs(index - searchMarker.index)) {
                min = Math.abs(index - searchMarker.index);
                old = i;
            }
        }
        return list.get(old);
    }

    public static ArraySearchMarker findMarker(AbstractType yarray, int index) {
        if (yarray._start == null || index == 0 || yarray._searchMarker == null) {
            return null;
        }
        //寻找和index最接近的marker
        ArraySearchMarker marker = findNearBy(yarray._searchMarker, index);
        Item p = yarray._start;
        int pindex = 0;
        if (marker != null) {
            p = marker.p;
            pindex = marker.index;
            refreshMarkerTimestamp(marker);
        }
        while (p.right != null && pindex < index) {
            if (!p.deleted() && p.countable()) {
                if (index < pindex + p.getLength()) {
                    break;
                }
                pindex += p.getLength();
            }
            p = (Item)p.right;
        }
        while (p.left != null && pindex > index) {
            p = (Item)p.left;
            if (!p.deleted() && p.countable()) {
                pindex -= p.getLength();
            }
        }
        while (p.left != null && p.left.getId().getClient().equals(p.getId().getClient()) && p.left.getId().getClock() + p.left.getLength() == p.getId().getClock()) {
            p = (Item)p.left;
            if (!p.deleted() && p.countable()) {
                pindex -= p.getLength();
            }
        }
        AbstractType abstractType = (AbstractType) p.parent;
        if (marker != null && Math.abs(marker.index - pindex) < abstractType.length() / maxSearchMarker) {
            overwriteMarker(marker, p, pindex);
            return marker;
        } else {
            return markPosition(yarray._searchMarker, p, pindex);
        }
    }

    public static void refreshMarkerTimestamp(ArraySearchMarker marker) {
        marker.timestamp = ArraySearchMarker.globalSearchMarkerTimestamp++;
    }

    public static void overwriteMarker(ArraySearchMarker marker, Item p, int index)  {
        marker.p.setMarker(false);
        marker.p = p;
        p.setMarker(true);
        marker.index = index;
        marker.timestamp = ArraySearchMarker.globalSearchMarkerTimestamp++;
    }

    public static ArraySearchMarker markPosition(List<ArraySearchMarker> searchMarker, Item p, int index) {
        if (searchMarker.size() >= maxSearchMarker) {
            ArraySearchMarker marker = searchMarker.stream().min(Comparator.comparing(ArraySearchMarker::getTimestamp)).get();
            overwriteMarker(marker, p, index);
            return marker;
        } else {
            ArraySearchMarker pm = new ArraySearchMarker(p, index);
            searchMarker.add(pm);
            return pm;
        }
    }

    public static List<Object> typeListToArray(AbstractType type) {
        List<Object> cs = new ArrayList<>();
        Item n = type._start;
        while (n != null) {
            if (n.countable() && !n.deleted()) {
                List<Object> c = n.content.getContent();
                for (int i = 0; i < c.size(); i++) {
                    cs.add(c.get(i));
                }
            }
            n = (Item)n.right;
        }
        return cs;
    }

    public static void typeListForEachSnapshot(AbstractType type,Method method,Snapshot snapshot) throws Exception {
        int index = 0;
        Item n = type._start;
        while(null != n){
            if(n.countable() && Snapshot.isVisible(n,snapshot)){
                List<Object> c = n.content.getContent();
                for(int i=0;i<c.size();i++){
                    method.invoke(c.get(i),index++,type);
                }
            }
            n = (Item)n.right;
        }
    }

    public List<Object> typeListToArraySnapshot(AbstractType type,Snapshot snapshot){
        List<Object> cs = new ArrayList<>();
        Item n = type._start;
        while(null != n){
            if(n.countable() && Snapshot.isVisible(n,snapshot)){
                List<Object> c = n.content.getContent();
                for(int i=0;i<c.size();i++){
                    cs.add(c.get(i));
                }
            }
            n = (Item)n.right;
        }
        return cs;
    }

    public static List<Object> typeListMap(AbstractType type,Method method) throws Exception {
        List<Object> result = new ArrayList<>();
        typeListForEach(type,method);
        return null;
    }

    public static Object typeListGet(AbstractType type, int index) {
        ArraySearchMarker marker = findMarker(type, index);
        Item n = type._start;
        if (marker != null) {
            n = marker.p;
            index -= marker.index;
        }
        for (; n != null; n = (Item) n.right) {
            if (!n.deleted() && n.countable()) {
                if (index < n.getLength()) {
                    return n.content.getContent().get(index);
                }
                index -= n.getLength();
            }
        }
        return null;
    }

    public static List<Object> typeListSlice(AbstractType type, int start, int end) {
        if (start < 0) {
            start = type._length + start;
        }
        if (end < 0) {
            end = type._length + end;
        }
        int len = end - start;
        List<Object> cs = new ArrayList<>();
        Item n = type._start;
        while (n != null && len > 0) {
            if (n.countable() && !n.deleted()) {
                List<Object> c = n.content.getContent();
                if (c.size() <= start) {
                    start -= c.size();
                } else {
                    for (int i = start; i < c.size() && len > 0; i++) {
                        cs.add(c.get(i));
                        len--;
                    }
                    start = 0;
                }
            }
            n = (Item)n.right;
        }
        return cs;
    }

    public static void typeListForEach(AbstractType type, Method method) throws InvocationTargetException, IllegalAccessException {
        int index = 0;
        Item n = type._start;
        while (n != null) {
            if (n.countable() && !n.deleted()) {
                List<Object> c = n.content.getContent();
                for (int i = 0; i < c.size(); i++) {
                    method.invoke(c.get(i), index++, type);
                }
            }
            n = (Item)n.right;
        }
    }

    protected Object typeMapGet(AbstractType parent,String key) {
        Item item = parent._map.get(key);
        if (item != null && !item.deleted()) {
            List<Object> content = item.content.getContent();
            return content.get(item.getLength() - 1);
        }
        return null;
    }

    protected void typeMapSet(Transaction transaction,AbstractType parent,String key, Object value){
        Item left = parent._map.get(key);
        YDoc doc = transaction.getDoc();
        Long ownClientId = doc.clientId;
        AbstractContent content;

        if (value == null) {
            List<Object> list = new ArrayList<>();
            list.add(value);
            content = new ContentAny(list);
        } else {
            if(value.getClass().equals(String.class)){
                ArrayList<Object> list = new ArrayList<>();
                list.add(value);
                content = new ContentAny(list);
            } else if (value.getClass().equals(YDoc.class)) {
                content = new ContentDoc((YDoc)value);
            } else if (value.getClass().equals(Byte.class)) {
                content = new ContentBinary((byte[])value);
            } else {
                content = new ContentType((AbstractType)value);
            }
        }
        Item newItem = new Item(new Id(ownClientId, StructStore.getState(doc.getStore(),ownClientId)), left,
            left == null ? null : left.lastId(), null, null, parent, key, content);
        newItem.integrate(transaction, 0);
    }

    protected void typeMapDelete(Transaction transaction, AbstractType parent,String key) {
        Item c = parent._map.get(key);
        if (c != null) {
            c.delete(transaction);
        }
    }

    public static Map<String,Object> typeMapGetAll(AbstractType parent){
        Map<String,Object> res = new HashMap<>();
        parent._map.forEach((key,value)->{
            if(!value.deleted()){
                res.put(key,value.content.getContent().get(value.getLength() - 1));
            }
        });
        return res;
    }

    public Boolean typeMapHas(AbstractType parent,String key){
        Item val = parent._map.get(key);
        return null != val && !val.deleted();
    }

    public static Object typeMapGetSnapshot(AbstractType parent,String key,Snapshot snapshot){
        Item v = parent._map.get(key);
        while(null != v && (!snapshot.getStateVector().containsKey(v.getId().getClient()) || (v.getId().getClock() >= (snapshot.getStateVector().get(v.getId().getClient()))))){
            v = (Item)v.left;
        }
        return null != v && Snapshot.isVisible(v,snapshot) ? v.content.getContent().get(v.getLength() - 1) : null;
    }

    protected Map<String, Item> typeMapEnumerate() {
        return _map.entrySet().stream().filter(e -> !e.getValue().deleted())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    protected Map<String, Object> typeMapEnumerateValues(AbstractType parent) {
        return parent._map.entrySet().stream().filter(e -> !e.getValue().deleted())
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                Item value = entry.getValue();
                return value.content.getContent().get(value.getLength() - 1);
            }));
    }

}
