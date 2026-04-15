package com.silentcaller.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.silentcaller.R;
import com.silentcaller.database.SilentNumberDatabase;
import com.silentcaller.model.SilentNumber;
import com.silentcaller.util.PhoneNumberUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OverlayService — shows a mute icon overlay over the incoming call screen.
 *
 * Feature 1 (Volume Button): A ContentObserver monitors the ringer volume.
 *   When the volume drops to 0 during a ringing call, a popup appears asking
 *   "Add to silent list?" with "Yes, Add" and "No Thanks" buttons.
 *   The popup auto-dismisses after 10 seconds if untouched.
 *
 * Feature 2 (Floating Mute Icon): A small mute icon is drawn over the profile
 *   picture area of the call screen. Tapping it immediately adds the caller's
 *   number to the Room Database and silences the current call.
 *
 * The overlay is only shown for numbers NOT already in the silent list.
 * When the call ends (IDLE/OFFHOOK), the IncomingCallReceiver stops this service.
 */
public class OverlayService extends Service {

    public static final String EXTRA_PHONE_NUMBER = "extra_phone_number";

    private static final String TAG = "OverlayService";
    private static final String PREFS_NAME = "silent_caller_prefs";
    private static final String KEY_SAVED_VOLUME = "saved_ringer_volume";
    private static final String KEY_SAVED_RINGER_MODE = "saved_ringer_mode";
    private static final String KEY_WAS_SILENCED = "was_silenced";

    // Auto-dismiss timeout for the volume-button popup (10 seconds)
    private static final long POPUP_TIMEOUT_MS = 10_000;

    private WindowManager windowManager;
    private View muteIconView;
    private View addDialogView;

    private String phoneNumber;
    private Handler mainHandler;
    private Runnable autoDismissRunnable;
    private ContentObserver volumeObserver;
    private ExecutorService executor;

    private boolean dialogShowing = false;
    private boolean overlayDismissed = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must call startForeground() immediately — required within 5 seconds of
        // startForegroundService(). This satisfies Android 8+ background service rules.
        startForegroundWithNotification();

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER);
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Double-check overlay permission — safety guard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Save the ringer state at the start of the call (before any volume-button press).
        // This is used by markAsSilenced() and saveRingerStateAndSilence() to know what
        // state to restore after the call ends.
        saveInitialRingerState();

        showMuteIcon();
        registerVolumeObserver();

        return START_NOT_STICKY;
    }

    /**
     * Captures the ringer state before any user interaction so it can be restored on IDLE.
     * KEY_WAS_SILENCED is intentionally NOT set here — that only happens when the user taps.
     */
    private void saveInitialRingerState() {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (audio == null) return;
        int vol = audio.getStreamVolume(AudioManager.STREAM_RING);
        int mode = audio.getRingerMode();
        prefs.edit()
                .putInt(KEY_SAVED_VOLUME, vol)
                .putInt(KEY_SAVED_RINGER_MODE, mode)
                .apply();
    }

    private static final String NOTIF_CHANNEL_ID = "silent_caller_overlay";
    private static final int NOTIF_ID = 8421;

    private void startForegroundWithNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Silent Caller Overlay",
                    NotificationManager.IMPORTANCE_MIN  // No sound/heads-up
            );
            channel.setDescription("Monitoring incoming call");
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Silent Caller")
                .setContentText("Tap the mute icon on the call screen to add caller to silent list.")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setSilent(true)
                .build();

        startForeground(NOTIF_ID, notification);
    }

    // ---- Feature 2: Mute icon over the profile picture area ----

    private void showMuteIcon() {
        if (windowManager == null) return;

        muteIconView = LayoutInflater.from(this).inflate(R.layout.overlay_mute_button, null);

        WindowManager.LayoutParams params = buildOverlayParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        );

        // Position over the caller's profile picture — roughly 33% from top, centred.
        // This matches where the contact avatar appears on most Android call screens.
        DisplayMetrics dm = getResources().getDisplayMetrics();
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = (int) (dm.heightPixels * 0.33f);

        muteIconView.setOnClickListener(v -> addNumberToSilentList(true));

        try {
            windowManager.addView(muteIconView, params);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to add mute icon view", e);
        }
    }

    // ---- Feature 1: Volume-button ContentObserver ----

    private void registerVolumeObserver() {
        volumeObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                if (dialogShowing || overlayDismissed) return;

                AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (audio == null) return;

                int vol = audio.getStreamVolume(AudioManager.STREAM_RING);
                if (vol == 0) {
                    // User pressed volume-down to silence — show "Add to list?" popup
                    mainHandler.post(OverlayService.this::showVolumeButtonPopup);
                }
            }
        };

        try {
            getContentResolver().registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    volumeObserver
            );
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to register volume observer", e);
        }
    }

    private void showVolumeButtonPopup() {
        if (dialogShowing || overlayDismissed) return;
        dialogShowing = true;

        // Remove the mute icon — replace with the popup
        removeMuteIcon();

        addDialogView = LayoutInflater.from(this).inflate(R.layout.overlay_add_dialog, null);

        WindowManager.LayoutParams params = buildOverlayParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dpToPx(140); // above the decline button

        // Wire up buttons
        addDialogView.findViewById(R.id.btn_yes_add)
                .setOnClickListener(v -> addNumberToSilentList(false));

        addDialogView.findViewById(R.id.btn_no_thanks)
                .setOnClickListener(v -> dismissAll());

        try {
            windowManager.addView(addDialogView, params);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to add dialog view", e);
            dismissAll();
            return;
        }

        // Auto-dismiss after 10 seconds
        autoDismissRunnable = this::dismissAll;
        mainHandler.postDelayed(autoDismissRunnable, POPUP_TIMEOUT_MS);
    }

    // ---- Add number to Room Database + silence current call ----

    /**
     * @param silenceImmediately true when the user taps the mute icon (Feature 2);
     *                           false when coming from the popup (Feature 1 — phone
     *                           was already silenced by the volume button).
     */
    private void addNumberToSilentList(boolean silenceImmediately) {
        cancelAutoDismiss();

        final String normalized = PhoneNumberUtils.normalize(phoneNumber);

        // Save ringer state and mark as silenced NOW (before the background thread)
        // so the IDLE restore in IncomingCallReceiver works correctly even if the
        // call ends before the DB insert completes.
        if (silenceImmediately) {
            saveRingerStateAndSilence();
        } else {
            // Volume button already silenced the ringer; just mark it for restore.
            markAsSilenced();
        }

        executor.execute(() -> {
            try {
                SilentNumberDatabase db = SilentNumberDatabase.getInstance(OverlayService.this);
                int count = db.silentNumberDao().countByNumber(normalized);
                if (count > 0) {
                    mainHandler.post(() -> {
                        Toast.makeText(OverlayService.this,
                                getString(R.string.overlay_already_in_list),
                                Toast.LENGTH_SHORT).show();
                        dismissAll();
                    });
                    return;
                }

                SilentNumber sn = new SilentNumber(normalized, null, System.currentTimeMillis());
                db.silentNumberDao().insert(sn);

                mainHandler.post(() -> {
                    Toast.makeText(OverlayService.this,
                            getString(R.string.number_added_success),
                            Toast.LENGTH_SHORT).show();
                    dismissAll();
                });
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error inserting number", e);
                mainHandler.post(this::dismissAll);
            }
        });
    }

    /**
     * Save the current ringer state, then silence the phone.
     * Sets KEY_WAS_SILENCED = true so IncomingCallReceiver restores properly on IDLE.
     */
    private void saveRingerStateAndSilence() {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (audio == null) return;

        // Only save state if it hasn't been saved already (avoid overwriting a valid saved state)
        if (!prefs.getBoolean(KEY_WAS_SILENCED, false)) {
            int currentVolume = audio.getStreamVolume(AudioManager.STREAM_RING);
            int currentRingerMode = audio.getRingerMode();
            prefs.edit()
                    .putInt(KEY_SAVED_VOLUME, currentVolume)
                    .putInt(KEY_SAVED_RINGER_MODE, currentRingerMode)
                    .putBoolean(KEY_WAS_SILENCED, true)
                    .apply();
        }

        if (nm != null && nm.isNotificationPolicyAccessGranted()) {
            audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        } else {
            audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0);
        }
    }

    /**
     * Called when the volume button already silenced the phone.
     * The pre-call ringer state was already captured in saveInitialRingerState().
     * We only need to mark KEY_WAS_SILENCED so IncomingCallReceiver restores on IDLE.
     */
    private void markAsSilenced() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_WAS_SILENCED, true).apply();
    }

    // ---- Cleanup ----

    public void dismissAll() {
        if (overlayDismissed) return;
        overlayDismissed = true;
        cancelAutoDismiss();
        removeMuteIcon();
        removeDialogView();
        stopSelf();
    }

    private void removeMuteIcon() {
        if (muteIconView != null && windowManager != null) {
            try {
                windowManager.removeView(muteIconView);
            } catch (Exception ignored) {}
            muteIconView = null;
        }
    }

    private void removeDialogView() {
        if (addDialogView != null && windowManager != null) {
            try {
                windowManager.removeView(addDialogView);
            } catch (Exception ignored) {}
            addDialogView = null;
        }
    }

    private void cancelAutoDismiss() {
        if (autoDismissRunnable != null) {
            mainHandler.removeCallbacks(autoDismissRunnable);
            autoDismissRunnable = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        overlayDismissed = true;
        cancelAutoDismiss();
        if (volumeObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(volumeObserver);
            } catch (Exception ignored) {}
        }
        removeMuteIcon();
        removeDialogView();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    // ---- Helpers ----

    private WindowManager.LayoutParams buildOverlayParams(int w, int h) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        return new WindowManager.LayoutParams(
                w, h, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
