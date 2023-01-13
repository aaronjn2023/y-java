package com.classpod.crdt.yjava.dto;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/11/21 5:33 PM
 **/
public class LazyStructWriterDto {

    private long written;
    private byte[] restEncoder;

    public long getWritten() {
        return written;
    }

    public void setWritten(long written) {
        this.written = written;
    }

    public byte[] getRestEncoder() {
        return restEncoder;
    }

    public void setRestEncoder(byte[] restEncoder) {
        this.restEncoder = restEncoder;
    }
}
