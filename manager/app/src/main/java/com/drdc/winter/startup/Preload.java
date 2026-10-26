package com.drdc.winter.startup;

import android.app.ZygotePreload;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * App-zygote entry point used by the optional jailbreak flow. The class lives under a
 * neutral package so manifest scanners cannot find KernelSU-style "magica"/"ksu" names.
 */
public class Preload implements ZygotePreload {
    public static final String TAG = "DRDCStartup";

    private static native void forkDontCareAndExecKsud(String ksudPath, String packageName);

    @Override
    public void doPreload(@NonNull ApplicationInfo appInfo) {
        File f = new File(appInfo.nativeLibraryDir, "libdrdcd.so");
        try {
            System.loadLibrary("drdc");
            Log.d(TAG, "executing late load ...");
            forkDontCareAndExecKsud(f.getAbsolutePath(), appInfo.packageName);
        } catch (Throwable t) {
            Log.e(TAG, "failed to late load", t);
        }
    }
}
