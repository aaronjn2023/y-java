package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.yjava.structs.AbstractStruct;
import com.classpod.crdt.yjava.structs.Item;
import com.classpod.crdt.yjava.types.AbstractType;
import com.classpod.crdt.yjava.types.YArray;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/21 17:12 PM
 **/
public class UndoManager {

    private List<AbstractType> scope;
    private Predicate<Item> deleteFilter;
    private Set<Object> trackedOrigins;
    private Deque<StackItem> undoStack;
    private Deque<StackItem> redoStack;

    // Whether the client is currently undoing (calling UndoManager.Undo()).
    private boolean undoing;
    private boolean redoing;
    private YDoc doc;
    private long lastChange;
    private int captureTimeout;

    public EventHandler stackItemAdded;
    public EventHandler stackItemPopped;

    private static final int OPERATION_TYPE_UNDO = 1;
    private static final int OPERATION_TYPE_REDO = 2;

    public UndoManager(AbstractType typeScope) {
        this(Stream.of(typeScope).collect(Collectors.toList()), 500, any -> true,
            Stream.of((Object)null).collect(Collectors.toSet()));
    }

    public UndoManager(List<AbstractType> typeScopes, int captureTimeout, Predicate<Item> deleteFilter,
        Set<Object> trackedOrigins) {
        this.scope = typeScopes;
        this.deleteFilter = deleteFilter == null ? any -> true : deleteFilter;
        this.trackedOrigins = trackedOrigins == null ? new HashSet<>() : trackedOrigins;
        this.trackedOrigins.add(this);
        this.undoStack = new ArrayDeque<>();
        this.redoStack = new ArrayDeque<>();
        this.undoing = false;
        this.redoing = false;
        this.doc = typeScopes.get(0).doc;
        this.lastChange = 0;
        this.captureTimeout = captureTimeout;
        EventHandler eventHandler = new EventHandler();
        try {
            eventHandler.addEventHandlerListener(UndoManager.class.getMethod("onAfterTransaction"));
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        this.doc.afterTransaction = eventHandler;
    }

    public int size() {
        return undoStack.size();
    }

    public void clear() throws Exception {
        doc.transact(tr -> {
            for (StackItem stackItem : undoStack) {
                DeleteSet.iterateDeletedStructs(tr,stackItem.deleteSet, i -> {
                    if (i instanceof Item) {
                        Item item = (Item)i;
                        if (scope.stream().anyMatch(type -> isParentOf(type, item))) {
                            item.keepItemAndParents(true);
                        }
                    }
                    return true;
                });
            }
            for (StackItem stackItem : redoStack) {
                DeleteSet.iterateDeletedStructs(tr,stackItem.deleteSet ,i -> {
                    if (i instanceof Item) {
                        Item item = (Item)i;
                        if (scope.stream().anyMatch(type -> isParentOf(type, item))) {
                            item.keepItemAndParents(true);
                        }
                    }
                    return true;
                });
            }
        }, null, true);
        undoStack.clear();
        redoStack.clear();
    }

    public void onAfterTransaction(Transaction transaction) throws Exception {
        // Only track certain transactions.
        Map<AbstractType, List<YEvent>> changedParentTypes = transaction.changedParentTypes;
        if (!scope.stream().anyMatch(changedParentTypes::containsKey) || (!trackedOrigins.contains(transaction.origin)
            && (transaction.origin == null || !trackedOrigins.stream()
            .anyMatch(to -> to.getClass().getTypeName().equals(transaction.origin.getClass().getTypeName()))))) {
            return;
        }

        Deque<StackItem> stack = undoing ? redoStack : undoStack;

        if (undoing) {
            // Next undo should not be appended to last stack item.
            stopCapturing();
        } else if (!redoing) {
            // Neither undoing nor redoing: delete redoStack.
            redoStack.clear();
        }

        Map<Long, Long> beforeState = transaction.beforeState;
        Map<Long, Long> afterState = transaction.afterState;

        long now = System.currentTimeMillis();
        if ((now - lastChange) < captureTimeout && stack.size() > 0 && !undoing && !redoing) {
            // Append change to last stack op.
            StackItem lastOp = stack.peek();
            ArrayList<DeleteSet> list = new ArrayList<>();
            list.add(lastOp.deleteSet);
            list.add(transaction.deleteSet);
            lastOp.deleteSet = new DeleteSet(list);
            lastOp.afterState = afterState;
        } else {
            // Create a new stack op.
            StackItem item = new StackItem(transaction.deleteSet, beforeState, afterState);
            stack.push(item);
        }

        if (!undoing && !redoing) {
            lastChange = now;
        }

        // Make sure that deleted structs are not GC'd.
        DeleteSet.iterateDeletedStructs(transaction,transaction.deleteSet, i -> {
            if (i instanceof Item) {
                Item item = (Item)i;
                if (scope.stream().anyMatch(type -> isParentOf(type, item))) {
                    item.keepItemAndParents(true);
                }
            }
            return true;
        });
        if (stackItemAdded != null) {
            EventHandler.callEventHandlerListeners(stackItemAdded, this,
                new StackEventArgs(stack.peek(), undoing ? OPERATION_TYPE_REDO : OPERATION_TYPE_UNDO,
                    transaction.changedParentTypes, transaction.origin));
        }
    }

    private static boolean isParentOf(AbstractType parent, Item child) {
        while (child != null) {
            if (child.parent == parent) {
                return true;
            }
            child = ((AbstractType)(child.parent))._item;
        }
        return false;
    }

    /// <summary>
    /// UndoManager merges Undo-StackItem if they are created within time-gap
    /// smaller than 'captureTimeout'. Call this method so that the next StackItem
    /// won't be merged.
    /// </summary>
    public void stopCapturing() {
        this.lastChange = 0L;
    }

    /// <summary>
    /// Undo last changes on type.
    /// </summary>
    /// <returns>
    /// Returns stack item if a change was applied.
    /// </returns>
    public StackItem undo() throws Exception {
        undoing = true;
        StackItem res;
        try {
            res = popStackItem(undoStack, OPERATION_TYPE_UNDO);
        } finally {
            undoing = false;
        }
        return res;
    }

    /// <summary>
    /// Redo last changes on type.
    /// </summary>
    /// <returns>
    /// Returns stack item if a change was applied.
    /// </returns>
    public StackItem redo() throws Exception {
        redoing = true;
        StackItem res;
        try {
            res = popStackItem(redoStack, OPERATION_TYPE_REDO);
        } finally {
            redoing = false;
        }
        return res;
    }

    private StackItem popStackItem(Deque<StackItem> stack, int eventType) throws Exception {
        final MutableReference<StackItem> result = new MutableReference<>();
        // Keep a reference to the transaction so we can fire the event with the 'changedParentTypes'.
        final MutableReference<Transaction> tr = new MutableReference<>();
        doc.transact(transaction -> {
            tr.set(transaction);
            while (stack.size() > 0 && result.get() == null) {
                StackItem stackItem = stack.pop();
                HashSet<Item> itemsToRedo = new HashSet<>();
                ArrayList<Item> itemsToDelete = new ArrayList<>();
                boolean performedChange = false;
                for (Map.Entry<Long, Long> entry : stackItem.afterState.entrySet()) {
                    Long client = entry.getKey();
                    Long endClock = entry.getValue();
                    Long startClock = stackItem.beforeState.getOrDefault(client, 0L);
                    Long len = endClock - startClock;
                    List<AbstractStruct> structs = doc.getStore().getClients().get(client);
                    if (!startClock.equals(endClock)) {
                        // Make sure structs don't overlap with the range of created operations [stackItem.start, stackItem.start + stackItem.end).
                        // This must be executed before deleted structs are iterated.
                        StructStore.getItemCleanStart(doc.getStore(), transaction, new Id(client, startClock));

                        if (endClock < StructStore.getState(doc.getStore(),client)) {
                            StructStore.getItemCleanStart(doc.getStore(), transaction, new Id(client, endClock));
                        }

                        StructStore.iterateStructs(transaction, structs, startClock, len, str -> {
                            if (str instanceof Item) {
                                try {
                                    Item it = (Item)str;
                                    if (it.redone != null) {
                                        Object[] redoneResult = doc.getStore().followRedone(str.getId());
                                        Integer diff = (Integer)redoneResult[0];
                                        AbstractStruct item = (AbstractStruct)redoneResult[1];

                                        if (diff > 0) {
                                            item = StructStore.getItemCleanStart(doc.getStore(), transaction,
                                                new Id(item.getId().getClient(), item.getId().getClock() + diff));
                                        }

                                        if (item.getLength() > len) {
                                            StructStore.getItemCleanStart(doc.getStore(), transaction,
                                                new Id(item.getId().getClient(), endClock));
                                        }

                                        it = (Item)item;
                                    }
                                    Item finalIt = it;
                                    if (!it.deleted() && scope.stream().anyMatch(type -> isParentOf(type, finalIt))) {
                                        itemsToDelete.add(it);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            return true;
                        });
                    }
                }

                DeleteSet.iterateDeletedStructs(transaction,transaction.deleteSet, str -> {
                    Id id = str.getId();
                    Long clock = id.getClock();
                    Long client = id.getClient();
                    Long startClock = stackItem.beforeState.getOrDefault(client, 0L);
                    Long endClock = stackItem.afterState.getOrDefault(client, 0L);
                    if (str instanceof Item) {
                        Item item = (Item)str;
                        if (scope.stream().anyMatch(type -> isParentOf(type, item)) && !(clock >= startClock
                            && clock < endClock)) {
                            itemsToRedo.add(item);
                        }
                    }
                    return true;
                });
                for (Item str : itemsToRedo) {
                    performedChange |= transaction.redoItem(str, itemsToRedo) != null;
                }

                // We want to delete in reverse order so that children are deleted before
                // parents, so we have more information available when items are filtered.
                for (int i = itemsToDelete.size() - 1; i >= 0; i--) {
                    Item item = itemsToDelete.get(i);
                    if (deleteFilter.test(item)) {
                        item.delete(transaction);
                        performedChange = true;
                    }
                }
                //TODO 确定不用判断performedChange？
                result.set(stackItem);
            }
            for (Map.Entry<AbstractType, Set<String>> kvp : transaction.changed.entrySet()) {
                AbstractType type = kvp.getKey();
                Set<String> subProps = kvp.getValue();

                // Destroy search marker if necessary.
                if (subProps.contains(null) && type instanceof YArray) {
                    YArray arr = (YArray)type;
                    arr.clearSearchMarkers();
                }
            }
        }, this, true);

        if (result.get() != null && stackItemPopped != null) {
            EventHandler.callEventHandlerListeners(stackItemPopped, this,
                new StackEventArgs(result.get(), eventType, tr.get().changedParentTypes, tr.get().origin));
        }

        return result.get();
    }

    public class StackEventArgs {

        private StackItem stackItem;
        private Integer operationType;
        private Map<AbstractType, List<YEvent>> changedParentTypes;
        private Object origin;

        public StackEventArgs(StackItem item, Integer operationType, Map<AbstractType, List<YEvent>> changedParentTypes,
            Object origin) {
            this.stackItem = item;
            this.operationType = operationType;
            this.changedParentTypes = changedParentTypes;
            this.origin = origin;
        }

        public StackItem getStackItem() {
            return stackItem;
        }

        public Integer getOperationType() {
            return operationType;
        }

        public Map<AbstractType, List<YEvent>> getChangedParentTypes() {
            return changedParentTypes;
        }

        public Object getOrigin() {
            return origin;
        }
    }
}
