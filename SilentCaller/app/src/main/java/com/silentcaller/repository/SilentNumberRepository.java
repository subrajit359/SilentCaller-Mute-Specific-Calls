package com.silentcaller.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.silentcaller.database.SilentNumberDatabase;
import com.silentcaller.database.SilentNumberDao;
import com.silentcaller.model.SilentNumber;
import com.silentcaller.util.PhoneNumberUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository layer — mediates between the ViewModel and Room database.
 * All database writes run on a background thread via ExecutorService.
 * Database reads return LiveData, so they are observed automatically.
 */
public class SilentNumberRepository {

    private final SilentNumberDao dao;
    private final ExecutorService executorService;
    private final LiveData<List<SilentNumber>> allNumbers;

    public SilentNumberRepository(Context context) {
        SilentNumberDatabase db = SilentNumberDatabase.getInstance(context);
        dao = db.silentNumberDao();
        executorService = Executors.newSingleThreadExecutor();
        allNumbers = dao.getAllNumbers();
    }

    /**
     * Returns LiveData list of all silent numbers (auto-updates UI on change).
     */
    public LiveData<List<SilentNumber>> getAllNumbers() {
        return allNumbers;
    }

    /**
     * Insert a new silent number on a background thread.
     * The phone number is normalized before saving.
     */
    public void insert(SilentNumber silentNumber) {
        executorService.execute(() -> {
            String normalized = PhoneNumberUtils.normalize(silentNumber.getPhoneNumber());
            silentNumber.setPhoneNumber(normalized);
            dao.insert(silentNumber);
        });
    }

    /**
     * Delete a silent number on a background thread.
     */
    public void delete(SilentNumber silentNumber) {
        executorService.execute(() -> dao.delete(silentNumber));
    }

    /**
     * Check (synchronously, on the calling thread) if a phone number already exists.
     * Caller MUST NOT be on the main thread.
     */
    public boolean numberExists(String phoneNumber) {
        String normalized = PhoneNumberUtils.normalize(phoneNumber);
        return dao.countByNumber(normalized) > 0;
    }

    /**
     * Find a SilentNumber by phone number synchronously.
     * Caller MUST NOT be on the main thread (used by BroadcastReceiver).
     * Returns null if not found.
     */
    public SilentNumber findByNumber(String phoneNumber) {
        String normalized = PhoneNumberUtils.normalize(phoneNumber);
        return dao.findByNumber(normalized);
    }
}
