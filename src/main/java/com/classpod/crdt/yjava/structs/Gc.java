package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.yjava.utils.IUpdateEncoder;
import com.classpod.crdt.yjava.utils.Id;
import com.classpod.crdt.yjava.utils.StructStore;
import com.classpod.crdt.yjava.utils.Transaction;
import java.io.IOException;

public class Gc extends AbstractStruct{

    public static final byte StructGCRefNumber = 0;

    public Gc(Id id, Integer length) {
        super(id, length);
    }

    @Override
    public boolean mergeWith(AbstractStruct right) {
        if (!right.getClass().equals(Gc.class)) {
            return false;
        }
        this.setLength(this.getLength() + right.getLength());
        return true;
    }

    @Override
    public boolean deleted() {
        return true;
    }

    public void delete(){}

    @Override
    public void integrate(Transaction transaction, int offset) {
        if (offset > 0) {
            this.getId().setClock(this.getId().getClock() + offset);
            this.setLength(this.getLength() - offset);
        }
        StructStore.addStruct(transaction.getDoc().getStore(), this);
    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) throws IOException {
        encoder.writeInfo(StructGCRefNumber);
        encoder.writeLen(this.getLength() - offset);
    }

    @Override
    public Long getMissing (Transaction transaction, StructStore store) {
        return null;
    }
}
