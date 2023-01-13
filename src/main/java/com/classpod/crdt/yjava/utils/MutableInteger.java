package com.classpod.crdt.yjava.utils;

import java.util.Objects;

public class MutableInteger {
    private int value;

    public MutableInteger(int value) {
        this.value = value;
    }

    public MutableInteger() {
    }

    public int get() {
        return value;
    }

    public void set(int value) {
        this.value = value;
    }

    public void incr() {
        value++;
    }

    public void incr(int num) {
        value += num;
    }

    public void decr() {
        value--;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MutableInteger))
            return false;
        MutableInteger that = (MutableInteger)o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}