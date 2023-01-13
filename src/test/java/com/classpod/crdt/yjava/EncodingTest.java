package com.classpod.crdt.yjava;

import com.classpod.crdt.yjava.utils.PermanentUserData;
import com.classpod.crdt.yjava.utils.YDoc;
import com.classpod.crdt.yjava.utils.YDocOptions;
import org.testng.annotations.Test;

/**
 * xxx
 *
 * @Author jiquanwei
 * @Date 2022/12/20 5:01 PM
 **/
public class EncodingTest extends BaseTest{

    @Test
    public void testPermanentUserData() throws Exception{
        YDoc ydoc1 = new YDoc(new YDocOptions());
        YDoc ydoc2 = new YDoc(new YDocOptions());
        PermanentUserData pd1 = new PermanentUserData(ydoc1);
        PermanentUserData pd2 = new PermanentUserData(ydoc2);

    }
}
