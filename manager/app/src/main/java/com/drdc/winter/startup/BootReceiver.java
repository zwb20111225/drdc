package com.drdc.winter.startup;

import static com.drdc.winter.startup.Preload.TAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import me.weishu.kernelsu.ui.util.KsuCliKt;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        var action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"com.drdc.winter.SYNC".equals(action)) {
            return;
        }
        if (KsuCliKt.rootAvailable()) return;
        try {
            context.startService(new Intent(context, SyncService.class));
            Log.i(TAG, "SyncService started from boot action: " + action);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to start SyncService from boot action: " + action, e);
        }
    }
}
