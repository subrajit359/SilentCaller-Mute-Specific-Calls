package com.silentcaller.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.silentcaller.model.SilentNumber;
import com.silentcaller.repository.SilentNumberRepository;

import java.util.List;

/**
 * ViewModel for the main screen.
 * Extends AndroidViewModel to get Application context for Room database.
 * Survives configuration changes (screen rotation) without data loss.
 */
public class SilentNumberViewModel extends AndroidViewModel {

    private final SilentNumberRepository repository;
    private final LiveData<List<SilentNumber>> allNumbers;

    public SilentNumberViewModel(@NonNull Application application) {
        super(application);
        repository = new SilentNumberRepository(application);
        allNumbers = repository.getAllNumbers();
    }

    /**
     * LiveData list of all silent numbers — observed by MainActivity.
     * Automatically updates the UI whenever the database changes.
     */
    public LiveData<List<SilentNumber>> getAllNumbers() {
        return allNumbers;
    }

    /**
     * Insert a new silent number.
     * Normalizes the number in the repository before saving.
     */
    public void insert(SilentNumber silentNumber) {
        repository.insert(silentNumber);
    }

    /**
     * Delete a silent number from the list.
     */
    public void delete(SilentNumber silentNumber) {
        repository.delete(silentNumber);
    }

    /**
     * Check if a number already exists (runs on background thread via callback).
     */
    public void checkNumberExists(String phoneNumber, ExistsCallback callback) {
        new Thread(() -> {
            boolean exists = repository.numberExists(phoneNumber);
            callback.onResult(exists);
        }).start();
    }

    /**
     * Callback interface for async existence checks.
     */
    public interface ExistsCallback {
        void onResult(boolean exists);
    }
}
