package com.silentcaller.receiver;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.silentcaller.database.SilentNumberDatabase;
import com.silentcaller.model.SilentNumber;
import com.silentcaller.service.OverlayService;
import com.silentcaller.util.PhoneNumberUtils;

import java.util.concurrent.Executors;

/**
 * BroadcastReceiver that listens for incoming phone calls.
 *
 * On RINGING:
 *   - Checks if the incoming number is on the silent list.
 *   - If YES: silences the phone (no ring, no vibration). Saves ringer state for restore.
 *   - If NO:  starts OverlayService so the user can add the number via the mute icon
 *             (Feature 2) or volume-button popup (Feature 1), if overlay permission is granted.
 *
 * On OFFHOOK (call answered): stops the OverlayService if running.
 *
 * On IDLE (call ended): restores the saved ringer state (if the call was silenced by this
 *   app — either from the silent list or via the overlay). Stops OverlayService.
 *
 * Registered in AndroidManifest.xml so it works even when the app is closed.
 */
public class IncomingCallReceiver extends BroadcastReceiver {

    private static final String TAG = "IncomingCallReceiver";
    private static final String PREFS_NAME = "silent_caller_prefs";
    private static final String KEY_FEATURE_ENABLED = "feature_enabled";
    private static final String KEY_SAVED_VOLUME = "saved_ringer_volume";
    private static final String KEY_SAVED_RINGER_MODE = "saved_ringer_mode";
    private static final String KEY_WAS_SILENCED = "was_silenced";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            boolean featureEnabled = prefs.getBoolean(KEY_FEATURE_ENABLED, true);
            if (!featureEnabled) {
                Log.d(TAG, "Feature disabled — skipping silence check.");
                return;
            }

            String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if (incomingNumber == null || incomingNumber.isEmpty()) {
                Log.d(TAG, "No incoming number (READ_CALL_LOG may be denied or hidden caller).");
                return;
            }

            String normalizedNumber = PhoneNumberUtils.normalize(incomingNumber);
            Log.d(TAG, "Incoming call from: " + incomingNumber + " → normalized: " + normalizedNumber);

            final String finalIncomingNumber = incomingNumber;
            final PendingResult pendingResult = goAsync();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    SilentNumberDatabase db = SilentNumberDatabase.getInstance(context);
                    SilentNumber found = db.silentNumberDao().findByNumber(normalizedNumber);

                    if (found != null) {
                        // Number is in the silent list — silence immediately, no overlay.
                        Log.d(TAG, "Number on silent list — silencing (no overlay).");
                        silencePhone(context, prefs);
                    } else {
                        // Number is NOT in the silent list — ring normally, show overlay.
                        Log.d(TAG, "Number NOT on silent list — ringing normally, showing overlay.");
                        prefs.edit().putBoolean(KEY_WAS_SILENCED, false).apply();
                        startOverlayServiceIfAllowed(context, finalIncomingNumber);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking silent list", e);
                } finally {
                    pendingResult.finish();
                }
            });

        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            // Call was answered — dismiss the overlay so it doesn't linger
            Log.d(TAG, "Call answered (OFFHOOK) — stopping overlay service.");
            stopOverlayService(context);

        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            // Call ended — restore ringer (if we silenced it) and stop the overlay.
            boolean wasSilenced = prefs.getBoolean(KEY_WAS_SILENCED, false);
            if (wasSilenced) {
                Log.d(TAG, "Call ended — restoring ringer.");
                restorePhone(context, prefs);
            }
            stopOverlayService(context);
        }
    }

    // ---- Overlay service lifecycle ----

    private void startOverlayServiceIfAllowed(Context context, String phoneNumber) {
        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(context)) {
            Log.d(TAG, "Overlay permission not granted — skipping overlay.");
            return;
        }

        Intent overlayIntent = new Intent(context, OverlayService.class);
        overlayIntent.putExtra(OverlayService.EXTRA_PHONE_NUMBER, phoneNumber);
        // startForegroundService() is required on API 26+ to start a service from the background.
        // Since minSdk is 29, this is always used. The service calls startForeground() promptly.
        context.startForegroundService(overlayIntent);
        Log.d(TAG, "OverlayService started for: " + phoneNumber);
    }

    private void stopOverlayService(Context context) {
        try {
            context.stopService(new Intent(context, OverlayService.class));
        } catch (Exception e) {
            Log.e(TAG, "Error stopping OverlayService", e);
        }
    }

    // ---- Audio management ----

    /**
     * Silence the phone completely (no ring, no vibration).
     *
     * Strategy:
     *  1. If the app has DND (Notification Policy) access: use setRingerMode(SILENT).
     *     This is the most reliable method — suppresses both audio and vibration.
     *  2. Fallback: mute the STREAM_RING using adjustStreamVolume(ADJUST_MUTE).
     *     This stops the ring audio but cannot suppress vibration without DND access.
     */
    private void silencePhone(Context context, SharedPreferences prefs) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;

        int currentVolume = audio.getStreamVolume(AudioManager.STREAM_RING);
        int currentRingerMode = audio.getRingerMode();

        prefs.edit()
                .putInt(KEY_SAVED_VOLUME, currentVolume)
                .putInt(KEY_SAVED_RINGER_MODE, currentRingerMode)
                .putBoolean(KEY_WAS_SILENCED, true)
                .apply();

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (nm != null && nm.isNotificationPolicyAccessGranted()) {
            // Best path: fully silent (no ring, no vibration)
            audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            Log.d(TAG, "Ringer mode set to SILENT via DND access. Saved mode: " + currentRingerMode);
        } else {
            // Fallback: mute audio stream (ring suppressed; vibration may persist)
            audio.adjustStreamVolume(
                    AudioManager.STREAM_RING,
                    AudioManager.ADJUST_MUTE,
                    0
            );
            Log.d(TAG, "Stream RING muted (no DND access; vibration may still occur).");
        }
    }

    /**
     * Restore the phone to the state it was in before silencing.
     */
    private void restorePhone(Context context, SharedPreferences prefs) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;

        int savedRingerMode = prefs.getInt(KEY_SAVED_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL);
        int savedVolume = prefs.getInt(KEY_SAVED_VOLUME, 3);

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (nm != null && nm.isNotificationPolicyAccessGranted()) {
            audio.setRingerMode(savedRingerMode);
            Log.d(TAG, "Ringer mode restored to: " + savedRingerMode);
        } else {
            // Unmute the stream and restore volume
            audio.adjustStreamVolume(
                    AudioManager.STREAM_RING,
                    AudioManager.ADJUST_UNMUTE,
                    0
            );
            try {
                audio.setStreamVolume(AudioManager.STREAM_RING, savedVolume, 0);
            } catch (Exception e) {
                Log.w(TAG, "Could not restore stream volume: " + e.getMessage());
            }
            Log.d(TAG, "Stream RING unmuted and volume restored to: " + savedVolume);
        }

        prefs.edit().putBoolean(KEY_WAS_SILENCED, false).apply();
    }
}
