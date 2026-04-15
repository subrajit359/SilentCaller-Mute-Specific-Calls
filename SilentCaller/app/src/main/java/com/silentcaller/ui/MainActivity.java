package com.silentcaller.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.silentcaller.R;
import com.silentcaller.adapter.SilentNumberAdapter;
import com.silentcaller.model.SilentNumber;
import com.silentcaller.viewmodel.SilentNumberViewModel;

/**
 * Main screen — displays the list of silenced phone numbers.
 *
 * Responsibilities:
 *  - Request runtime permissions (READ_PHONE_STATE, READ_CALL_LOG, MODIFY_AUDIO_SETTINGS)
 *  - Check and request SYSTEM_ALERT_WINDOW permission for overlay features
 *  - Observe LiveData list from ViewModel and update RecyclerView
 *  - Handle FAB click to open AddNumberActivity
 *  - Handle remove button click with confirmation dialog
 */
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private SilentNumberViewModel viewModel;
    private SilentNumberAdapter adapter;
    private View tvEmpty;
    private RecyclerView recyclerView;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar
        setSupportActionBar(findViewById(R.id.toolbar));

        // Views
        recyclerView = findViewById(R.id.recycler_view);
        tvEmpty = findViewById(R.id.tv_empty);
        FloatingActionButton fab = findViewById(R.id.fab_add);

        // RecyclerView setup
        adapter = new SilentNumberAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Remove button — show confirmation dialog
        adapter.setOnItemActionListener(this::showDeleteConfirmationDialog);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(SilentNumberViewModel.class);
        viewModel.getAllNumbers().observe(this, numbers -> {
            adapter.submitList(numbers);
            // Toggle empty state
            if (numbers == null || numbers.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });

        // FAB — navigate to AddNumberActivity
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, AddNumberActivity.class))
        );

        // Request runtime permissions on launch
        checkAndRequestPermissions();

        // Check overlay permission (SYSTEM_ALERT_WINDOW) — needed for mute icon overlay
        checkOverlayPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check overlay permission each time the activity resumes,
        // in case the user just returned from the Settings screen.
        // Only show prompt if not already granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            // Already prompted once; just show a quiet banner on repeat visits.
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- Runtime Permissions ----

    private void checkAndRequestPermissions() {
        boolean allGranted = true;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (!allGranted) {
            showPermissionRationaleDialog();
        }
    }

    private void showPermissionRationaleDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_rationale_title)
                .setMessage(R.string.permission_rationale_message)
                .setPositiveButton(R.string.grant, (dialog, which) ->
                        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
                )
                .setNegativeButton(R.string.cancel, (dialog, which) ->
                        showPermissionDeniedWarning()
                )
                .setCancelable(false)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                showPermissionDeniedWarning();
            }
        }
    }

    private void showPermissionDeniedWarning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_denied_title)
                .setMessage(R.string.permission_denied_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.dismiss, null)
                .show();
    }

    // ---- Overlay (SYSTEM_ALERT_WINDOW) Permission ----

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return; // Not required below M
        if (Settings.canDrawOverlays(this)) return; // Already granted

        new AlertDialog.Builder(this)
                .setTitle(R.string.overlay_permission_title)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())
                    );
                    startActivity(intent);
                })
                .setNegativeButton(R.string.dismiss, null)
                .show();
    }

    // ---- Delete Confirmation ----

    private void showDeleteConfirmationDialog(SilentNumber silentNumber) {
        String displayNumber = silentNumber.getPhoneNumber();
        if (silentNumber.getLabel() != null && !silentNumber.getLabel().isEmpty()) {
            displayNumber = silentNumber.getLabel() + " (" + silentNumber.getPhoneNumber() + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.confirm_delete_message, displayNumber))
                .setPositiveButton(R.string.remove, (dialog, which) -> {
                    viewModel.delete(silentNumber);
                    Snackbar.make(recyclerView,
                            R.string.number_removed,
                            Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
