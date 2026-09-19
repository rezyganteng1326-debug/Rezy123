// language: Java, file: DeviceInfo.java
package com.rat.client;

import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import java.util.UUID;

public class DeviceInfo {

    public static String getDeviceId(Context ctx) {
        String id = Settings.Secure.getString(
            ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null || id.isEmpty())
            id = UUID.randomUUID().toString();
        return id;
    }

    public static String getModel() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public static String getAndroidVersion() {
        return String.valueOf(Build.VERSION.SDK_INT);
    }

    public static int getBattery(Context ctx) {
        IntentFilter ifl = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent bi = ctx.registerReceiver(null, ifl);
        if (bi == null) return -1;
        int level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return (int)(level * 100f / scale);
    }

    public static String getNetworkType(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager)
            ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "unknown";
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) return "offline";
        return ni.getTypeName();
    }
}