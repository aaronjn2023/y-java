package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import com.classpod.crdt.yjava.structs.Item;
import com.classpod.crdt.yjava.types.AbstractType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class RelativePosition {
    public Id type;
    public String tname;
    public Id item;
    public Integer assoc;

    public RelativePosition(Id type, String tname, Id item, Integer assoc) {
        this.type = type;
        this.tname = tname;
        this.item = item;
        this.assoc = assoc;
    }

    public static RelativePosition createRelativePosition(AbstractType type, Id item, Integer assoc) throws Exception {
        Id typeId = null;
        String tname = null;
        if (type._item == null) {
            tname = Id.findRootTypeKey(type);
        } else {
            typeId = new Id(type._item.getId().getClient(), type._item.getId().getClock());
        }
        return new RelativePosition(typeId, tname, item, assoc);
    }

    public static RelativePosition createRelativePositionFromTypeIndex(AbstractType type, Integer index, Integer assoc) throws Exception {
        Item t = type._start;
        if (assoc < 0) {
            if (index == 0) {
                return createRelativePosition(type, null, assoc);
            }
            index--;
        }
        while (t != null) {
            if (!t.deleted() && t.countable()) {
                if (t.getLength() > index) {
                    return createRelativePosition(type, new Id(t.getId().getClient(), t.getId().getClock() + index), assoc);
                }
                index -= t.getLength();
            }
            if (t.right == null && assoc < 0) {
                return createRelativePosition(type, t.lastId(), assoc);
            }
            t = (Item) t.right;
        }
        return createRelativePosition(type, null, assoc);
    }


    public void writeRelativePosition(ByteArrayOutputStream stream) throws Exception {
        if (item != null) {
            StreamEncodingExtensions.writeVarUint(stream, 0L);
            item.write(stream);
        } else if (tname != null) {
            StreamEncodingExtensions.writeVarUint( stream, 1L);
            StreamEncodingExtensions.writeVarString(stream, tname);
        } else if (type != null) {
            StreamEncodingExtensions.writeVarUint(stream, 2L);
            type.write(stream);
        } else {
            throw new Exception("unexpectedCase");
        }
        StreamEncodingExtensions.writeVarInt(stream, assoc);
    }

    public static byte[] encodeRelativePosition(RelativePosition rpos) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        rpos.writeRelativePosition(stream);
        return stream.toByteArray();
    }
}
