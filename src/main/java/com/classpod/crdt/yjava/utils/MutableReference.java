package com.classpod.crdt.yjava.utils;

import java.util.Objects;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/23 11:31 AM
 **/
public class MutableReference<V> {

    private V value;

    public V get() {
        return value;
    }

    public void set(V value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MutableReference))
            return false;
        MutableReference<?> that = (MutableReference<?>)o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
