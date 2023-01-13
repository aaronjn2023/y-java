package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamDecodingExtensions;
import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import com.classpod.crdt.yjava.types.AbstractType;

import java.io.*;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/09/16 17:54 PM
 **/
public class Id {
    private Long client;

    private Long clock;

    public Id(Long client, Long clock) {
        this.client = client;
        this.clock = clock;
    }

    public void setClient(Long client) {
        this.client = client;
    }

    public Long getClient() {
        return this.client;
    }

    public void setClock(Long clock) {
        this.clock = clock;
    }

    public Long getClock() {
        return this.clock;
    }

    public static boolean compareIds(Id a, Id b) {
        return (a == null && b == null) || (a != null && b != null && a.getClient().equals(b.getClient()) && a.getClock().equals(b.getClock()));
    }

    public void write(ByteArrayOutputStream stream) {
        StreamEncodingExtensions.writeVarUint(stream, this.client);
        StreamEncodingExtensions.writeVarUint(stream, this.clock);
    }

    public static Id read(ByteArrayInputStream stream) {
        long client = StreamDecodingExtensions.readVarUInt(stream);
        long clock = StreamDecodingExtensions.readVarUInt(stream);
        return new Id(client, clock);
    }

    public static String findRootTypeKey(AbstractType type) throws Exception {
        for (String key : type.doc.share.keySet()) {
            if (type.doc.share.get(key).equals(type)) {
                return key;
            }
        }
        throw new Exception("not findRootTypeKey");
    }
}

