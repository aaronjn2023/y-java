package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.y.lib.StreamDecodingExtensions;

import java.io.ByteArrayInputStream;

/**
 * test
 *
 * @Author jiquanwei
 * @Date 2022/12/28 17:40 PM
 **/
public class Test {

    public static void main(String[] args) {
        byte[] bytes = new byte[]{-65,-57,-99,-92,14,0,-124,37,1,1,97,0};
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        ByteArrayInputStream inputStream2 = new ByteArrayInputStream(bytes);

        System.out.println(StreamDecodingExtensions.readVarUInt(inputStream));
        System.out.println((int)3834078143L);
        System.out.println(StreamDecodingExtensions.readVarUIntLong(inputStream2) & 0xffffffffL);

    }





}
