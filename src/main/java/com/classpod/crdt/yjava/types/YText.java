package com.classpod.crdt.yjava.types;

import com.classpod.crdt.yjava.structs.*;
import com.classpod.crdt.yjava.utils.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/16 17:08 PM
 **/
public class YText extends AbstractType{

    private static final int CHANGE_TYPE_ADDED = 1;
    private static final int CHANGE_TYPE_REMOVED = 2;
    private final int YTextRefId = 2;
    private final String CHANGE_KEY = "ychange";
    private static AtomicInteger resCount = new AtomicInteger(0);

    private List<ArraySearchMarker> _searchMarker = null;
    private List<ActionFunInterface<Object>> pending;

    public YText() {
        super();
    }

    public YText(String str) {
        List<ActionFunInterface<Object>> actionFunInterfaces = new ArrayList<>();
        if (str != null) {
            actionFunInterfaces.add(empty -> insert(0, str, null));
        }
        pending = actionFunInterfaces;
        _searchMarker = new ArrayList<>();
    }

    public int getLength(){
        return this._length;
    }

    @Override
    public void integrate(YDoc doc, Item item) throws Exception {
        super.integrate(doc, item);
        if(null != pending && pending.size() > 0){
            for (ActionFunInterface<Object> anInterface : pending) {
                anInterface.action(null);
            }
        }
        pending = null;
    }

    @Override
    public YText copy(){
        return new YText();
    }

    @Override
    public YText clone() {
        YText text = new YText();
        try {
            text.applyDelta(toDelta(null, null, null), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return text;
    }

    public void applyDelta(List<Delta> delta, boolean sanitize) throws Exception {
        if (doc != null) {
            doc.transact(tr -> {
                ItemTextListPosition curPos = new ItemTextListPosition(null, this._start, 0, new HashMap<>());
                for (int i = 0; i < delta.size(); i++) {
                    Delta op = delta.get(i);
                    if (op.insert != null) {
                        // Quill assumes that the content starts with an empty paragraph.
                        // Yjs/Y.Text assumes that it starts empty. We always hide that
                        // there is a newline at the end of the content.
                        // If we omit this step, clients will see a different number of paragraphs,
                        // but nothing bad will happen.
                        Object ins;
                        if (!sanitize && (op.insert instanceof String) && i == delta.size() - 1 && curPos.right == null
                            && ((String)op.insert).endsWith("\n")) {
                            String insertStr = (String)op.insert;
                            ins = insertStr.substring(0, insertStr.length() - 1);
                        } else {
                            ins = op.insert;
                        }
                        if (!(ins instanceof String) || ((String)ins).length() > 0) {
                            // TODO: Null attributes by default to avoid unnecessary allocations?
                            insertText(tr,this, curPos, ins, op.attributes == null ? new HashMap<>() : op.attributes);
                        }
                    } else if (op.retain != null) {
                        formatText(tr, this,curPos, op.retain, op.attributes == null ? new HashMap<>() : op.attributes);
                    } else if (op.delete != null) {
                        deleteText(tr, curPos, op.delete);
                    }
                }
            }, null, true);
        } else {
            pending.add(empty -> applyDelta(delta, sanitize));
        }
    }

    private void packStr(String str, Map<String, Object> currentAttributes, List<Delta> ops) {
        if (str.length() > 0) {
            // Pack str with attributes to ops.
            HashMap<String, Object> attributes = new HashMap<>();
            boolean addAttributes = false;
            for (Map.Entry<String, Object> entry : currentAttributes.entrySet()) {
                addAttributes = true;
                attributes.put(entry.getKey(), entry.getValue());
            }
            Delta op = new Delta();
            op.insert = str;
            if (addAttributes) {
                op.attributes = attributes;
            }
            ops.add(op);
            str = "";
        }
    }

    public List<Delta> toDelta(Snapshot snapshot, Snapshot prevSnapshot,
        ComputeYChangeFun<Integer, Id, YTextChangeAttributes> computeYChange) throws Exception {
        ArrayList<Delta> ops = new ArrayList<>();
        HashMap<String, Object> currentAttributes = new HashMap<>();
        final StringBuffer[] str = {new StringBuffer()};
        Item n = this._start;
        YDoc doc = this.doc;
        packStr(str[0].toString(), currentAttributes, ops);

        final StringBuilder[] stringBuilders = {new StringBuilder()};
        final Item[] items = {_start};

        // Snapshots are merged again after the transaction, so we need to keep the
        // transaction alive until we are done.
        doc.transact(tr -> {
            if (snapshot != null) {
                Snapshot.splitSnapshotAffectedStructs(tr, snapshot);
            }
            if (prevSnapshot != null) {
                Snapshot.splitSnapshotAffectedStructs(tr, prevSnapshot);
            }
            while (n != null) {
                if (Snapshot.isVisible(n,snapshot) || (prevSnapshot != null && Snapshot.isVisible(n,prevSnapshot))) {
                    AbstractContent content = n.content;
                    if (content instanceof ContentString) {
                        Object val = currentAttributes.get(CHANGE_KEY);
                        YTextChangeAttributes cur = null;
                        if (val != null) {
                            cur = (YTextChangeAttributes)val;
                        }
                        if (snapshot != null && !Snapshot.isVisible(n,snapshot)) {
                            if (cur == null || cur.getUser() != n.getId().getClient() || cur.getState() != CHANGE_TYPE_REMOVED) {
                                packStr(str[0].toString(), currentAttributes, ops);
                                currentAttributes.put(CHANGE_KEY, computeYChange != null ? computeYChange.compute(CHANGE_TYPE_REMOVED, n.getId()) :
                                        new YTextChangeAttributes(CHANGE_TYPE_REMOVED));
                            }
                        } else if (prevSnapshot != null && !Snapshot.isVisible(n,prevSnapshot)) {
                            if (cur == null || cur.getUser() != n.getId().getClient() || cur.getState() != CHANGE_TYPE_ADDED) {
                                packStr(str[0].toString(), currentAttributes, ops);
                                currentAttributes.put(CHANGE_KEY, computeYChange != null ? computeYChange.compute(CHANGE_TYPE_ADDED, n.getId()) :
                                    new YTextChangeAttributes(CHANGE_TYPE_ADDED));
                            }
                        } else if (cur != null) {
                            packStr(str[0].toString(), currentAttributes, ops);
                            currentAttributes.remove(CHANGE_KEY);
                        }
                        String contString = ((ContentString)n.content).str;
                        str[0].append(contString);
//                        ContentString cs = (ContentString)content;
//                        stringBuilders[0].append(cs.getString());
                    } else if (content instanceof ContentEmbed) {
                        packStr(str[0].toString(), currentAttributes, ops);
                        Delta op = new Delta();
                        op.insert = n.content.getContent().get(0);

                        if (!currentAttributes.isEmpty()) {
                            Map<String,Object> attrs = new HashMap<>();
                            op.attributes = attrs;
                            currentAttributes.forEach((key,value)->{
                                attrs.put(key,value);
                            });
                        }
                        ops.add(op);
                    } else if (content instanceof ContentFormat) {
                        if (Snapshot.isVisible(n,snapshot)) {
                            ContentFormat cf = (ContentFormat)content;
                            packStr(str[0].toString(), currentAttributes, ops);
                            updateCurrentAttributes(currentAttributes, (ContentFormat) n.content);
                        }
                    }

                }
                items[0] = (Item)n.right;
            }
            packStr(str[0].toString(), currentAttributes, ops);
        }, "splitSnapshotAffectedStructs", true);
        return ops;
    }

    public void insert(int index, String text, Map<String, Object> attributes) throws Exception {
        if (text == null || text.isEmpty()) {
            return;
        }
        YDoc doc = this.doc;
        final Map<String, Object> finalAttributes = attributes;
        if (doc != null) {
            doc.transact(tr -> {
                ItemTextListPosition pos = findPosition(tr,this,index);
                if (null == attributes) {
                    insertText(tr,this, pos, text, new HashMap<>(pos.currentAttributes));
                } else {
                    insertText(tr,this, pos, text, finalAttributes);
                }
            }, null, true);
        } else {
            pending.add(t -> insert(index, text, finalAttributes));
        }
    }

    public void insertEmbed(int index, Object embed, Map<String, Object> attributes) throws Exception {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        final Map<String, Object> finalAttributes = attributes;
        if (doc != null) {
            doc.transact(tr -> insertText(tr, this,findPosition(tr, this,index), embed, finalAttributes), null, true);
        } else {
            pending.add(empty -> insertEmbed(index, embed, finalAttributes));
        }
    }

    public void delete(int index, int length) throws Exception {
        if (length == 0) {
            return;
        }
        if (doc != null) {
            doc.transact(tr -> deleteText(tr, findPosition(tr,this, index), length), null, true);
        } else {
            pending.add(empty -> delete(index, length));
        }
    }

    public void format(int index, int length, Map<String, Object> attributes) throws Exception {
        if (length == 0) {
            return;
        }

        if (doc != null) {
            doc.transact(tr -> {
                ItemTextListPosition pos = findPosition(tr,this,index);
                if (pos.right == null) {
                    return;
                }
                formatText(tr, this,pos, length, attributes);
            }, null, true);
//            YText yText = new YText();
//            Transaction.transact(doc,yText.getClass(),yText.getClass().getMethod("findPosition",Transaction.class,Integer.class),index);
        } else {
            pending.add(empty -> format(index, length, attributes));
        }
    }

    @Override
    public String toString() {
        String str = "";
        Item n = this._start;
        while (n != null) {
            if (!n.deleted() && n.countable() && n.content instanceof ContentString) {
                str += ((ContentString)n.content).str;
            }

            n = (Item)n.right;
        }
        return str;
    }


    public void removeAttribute(String name) throws Exception {
        YText yText = new YText();
        if (doc != null) {
            doc.transact(transaction -> {
                typeMapDelete(transaction,this,name);
            },null,true);
//            Transaction.transact(doc,yText.getClass(),yText.getClass().getMethod("removeAttribute",String.class), name);
        } else {
            pending.add(empty -> removeAttribute(name));
        }
    }

    public void setAttribute(String name, Object value) throws Exception {
        YText yText = new YText();
        if (doc != null) {
            doc.transact(transaction -> {
                typeMapSet(transaction,this,name,value);
            },null,true);
//            AbstractType abstractType = new AbstractType();
//            Transaction.transact(doc,abstractType.getClass(),abstractType.getClass().getMethod("typeMapSet",Transaction.class,String.class, Object.class),name,value);
        } else {
            pending.add(empty -> setAttribute(name, value));
        }
    }


    public Object getAttribute(String name) {
        return typeMapGet(this,name);
    }

    public Object getAttributes() {
        return typeMapEnumerateValues(this);
    }

    @Override
    public void write(IUpdateEncoder encoder){
        encoder.writeTypeRef(YTextRefId);
    }

    public YText readYText(IUpdateDecoder decoder){
        return new YText();
    }

    @Override
    public void callObserver(Transaction transaction, Set<String> parentSubs) throws Exception {
        super.callObserver(transaction, parentSubs);

        YTextEvent evt = new YTextEvent(this, transaction, parentSubs);
        YDoc doc = transaction.getDoc();
        callTypeObservers(this,transaction,evt);
        // If a remote change happened, we try to cleanup potential formatting duplicates.
        if (!transaction.local) {
            // Check if another formatting item was inserted.
            AtomicReference<Boolean> foundFormattingItem = new AtomicReference<>(false);

            for (Map.Entry<Long, Long> kvp : transaction.afterState.entrySet()) {
                Long client = kvp.getKey();
                Long afterClock = kvp.getValue();
                Long clock = null == transaction.beforeState.get(client) ? 0L : transaction.beforeState.get(client);
                if (afterClock.equals(clock)) {
                    continue;
                }
                StructStore.iterateStructs(transaction, doc.getStore().getClients().get(client), clock, afterClock, item -> {
                        if (!item.deleted() && ((Item)item).content instanceof ContentFormat) {
                            foundFormattingItem.set(true);
                        }
                        return true;
                    });
                if (foundFormattingItem.get()) {
                    break;
                }
            }
            if (!foundFormattingItem.get()) {
                DeleteSet.iterateDeletedStructs(transaction, transaction.deleteSet,item -> {
                    if (item instanceof Gc || foundFormattingItem.get()) {
                        return false;
                    }
                    Item it = (Item)item;
                    if (it != null && it.parent == this && it.content instanceof ContentFormat) {
                        foundFormattingItem.set(true);
                        // Don't iterate further.
                        return false;
                    }
                    return true;
                });
            }
            YText yText = new YText();
            Method method = yText.getClass().getMethod("cleanFun",Transaction.class,YText.class,AtomicReference.class);
            Transaction.transact(doc,yText.getClass(),method);
        }
//        callTypeObservers(this, transaction, evt);
    }

    private void cleanFun(Transaction transaction,YText yText,AtomicReference<Boolean> foundFormattingItem) throws Exception{
        if (foundFormattingItem.get()) {
            // If a formatting item was inserted, we simply clean the whole type.
            // We need to compuyte currentAttributes for the current position anyway.
            cleanupYTextFormatting(this);
        } else {
            // If no formatting attribute was inserted, we can make due with contextless formatting cleanups.
            // Contextless: it is not necessary to compute currentAttributes for the affected position.
            DeleteSet.iterateDeletedStructs(transaction,transaction.deleteSet,item -> {
                if(item instanceof Gc){
                    return false;
                }
                Item it = (Item)item;
                if (it != null && it.parent == this) {
                    cleanupContextlessFormattingGap(transaction, it);
                }
                return true;
            });
        }
    }

    @Override
    public String toJSON(){
        return this.toString();
    }

    public int cleanupYTextFormatting(YText type) throws NoSuchMethodException {
        YText yText = new YText();
        Method method = yText.getClass().getMethod("tranctionFun",Transaction.class,YText.class);
        Transaction.transact(doc,yText.getClass(),method);
        return resCount.get();
    }

    private int tranctionFun(Transaction transaction,YText type){
        int res = 0;
        Item start = (Item)(type._start);
        Item end = type._start;
        Map<String,Object> startAttributes = new HashMap<>();
        Map<String,Object> currentAttributes = new HashMap<>();
        while(null != end){
            if(!end.deleted()){
                if(end.content.getClass() == ContentFormat.class){
                    updateCurrentAttributes(currentAttributes, (ContentFormat)end.content);
                    break;
                }
                res += cleanupFormattingGap(transaction,start,end,startAttributes,currentAttributes);
                resCount.getAndSet(res);
                startAttributes = currentAttributes;
                start = end;
                break;
            }
            end = (Item)end.right;
        }
        return res;
    }

    private ItemTextListPosition findPosition(Transaction transaction,AbstractType parent,int index) throws Exception {
        HashMap<String, Object> currentAttributes = new HashMap<>();
        ArraySearchMarker marker = findMarker(parent, index);
        if (marker != null) {
            ItemTextListPosition pos =
                new ItemTextListPosition((Item)marker.p.left, marker.p, marker.index, currentAttributes);
            return pos.findNextPosition(transaction, pos,index - marker.index);
        } else {
            ItemTextListPosition pos = new ItemTextListPosition(null, _start, 0, currentAttributes);
            return pos.findNextPosition(transaction,pos,index);
        }
    }

    private void insertText(Transaction transaction,AbstractType parent, ItemTextListPosition currPos, Object text,
        Map<String, Object> attributes) throws Exception {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        for (Map.Entry<String, Object> entry : currPos.currentAttributes.entrySet()) {
            if (!attributes.containsKey(entry.getKey())) {
                attributes.put(entry.getKey(), null);
            }
        }
        YDoc doc = transaction.getDoc();
        Long ownClientId = doc.clientId;
        currPos.minimizeAttributeChanges(currPos,attributes);
        Map<String, Object> negatedAttributes = insertAttributes(transaction,parent,currPos, attributes);

        // Insert content.
        AbstractContent content;
        if (text instanceof String) {
            String s = (String)text;
            content = new ContentString(s);
        } else {
            if(text instanceof AbstractType){
                content = new ContentType((AbstractType) text);
            }else{
                content = new ContentEmbed(text);
            }
        }
        if (null != parent._searchMarker) {
            updateMarkerChanges(parent._searchMarker, currPos.index, content.length());
        }
        Item right = new Item(new Id(ownClientId, StructStore.getState(doc.getStore(),ownClientId)), currPos.left,
            currPos.left != null ? currPos.left.lastId() : null, currPos.right,
            currPos.right != null ? currPos.right.getId() : null, parent, null, content);
        right.integrate(transaction, 0);
        currPos.right = right;
        currPos.forward();
        currPos.insertNegatedAttributes(transaction, parent,currPos, negatedAttributes);
    }

    private void formatText(Transaction transaction, AbstractType parent,ItemTextListPosition curPos, int length,
        Map<String, Object> attributes) throws Exception {
        YDoc doc = transaction.getDoc();
        Long ownClientId = doc.clientId;
        curPos.minimizeAttributeChanges(curPos,attributes);
        Map<String, Object> negatedAttributes = insertAttributes(transaction,parent, curPos, attributes);

        // Iterate until first non-format or null is found.
        // Delete all formats with attributes[format.Key] != null
        iterationLoop:while (curPos.right != null &&
                (length > 0 ||
                        (negatedAttributes.size() > 0 &&
                                (curPos.right.deleted() || curPos.right.content instanceof ContentFormat)))) {
            if (!curPos.right.deleted()) {
                AbstractContent content = curPos.right.content;
                if (content instanceof ContentFormat) {
                    ContentFormat cf = (ContentFormat)content;
                    if (attributes.containsKey(cf.getKey())) {
                        Object attr = attributes.get(cf.getKey());
                        if (equalAttrs(attr, cf.getValue())) {
                            negatedAttributes.remove(cf.getKey());
                        } else {
                            if(length == 0){
                                break iterationLoop;
                            }
                            negatedAttributes.put(cf.getKey(), cf.getValue());
                        }
                        curPos.right.delete(transaction);
                    }else{
                        curPos.currentAttributes.put(cf.getKey(), cf.getValue());
                    }
                } else {
                    if (length < curPos.right.getLength()) {
                        Id id = curPos.right.getId();
                        StructStore.getItemCleanStart(transaction, new Id(id.getClient(), id.getClock() + length));
                    }
                    length -= curPos.right.getLength();
                }
            }
            curPos.forward();
        }

        // Quill just assumes that the editor starts with a newline and that it always
        // ends with a newline. We only insert that newline when a new newline is
        // inserted - i.e. when length is bigger than type.length.
        if (length > 0) {
            StringBuilder newLines = new StringBuilder();
            for (; length > 0; length--) {
                newLines.append('\n');
            }
            curPos.right = new Item(new Id(ownClientId, StructStore.getState(doc.getStore(),ownClientId)), curPos.left,
                curPos.left != null ? curPos.left.lastId() : null, curPos.right,
                curPos.right != null ? curPos.right.getId() : null, parent, null, new ContentString(newLines.toString()));
            curPos.right.integrate(transaction, 0);
            curPos.forward();
        }
        curPos.insertNegatedAttributes(transaction, parent,curPos, negatedAttributes);
    }

    private Map<String, Object> insertAttributes(Transaction transaction, AbstractType parent,ItemTextListPosition currPos,
        Map<String, Object> attributes) throws Exception {
        YDoc doc = transaction.getDoc();
        Long ownClientId = doc.clientId;
        Map<String, Object> negatedAttributes = new HashMap<>();
        // Insert format-start items.
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            Object currentVal = currPos.currentAttributes.get(key);

            if (!equalAttrs(currentVal, value)) {
                // Save negated attribute (set null if currentVal is not set).
                negatedAttributes.put(key, currentVal);
                currPos.right = new Item(new Id(ownClientId, StructStore.getState(doc.getStore(),ownClientId)), currPos.left,
                    currPos.left != null ? currPos.left.lastId() : null, currPos.right,
                    currPos.right != null ? currPos.right.getId() : null, parent, null, new ContentFormat(key, value));
                currPos.right.integrate(transaction, 0);
                currPos.forward();
            }
        }
        return negatedAttributes;
    }

    private void cleanupContextlessFormattingGap(Transaction transaction, Item item) {
        // Iterate until item.Right is null or content.
        while (item != null && item.right != null && (item.right.deleted() || (
            !(((Item)item.right).content instanceof ContentString)
                && !(((Item)item.right).content instanceof ContentEmbed)))) {
            item = (Item)item.right;
        }

        HashSet<Object> attrs = new HashSet<>();

        // Iterate back until a content item is found.
        while (item != null && (item.deleted() || (!(item.content instanceof ContentString)
            && !(item.content instanceof ContentEmbed)))) {
            if (!item.deleted() && item.content instanceof ContentFormat) {

                String key = ((ContentFormat)item.content).getKey();
                if (attrs.contains(key)) {
                    item.delete(transaction);
                } else {
                    attrs.add(key);
                }
            }
            item = (Item)item.left;
        }
    }

//    public int cleanupFormatting() throws Exception {
//        final MutableInteger res = new MutableInteger();
//        YText yText = new YText();
//        Transaction.transact(this.doc,yText.getClass(),transaction -> {
//            Item start = _start;
//            Item end = _start;
//            HashMap<String, Object> startAttributes = new HashMap<>();
//            HashMap<String, Object> currentAttributes = new HashMap<>();
//            while (end != null) {
//                if (!end.deleted()) {
//                    AbstractContent content = end.content;
//                    if (content instanceof ContentFormat) {
//                        updateCurrentAttributes(currentAttributes, (ContentFormat)content);
//                    } else if (content instanceof ContentEmbed || content instanceof ContentString) {
//                        res.incr(cleanupFormattingGap(transaction, start, end, startAttributes, currentAttributes));
//                        startAttributes = new HashMap<>(currentAttributes);
//                        start = end;
//                    }
//                }
//                end = (Item)end.right;
//            }
//        });
//        return res.get();
//    }

    private ItemTextListPosition deleteText(Transaction transaction, ItemTextListPosition curPos, int length)
        throws Exception {
        int startLength = length;
        Map<String, Object> startAttrs = new HashMap<>(curPos.currentAttributes);
        Item start = curPos.right;

        while (length > 0 && curPos.right != null) {
            if (!curPos.right.deleted()) {
                AbstractContent content = curPos.right.content;
                if (content instanceof ContentString) {
                    if (length < curPos.right.getLength()) {
                        Id id = curPos.right.getId();
                        StructStore.getItemCleanStart(transaction, new Id(id.getClient(), id.getClock() + length));
                    }
                    length -= curPos.right.getLength();
                    curPos.right.delete(transaction);
                }
            }
            curPos.forward();
        }

        if (start != null) {
            cleanupFormattingGap(transaction, start, curPos.right, startAttrs, new HashMap<>(curPos.currentAttributes));
        }
        AbstractType parent = null;
        if (curPos.left != null) {
            parent = (AbstractType)curPos.left.parent;
        } else if (curPos.right != null) {
            parent = (AbstractType)curPos.right.parent;
        }
        if (parent != null && parent._searchMarker != null) {
            updateMarkerChanges(parent._searchMarker, curPos.index, -startLength + length);
        }
        return curPos;
    }

    private int cleanupFormattingGap(Transaction transaction, Item start, Item curr, Map<String, Object> startAttributes,
        Map<String, Object> currAttributes) {
        Item end = curr;
        Map<String,Object> endAttributes = new HashMap<>(currAttributes);
        while (end != null && (!end.countable() || end.deleted())) {
            if (!end.deleted() && end.content instanceof ContentFormat) {
                updateCurrentAttributes(endAttributes, (ContentFormat)end.content);
            }
            end = (Item)end.right;
        }

        int cleanups = 0;
        Boolean reachedEndOfCurr = false;
        while (start != end) {
            if(curr == start){
                reachedEndOfCurr = true;
            }
            if (!start.deleted()) {
                AbstractContent content = start.content;
                if (content instanceof ContentFormat) {
                    ContentFormat cf = (ContentFormat)content;
                    Object endVal = endAttributes.get(cf.getKey());
                    Object startVal = startAttributes.get(cf.getKey());
                    if (endVal != cf.getValue() || startVal == cf.getValue()) {
                        // Either this format is overwritten or it is not necessary because the attribute already existed.
                        start.delete(transaction);
                        cleanups++;
                        if(!reachedEndOfCurr && currAttributes.get(cf.getKey()) == cf.getValue() && startVal != cf.getValue()){
                            currAttributes.remove(cf.getKey());
                        }
                    }
                }
            }
            start = (Item)start.right;
        }
        return cleanups;
    }

    public static boolean equalAttrs(Object attr1, Object attr2) {
        return Objects.equals(attr1, attr2);
    }

    public static void updateCurrentAttributes(Map<String, Object> attributes, ContentFormat format) {
        if (format.getValue() == null) {
            attributes.remove(format.getKey());
        } else {
            attributes.put(format.getKey(), format.getValue());
        }
    }

    public static YText readText(IUpdateDecoder decoder) {
        return new YText();
    }

    private class ItemTextListPosition {
        private Item left;
        private Item right;
        private int index;
        private Map<String, Object> currentAttributes;

        public ItemTextListPosition(Item left, Item right, int index, Map<String, Object> currentAttributes) {
            this.left = left;
            this.right = right;
            this.index = index;
            this.currentAttributes = currentAttributes;
        }

        public void forward() throws Exception {
            if (this.right == null) {
                throw new Exception("Unexpected");
            }
            AbstractContent content = right.content;
            if (content instanceof ContentFormat) {
                if (!right.deleted()) {
                    updateCurrentAttributes(this.currentAttributes, (ContentFormat)content);
                }
            }else{
                if (!right.deleted()) {
                    this.index += right.getLength();
                }
            }
            left = right;
            right = (Item)right.right;
        }

        public ItemTextListPosition findNextPosition(Transaction transaction,ItemTextListPosition pos,int count) throws Exception {
            while (pos.right != null && count > 0) {
                AbstractContent content = pos.right.content;
                if (content instanceof ContentFormat) {
                    if (!pos.right.deleted()) {
                        updateCurrentAttributes(pos.currentAttributes, (ContentFormat)pos.right.content);
                    }
                } else {
                    if (!pos.right.deleted()) {
                        Integer rightLength = pos.right.getLength();
                        if (count < rightLength) {
                            // Split right.
                            StructStore.getItemCleanStart(transaction,
                                new Id(pos.right.getId().getClient(), pos.right.getId().getClock() + count));
                        }
                        pos.index += rightLength;
                        count -= rightLength;
                    }
                }

                pos.left = pos.right;
                pos.right = (Item)pos.right.right;
                // We don't forward() because that would halve the performance because we already do the checks above.
            }
            return pos;
        }

        public void insertNegatedAttributes(Transaction transaction,AbstractType parent,ItemTextListPosition currPos,
            Map<String, Object> negatedAttributes) throws Exception {
            // Check if we really need to remove attributes.
            while (currPos.right != null && (currPos.right.deleted() || insertNegatedAttributesCheck(negatedAttributes,currPos))) {
                if (!currPos.right.deleted()) {
                    negatedAttributes.remove(((ContentFormat)currPos.right.content).getKey());
                }
                forward();
            }

            YDoc doc = transaction.getDoc();
            Long ownClientId = doc.clientId;
            for (Map.Entry<String, Object> entry : negatedAttributes.entrySet()) {
                Item left = currPos.left;
                Item right = currPos.right;
                String key = entry.getKey();
                Object value = entry.getValue();
                Item nextFormat = new Item(new Id(ownClientId, StructStore.getState(doc.getStore(),ownClientId)), left,
                    left != null ? left.lastId() : null, right, right != null ? right.getId() : null, parent, null,
                    new ContentFormat(key, value));
                nextFormat.integrate(transaction, 0);
                currPos.right = nextFormat;
                currPos.forward();
//                currentAttributes.put(key, value);
//                updateCurrentAttributes(currentAttributes, (ContentFormat)left.content);
            }
        }

        public void minimizeAttributeChanges(ItemTextListPosition currPos,Map<String, Object> attributes) throws Exception {
            // Go right while attributes[right.Key] == right.Value (or right is deleted).
            while (currPos.right != null) {
                if (currPos.right.deleted() || minimizeAttributeChangesCheck(currPos,attributes)) {
                    forward();
                } else {
                    break;
                }
            }
        }

        private boolean insertNegatedAttributesCheck(Map<String, Object> negatedAttributes,ItemTextListPosition currPos) {
            if (currPos.right.content instanceof ContentFormat) {
                ContentFormat cf = (ContentFormat)currPos.right.content;
                String cfKey = cf.getKey();
                return negatedAttributes.containsKey(cfKey) && equalAttrs(negatedAttributes.get(cfKey), cf.getValue());
            }
            return false;
        }

        private boolean minimizeAttributeChangesCheck(ItemTextListPosition currPos,Map<String, Object> attributes) {
            if (currPos.right.content instanceof ContentFormat) {
                ContentFormat cf = (ContentFormat)currPos.right.content;
                Object val = attributes.get(cf.getKey());
                return equalAttrs(val, cf.getValue());
            }
            return false;
        }

    }

    public class YTextChangeAttributes {
        private int type;
        private int user;
        private int state;

        public YTextChangeAttributes(int type, int user, int state) {
            this.type = type;
            this.user = user;
            this.state = state;
        }

        public YTextChangeAttributes(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public int getUser() {
            return user;
        }

        public void setUser(int user) {
            this.user = user;
        }

        public int getState() {
            return state;
        }

        public void setState(int state) {
            this.state = state;
        }
    }

}
