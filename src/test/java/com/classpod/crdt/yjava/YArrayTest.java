package com.classpod.crdt.yjava;

import com.classpod.crdt.yjava.utils.Encoding;
import com.classpod.crdt.yjava.utils.YDoc;
import com.classpod.crdt.yjava.utils.YDocOptions;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/12/14 8:53 PM
 **/
public class YArrayTest {

    @Test
    public void testBasicUpdate() throws Exception{
        YDoc doc1 = new YDoc(new YDocOptions());
        YDoc doc2 = new YDoc(new YDocOptions());
        List<Object> list = new ArrayList<>();
        list.add("hi");
        doc1.getArray("array").insert(0,list);
        byte[] encodedTargetStateVector = new byte[0];
        byte[] update = Encoding.encodeStateAsUpdate(doc1,encodedTargetStateVector);
        Encoding.applyUpdate(doc2,update,"");
        assertEquals(doc2.getArray("array").toArray(),list);
    }
}
