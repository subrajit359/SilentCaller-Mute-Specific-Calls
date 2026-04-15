package com.silentcaller.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.silentcaller.R;
import com.silentcaller.model.SilentNumber;
import com.silentcaller.viewmodel.SilentNumberViewModel;

/**
 * Screen for adding a new phone number to the silent list.
 *
 * Responsibilities:
 *  - Accept a phone number and optional label
 *  - Validate: not empty, not duplicate
 *  - Insert into database via ViewModel
 *  - Return to MainActivity on success
 */
public class AddNumberActivity extends AppCompatActivity {

    private SilentNumberViewModel viewModel;
    private TextInputLayout tilPhoneNumber;
    private TextInputLayout tilLabel;
    private TextInputEditText etPhoneNumber;
    private TextInputEditText etLabel;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_number);

        // Toolbar with back button
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.add_number_title);
        }

        // Views
        tilPhoneNumber = findViewById(R.id.til_phone_number);
        tilLabel = findViewById(R.id.til_label);
        etPhoneNumber = findViewById(R.id.et_phone_number);
        etLabel = findViewById(R.id.et_label);
        btnSave = findViewById(R.id.btn_save);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(SilentNumberViewModel.class);

        // Save button click
        btnSave.setOnClickListener(v -> attemptSave());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void attemptSave() {
        // Clear previous errors
        tilPhoneNumber.setError(null);

        String rawNumber = etPhoneNumber.getText() != null
                ? etPhoneNumber.getText().toString().trim()
                : "";

        String label = etLabel.getText() != null
                ? etLabel.getText().toString().trim()
                : "";

        // Validation: phone number must not be empty
        if (TextUtils.isEmpty(rawNumber)) {
            tilPhoneNumber.setError(getString(R.string.error_empty_number));
            return;
        }

        // Basic format check — must contain at least 5 digits
        String digitsOnly = rawNumber.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 5) {
            tilPhoneNumber.setError(getString(R.string.error_invalid_number));
            return;
        }

        // Disable button during async check to prevent double-saves
        btnSave.setEnabled(false);

        // Check for duplicate on background thread
        viewModel.checkNumberExists(rawNumber, exists -> {
            runOnUiThread(() -> {
                if (exists) {
                    tilPhoneNumber.setError(getString(R.string.error_duplicate_number));
                    btnSave.setEnabled(true);
                } else {
                    // Save the number
                    long now = System.currentTimeMillis();
                    SilentNumber silentNumber = new SilentNumber(
                            rawNumber,
                            label.isEmpty() ? null : label,
                            now
                    );
                    viewModel.insert(silentNumber);
                    Toast.makeText(this, R.string.number_added_success, Toast.LENGTH_SHORT).show();
                    finish(); // Go back to MainActivity
                }
            });
        });
    }
}
