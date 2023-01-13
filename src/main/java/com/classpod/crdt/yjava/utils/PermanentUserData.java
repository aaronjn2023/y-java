package com.classpod.crdt.yjava.utils;

import com.classpod.crdt.yjava.structs.Item;
import com.classpod.crdt.yjava.types.YArray;
import com.classpod.crdt.yjava.types.YArrayEvent;
import com.classpod.crdt.yjava.types.YMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PermanentUserData {
    public YDoc doc;

    public HashMap<String, DeleteSet> dss;

    public YMap yusers;

    public HashMap<Integer, String> clients;

    public PermanentUserData(YDoc doc) throws Exception {
        this.doc = doc;
        this.dss = new HashMap<>();
        this.yusers = doc.getMap("users");
        clients = new HashMap<>();
    }

    public static void todoMethod(PermanentUserData permanentUserData, YArrayEvent event, String userDescription) {
        ChangesCollection changesCollection = event.changes;
        for(Item item : changesCollection.added) {
            for (Object encodedDs : item.content.getContent()) {
                if (encodedDs instanceof byte[]) {
                    List<DeleteSet> deleteSetList = new ArrayList<>();
                    deleteSetList.add(permanentUserData.dss.getOrDefault(userDescription, DeleteSet.createDeleteSet()));
                    permanentUserData.dss.put(userDescription, DeleteSet.mergeDeleteSets(deleteSetList));
                }
            }
        }
    }

    public void initUser(YMap user, String userDescription) throws NoSuchMethodException {
        YArray ds = (YArray) user.get("ds");
        YArray ids = (YArray) user.get("ids");
        ds.observe(PermanentUserData.class.getMethod("todoMethod"));
    }

    public String getUserByClientId (Integer clientId) {
        return this.clients.getOrDefault(clientId, null);
    }

    public String getUserByDeletedId (Id id) {
        for (String userDescription : this.dss.keySet()) {
            DeleteSet ds = this.dss.get(userDescription);
            if (DeleteSet.isDeleted(ds, id)) {
                return userDescription;
            }
        }
        return null;
    }
}
