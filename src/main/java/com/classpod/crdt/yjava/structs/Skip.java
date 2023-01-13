package com.classpod.crdt.yjava.structs;

import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import com.classpod.crdt.yjava.utils.*;

import java.io.IOException;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/17 2:27 PM
 **/
public class Skip extends AbstractStruct{
    public Skip(Id id, Integer length) {
        super(id, length);
    }

    @Override
    public boolean mergeWith(AbstractStruct right) {
        if(!right.getClass().equals(Skip.class)){
            return false;
        }
        this.setLength(this.getLength() + right.getLength());;
        return true;
    }

    @Override
    public boolean deleted() {
        return true;
    }

    @Override
    public void integrate(Transaction transaction, int offset) {
        return;
    }

    @Override
    public void write(IUpdateEncoder encoder, int offset) throws IOException {
        encoder.writeInfo(10);
        StreamEncodingExtensions.writeVarUint(encoder.restEncoder(),this.getLength() - offset);
    }

    @Override
    public Long getMissing(Transaction transaction, StructStore store) {
        return null;
    }


}
