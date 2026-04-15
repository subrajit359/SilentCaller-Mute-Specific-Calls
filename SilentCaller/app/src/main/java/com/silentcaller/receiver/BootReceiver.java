package com.silentcaller.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BootReceiver — listens for device boot and locked-boot events.
 *
 * Since IncomingCallReceiver is declared in AndroidManifest.xml with its own
 * intent-filter, Android will automatically re-register it after boot.
 * This BootReceiver exists as an explicit hook for any initialization work
 * that might be needed post-boot (e.g., verifying SharedPreferences state).
 *
 * No foreground service is needed here — the IncomingCallReceiver is already
 * a manifest-registered receiver and survives reboots on its own.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {

            Log.d(TAG, "Device booted — IncomingCallReceiver is active via manifest registration.");

            // The IncomingCallReceiver is manifest-registered so Android restores it
            // automatically. We log here to confirm the boot event was received.
            // SharedPreferences state (saved volume, feature enabled flag) persists
            // across reboots in the app's private storage, so no reinitialization needed.
        }
    }
}
