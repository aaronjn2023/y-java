package com.classpod.crdt.yjava.dto;

import com.classpod.crdt.yjava.structs.AbstractStruct;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/22 9:57 AM
 **/
public class CurrAbstructWriteDto {
    private AbstractStruct struct;
    private int offset;

    public CurrAbstructWriteDto(AbstractStruct struct,int offset){
        this.struct = struct;
        this.offset = offset;
    }

    public CurrAbstructWriteDto(){
        //
    }

    public AbstractStruct getStruct() {
        return struct;
    }

    public void setStruct(AbstractStruct struct) {
        this.struct = struct;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
