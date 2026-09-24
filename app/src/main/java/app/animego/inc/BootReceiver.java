package app.animego.inc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import app.animego.inc.db.DatabaseHelper;
import app.animego.inc.download.DownloadService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;

        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        try {
            if (!db.hasPendingDownloads()) return;
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(new Intent(context, DownloadService.class));
            else context.startService(new Intent(context, DownloadService.class));
        } finally {
            db.close();
        }
    }
}
