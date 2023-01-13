package com.classpod.crdt.yjava;

import com.classpod.crdt.y.lib.ByteToUnIntUtil;
import com.classpod.crdt.y.lib.StreamEncodingExtensions;
import com.classpod.crdt.yjava.types.YArray;
import com.classpod.crdt.yjava.types.YArrayEvent;
import com.classpod.crdt.yjava.types.YMap;
import com.classpod.crdt.yjava.utils.Encoding;
import com.classpod.crdt.yjava.utils.Transaction;
import com.classpod.crdt.yjava.utils.YDoc;
import com.classpod.crdt.yjava.utils.YDocOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/12/12 3:38 PM
 **/
public class YDocTest extends BaseTest{
    static final Logger logger = LoggerFactory.getLogger(YDocTest.class);

    @Test
    public void testClientIdDuplicateChange() throws Exception{
        YDoc doc1 = new YDoc(new YDocOptions());
        doc1.clientId = 0L;
        YDoc doc2 = new YDoc(new YDocOptions());
        doc2.clientId = 0L;
        assertEquals(doc2.clientId,doc1.clientId);
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        doc1.getArray("a").insert(0,list);
        int[] a = {0};
        byte[] encodeStateAsUpdate = Encoding.encodeStateAsUpdate(doc1,ByteToUnIntUtil.unIntToByte(a));
        logger.info("Encoding.encodeStateAsUpdate -res:{}",encodeStateAsUpdate);
        Encoding.applyUpdate(doc2,encodeStateAsUpdate,true);
        assertNotEquals(doc2.clientId,doc1.clientId);
    }

    @Test
    public void testGetTypeEmptyId() throws Exception {
        YDoc doc1 = new YDoc(new YDocOptions());
        doc1.getText("").insert(0,"h",null);
        doc1.getText("").insert(1,"i",null);
        doc1.getText("").insert(2,"l",null);
        YDoc doc2 = new YDoc(new YDocOptions());
        int[] a = {0};
        byte[] encodeStateAsUpdate = Encoding.encodeStateAsUpdate(doc1,ByteToUnIntUtil.unIntToByte(a));
        Encoding.applyUpdate(doc2,encodeStateAsUpdate,true);
        assertEquals(doc2.getText("").toString(),"hil");
//        assertEquals(doc2.getText("''").toString(),"hi");
    }

    @Test
    public void testToJSON() throws Exception{
        YDoc doc = new YDoc(new YDocOptions());
        YArray arr = doc.getArray("array");
        List<Object> list = new ArrayList<>();
        list.add("test1");
        arr.push(list);
        YMap map = doc.getMap("map");
        map.set("k1","v1");
        YMap map2 = new YMap();
        map.set("k2",map2);
        map2.set("m2k1","m2v1");
        System.out.println(doc.toJSON());
//        assertEquals();
    }

}
