package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.yjava.types.AbstractType;

public class AbsolutePosition {
    public AbstractType type;
    public Integer index;
    public Integer assoc;

    public AbsolutePosition(AbstractType type, Integer index, Integer assoc) {
        this.type = type;
        this.index = index;
        this.assoc = assoc;
    }

    public static AbsolutePosition createAbsolutePosition(AbstractType type, Integer index, Integer assoc) {
        return new AbsolutePosition(type, index, assoc);
    }

}
