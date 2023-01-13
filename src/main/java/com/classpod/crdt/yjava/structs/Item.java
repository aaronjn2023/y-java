package com.classpod.crdt.yjava.structs;

import cn.hutool.core.collection.CollectionUtil;
import com.classpod.crdt.y.lib.Bit;
import com.classpod.crdt.y.lib.BitContanst;
import com.classpod.crdt.yjava.types.AbstractType;
import com.classpod.crdt.yjava.types.ArraySearchMarker;
import com.classpod.crdt.yjava.utils.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Item extends AbstractStruct {

    public AbstractStruct left;
    public Id origin;
    public AbstractStruct right;
    public Id rightOrigin;
    public Object parent;
    public String parentSub;
    public AbstractContent content;
    public Id redone;
    public Integer info;

    public Item(Id id, AbstractStruct left, Id origin, AbstractStruct right, Id rightOrigin, Object parent,
                String parentSub, AbstractContent content) {
        super(id, content.length());
        this.origin = origin;
        this.left = left;
        this.right = right;
        this.rightOrigin = rightOrigin;
        this.parent = parent;
        this.parentSub = parentSub;
        this.redone = null;
        this.content = content;
        this.info = content.countable() ? Bit.Bit2 : 0;
    }

    public Item(Id id, Integer length) {
        super(id, length);
    }

    @Override
    public boolean mergeWith(AbstractStruct right) {
        if (!(right instanceof Item)) {
            return false;
        }
        Item target = (Item) right;
        if (Id.compareIds(target.origin, this.lastId())
                && this.right == target
                && Id.compareIds(this.rightOrigin, target.rightOrigin)
                && this.getId().getClient().equals(target.getId().getClient())
                && (this.getId().getClock() + (long)this.getLength()) == target.getId().getClock()
                && Objects.equals(this.deleted(),target.deleted())
                && this.redone == null
                && target.redone == null
                && this.content.getClass().equals(target.content.getClass())
                && this.content.mergeWith(target.content)
        ) {
            List<ArraySearchMarker> searchMarker = ((AbstractType)this.parent)._searchMarker;
            if(null != searchMarker && searchMarker.size() > 0){
                searchMarker.forEach(marker -> {
                    if(marker.p == target){
                        marker.p = this;
                        if(!this.deleted() && this.countable()){
                            marker.index -= this.getLength();
                        }
                    }
                });
            }
            if (target.keep()) {
                this.setKeep(true);
            }
            this.right = target.right;
            if (this.right != null) {
                ((Item)this.right).left = this;
            }
            this.setLength( this.getLength() + target.getLength()) ;
            return true;
        }
        return false;
    }

    @Override
    public boolean deleted() {
        return (this.info & Bit.Bit3) > 0;
    }

    public void setDeleted(boolean doDelete) {
        if (this.deleted() != doDelete) {
            this.info ^= Bit.Bit3;
        }
    }

    public void markDeleted(){
        this.info |= Bit.Bit3;
    }

    public boolean keep() {
        return (this.info & Bit.Bit1) > 0;
    }

    public void setKeep(boolean doKeep) {
        if (this.keep() != doKeep) {
            this.info ^= Bit.Bit1;
        }
    }

    public boolean countable() {
        return (this.info & Bit.Bit2) > 0;
    }

    public boolean marker() {
        return (this.info & Bit.Bit4) > 0;
    }

    public void setMarker(boolean isMarked) {
        if (((this.info & Bit.Bit4) > 0) != isMarked) {
            this.info ^= Bit.Bit4;
        }
    }

    @Override
    public Long getMissing(Transaction transaction, StructStore store) {
        if(null != this.origin && !this.origin.getClient().equals(this.getId().getClient()) && this.origin.getClock() >= getState(store,this.origin.getClient())){
            return this.origin.getClient();
        }
        if(null != rightOrigin && !this.rightOrigin.getClient().equals(this.getId().getClient()) && this.rightOrigin.getClock() >= getState(store,this.rightOrigin.getClient())){
            return this.rightOrigin.getClient();
        }
        if(null != parent && Objects.equals(this.parent.getClass(),Id.class) && !this.getId().getClient().equals(((Id)this.parent).getClient()) && ((Id)this.parent).getClock() >= getState(store, ((Id) this.parent).getClient())){
            return ((Id)this.parent).getClient();
        }
        if(null != this.origin){
            this.left = getItemCleanEnd(transaction,store,this.origin);
            // 已经解决的bug
            this.origin = ((Item)this.left).lastId();
        }
        if(null != this.rightOrigin){
            this.right = getItemCleanStart(transaction,this.rightOrigin);
            this.rightOrigin = this.right.getId();
        }
        if((null != this.left && this.left.getClass() == Gc.class) ||(null != this.right && this.right.getClass() == Gc.class)){
            this.parent = null;
        }
        if(null == this.parent){
            if(null != this.left && this.left.getClass() == Item.class){
                Item curItem = (Item)this.left;
                this.parent = curItem.parent;
                this.parentSub = curItem.parentSub;
            }
            if(null != this.right && this.right.getClass() == Item.class){
                Item curItem = (Item)this.right;
                this.parent = curItem.parent;
                this.parentSub = curItem.parentSub;
            }
        }else if(this.parent.getClass() == Id.class){
            Id currId = (Id)this.parent;
            AbstractStruct parentItem = getItem(store,currId);
            if(parentItem.getClass() == Gc.class){
                this.parent = null;
            }else{
                this.parent = this.content;
            }
        }
        return null;
    }

    @Override
    public void integrate(Transaction transaction, int offset) {
        if(offset > 0){
            this.getId().setClock(this.getId().getClock() + offset);
            this.left =  getItemCleanEnd(transaction,transaction.getDoc().getStore(), new Id(this.getId().getClient(),this.getId().getClock() - 1));;
            this.origin = ((Item)this.left).lastId();
            this.content = this.content.splice(offset);
            this.setLength(this.getLength() - offset);
        }
        if(null != this.parent){
            if((null == this.left && (null == this.right || ((Item) this.right).left != null)) || (null != this.left && ((Item)this.left).right != this.right)){
                AbstractStruct left = this.left;
                AbstractStruct o;
                if(null != left){
                    o = ((Item)left).right;
                }else if(null != this.parentSub){
                    o = ((AbstractType)this.parent)._map.get(this.parentSub);
                    while (null != o && null != ((Item)o).left){
                        o = ((Item) o).left;
                    }
                }else{
                    o = ((AbstractType)this.parent)._start;
                }
                Set<Item> conflictingItems = new HashSet<>();
                Set<Item> itemsBeforeOrigin = new HashSet<>();
                while(null != o && o != this.right){
                    itemsBeforeOrigin.add((Item)o);
                    conflictingItems.add((Item)o);
                    if(Id.compareIds(this.origin,((Item) o).origin)){
                        if(o.getId().getClient() < this.getId().getClient()){
                            left = o;
                            conflictingItems.clear();
                        }else if(Id.compareIds(this.rightOrigin,((Item) o).rightOrigin)){
                            break;
                        }
                    }else if(null != ((Item) o).origin && itemsBeforeOrigin.contains(getItem(transaction.getDoc().getStore(), ((Item) o).origin))){
                        if(!conflictingItems.contains(getItem(transaction.getDoc().getStore(), ((Item) o).origin))){
                            left = o;
                            conflictingItems.clear();
                        }
                    }else{
                        break;
                    }
                    o = ((Item) o).right;
                }
                this.left = left;
            }
            if(null != this.left){
                AbstractStruct right = ((Item)this.left).right;
                this.right = right;
                ((Item)this.left).right = this;
            }else{
                AbstractStruct r;
                if(null != this.parentSub){
                    r = ((AbstractType) this.parent)._map.get(this.parentSub);
                    while(null != r && null != ((Item)r).left){
                        r = ((Item) r).left;
                    }
                }else{
                    r = ((AbstractType) this.parent)._start;
                    ((AbstractType) this.parent)._start = this;
                }
                this.right = r;
            }
            if(null != this.right){
                ((Item)this.right).left = this;
            }else if(null != this.parentSub){
                ((AbstractType) this.parent)._map.put(this.parentSub,this);
                if(null != this.left){
                    ((Item)this.left).delete(transaction);
                }
            }
            if(null == this.parentSub && this.countable() && !this.deleted()){
                ((AbstractType) this.parent)._length += this.getLength();
            }
            StructStore.addStruct(transaction.getDoc().getStore(), this);
            try {
                this.content.integrate(transaction,this);
                Transaction.addChangedTypeToTransaction(transaction,((AbstractType) this.parent),this.parentSub);
                if(null != ((AbstractType) this.parent)._item && ((AbstractType) this.parent)._item.deleted() ||
                        (null != this.parentSub && null != this.right)){
                    this.delete(transaction);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            new Gc(this.getId(),this.getLength()).integrate(transaction,0);
        }
    }

    public Item next(){
        Item n = (Item)this.right;
        while(null != n && n.deleted()){
            n = (Item)n.right;
        }
        return n;
    }

    public Item prev(){
        Item n = (Item)this.left;
        while(null != n && n.deleted()){
            n = (Item)n.left;
        }
        return n;
    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) throws IOException {
        Id origin = offset > 0 ? new Id(this.getId().getClient(),this.getId().getClock() + offset -1) : this.origin;
        Id rightOrigin = this.rightOrigin;
        String parentSub = this.parentSub;
        int info = (this.content.getRef() & BitContanst.BITS5) | (null == origin ? 0 : BitContanst.BIT8)
                | (null == rightOrigin ? 0 : BitContanst.BIT7) | (null == parentSub ? 0 : BitContanst.BIT6);
        encoder.writeInfo(info);
        if(null != origin){
            encoder.writeLeftID(origin);
        }
        if(null != rightOrigin){
            encoder.writeRightID(rightOrigin);
        }
        try {
            if(null == origin && null == rightOrigin){
                AbstractType parent = (AbstractType)this.parent;
                Item parentItem = parent._item;
                if(null == parentItem){
                    String yKey =  Id.findRootTypeKey(parent);
                    encoder.writeParentInfo(true);
                    encoder.writeString(yKey);
                }else if(null != parentItem && !parent.getClass().equals(String.class) && !parent.getClass().equals(Id.class)){
                    encoder.writeParentInfo(false);
                    encoder.writeLeftID(parentItem.getId());
                }else if(parent.getClass().equals(String.class)){
                    encoder.writeParentInfo(true);
                    encoder.writeString(parent.toString());
                }else if(parent.getClass().equals(Id.class)){
                    encoder.writeParentInfo(false);
                    encoder.writeLeftID(parent._item.getId());
                }else{
                    throw new RuntimeException("item write error,not find class type");
                }
                if(null != parentSub){
                    encoder.writeString(parentSub);
                }
            }
            this.content.write(encoder,offset);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Id lastId() {
        return this.getLength() == 1 ? this.getId() :
                new Id(this.getId().getClient(), this.getId().getClock() + this.getLength() - 1);
    }

    public Item splitItem(Transaction transaction, AbstractStruct struct,long diff) {
        Item leftItem = (Item)struct;
        long client = leftItem.getId().getClient();
        long clock = leftItem.getId().getClock();

        Item rightItem =
                new Item(
                        new Id(client, clock + diff),
                        leftItem,
                        new Id(client, clock + diff - 1),
                        this.right,
                        this.rightOrigin,
                        this.parent,
                        this.parentSub,
                        this.content.splice((int)diff));
        if (leftItem.deleted()) {
            rightItem.markDeleted();
        }
        if (leftItem.keep()) {
            rightItem.setKeep(true);
        }
        if (leftItem.redone != null) {
            rightItem.redone = new Id(leftItem.redone.getClient(), leftItem.redone.getClock() + diff);
        }
        leftItem.right = rightItem;
        if (rightItem.right != null) {
            ((Item) rightItem.right).left = rightItem;
        }
        transaction._mergeStructs.add(rightItem);
        if (rightItem.parentSub != null && rightItem.right == null) {
            ((AbstractType)rightItem.parent)._map.put(rightItem.parentSub,rightItem);
        }
        leftItem.setLength((int)diff);
        return rightItem;
    }

    public void delete(Transaction transaction) {
        if (!this.deleted()) {
            AbstractType parent = (AbstractType)this.parent;
            if (this.countable() && this.parentSub == null) {
                parent._length -= this.getLength();
            }
            this.markDeleted();
            DeleteSet.addToDeleteSet(transaction.deleteSet, this.getId().getClient(), this.getId().getClock(),
                    this.getLength());
            Transaction.addChangedTypeToTransaction(transaction, parent, this.parentSub);
            this.content.delete(transaction);
        }
    }

    public boolean isVisible(Snapshot snap) {
        return snap == null ? !deleted() : snap.getStateVector().containsKey(getId().getClient())
                && snap.getStateVector().get(getId().getClient()) > getId().getClock() && DeleteSet.isDeleted(
                snap.getDeleteSet(), getId());
    }

    public Long getState(StructStore store,Long client){
        List<AbstractStruct> structs = store.getClients().get(client);
        if(CollectionUtil.isEmpty(structs)){
            return 0L;
        }
        AbstractStruct lastStruct = structs.get(structs.size() - 1);
        return lastStruct.getId().getClock() + lastStruct.getLength();
    }

    public void gc(StructStore store, boolean parentGCd) {
        if (!this.deleted()) {
            return;
        }
        this.content.gc(store);
        if (parentGCd) {
            try {
                StructStore.replaceStruct(store, this, new Gc(this.getId(), this.getLength()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            this.content = new ContentDeleted(this.getLength());
        }
    }

    public Item getPrev() {
        Item n = (Item)this.left;
        while (n != null && n.deleted()) {
            n = (Item)n.left;
        }
        return n;
    }

    public void keepItemAndParents(boolean value) {
        Item item = this;
        while (item != null && item.keep() != value) {
            item.setKeep(value);
            item = ((AbstractType)item.parent)._item;
        }
    }



    private AbstractStruct getItem(StructStore store,Id id){
        List<AbstractStruct> structs = store.getClients().get(id.getClient());
        try {
            return structs.get(StructStore.findIndexSS(structs,id.getClock()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private AbstractStruct getItemCleanStart(Transaction transaction,Id id){
        List<AbstractStruct> structs = transaction.getDoc().getStore().getClients().get(id.getClient());
        try {
            return structs.get(StructStore.findIndexCleanStart(transaction,structs,id.getClock()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private AbstractStruct getItemCleanEnd(Transaction transaction,StructStore store,Id id){
        List<AbstractStruct> structs = store.getClients().get(id.getClient());
        AbstractStruct struct = null;
        try {
            int index = StructStore.findIndexSS(structs,id.getClock());
            struct = structs.get(index);
            if(id.getClock() != struct.getId().getClock() + struct.getLength() -1 && struct.getClass() != Gc.class){
                // 添加新元素
                structs.add(index + 1,splitItem(transaction,struct,id.getClock() - struct.getId().getClock() + 1));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return struct;
    }
}
