package com.silentcaller.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.silentcaller.R;

/**
 * Settings screen — global toggle to enable/disable the silent feature.
 *
 * The toggle state is stored in SharedPreferences.
 * IncomingCallReceiver reads this preference on every incoming call.
 * Disabling the feature does NOT remove numbers from the list.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "silent_caller_prefs";
    private static final String KEY_FEATURE_ENABLED = "feature_enabled";

    private SharedPreferences prefs;
    private SwitchCompat switchFeature;
    private TextView tvStatusDescription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Toolbar with back button
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        switchFeature = findViewById(R.id.switch_feature_enabled);
        tvStatusDescription = findViewById(R.id.tv_status_description);

        // Load saved state — default is enabled
        boolean isEnabled = prefs.getBoolean(KEY_FEATURE_ENABLED, true);
        switchFeature.setChecked(isEnabled);
        updateStatusDescription(isEnabled);

        switchFeature.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(KEY_FEATURE_ENABLED, isChecked).apply();
            updateStatusDescription(isChecked);
        });
    }

    private void updateStatusDescription(boolean enabled) {
        if (enabled) {
            tvStatusDescription.setText(R.string.feature_enabled_description);
        } else {
            tvStatusDescription.setText(R.string.feature_disabled_description);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
