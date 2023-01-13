package com.classpod.crdt.yjava.utils;

import java.util.HashMap;
import java.util.Map;
/**
 * threadlocal工具类
 *
 * @Author jiquanwei
 * @Date 2022/11/21 11:49 AM
 **/
public class ThreadLocalUtil {

    private static final ThreadLocal<Map<String, Object>> THREAD_LOCAL = ThreadLocal.withInitial(() -> new HashMap<>());

    private ThreadLocalUtil() {
    }

    public static void setCache(String key, Object obj) {
        THREAD_LOCAL.get().put(key, obj);
    }

    public static Object getCache(String key) {
        return THREAD_LOCAL.get().get(key);
    }

    public static Object removeCache(String key) {
        return THREAD_LOCAL.get().remove(key);
    }

    public static void removeAllCache() {
        THREAD_LOCAL.remove();
    }

}