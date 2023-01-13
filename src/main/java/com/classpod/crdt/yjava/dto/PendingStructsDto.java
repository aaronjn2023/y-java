package com.classpod.crdt.yjava.dto;

import java.util.Map;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/17 5:51 PM
 **/

public class PendingStructsDto {
    private Map<Long,Long> missingSV;
    private byte[] uint8Array;

    public Map<Long, Long> getMissingSV() {
        return missingSV;
    }

    public void setMissingSV(Map<Long, Long> missingSV) {
        this.missingSV = missingSV;
    }

    public byte[] getUint8Array() {
        return uint8Array;
    }

    public void setUint8Array(byte[] uint8Array) {
        this.uint8Array = uint8Array;
    }
}
