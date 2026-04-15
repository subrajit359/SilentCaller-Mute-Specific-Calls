package com.silentcaller.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.silentcaller.model.SilentNumber;

import java.util.List;

/**
 * Data Access Object for SilentNumber.
 * All database interactions for silent numbers go through this interface.
 */
@Dao
public interface SilentNumberDao {

    /**
     * Insert a new silent number. Ignores duplicate phone numbers (unique constraint).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(SilentNumber silentNumber);

    /**
     * Delete a specific silent number from the list.
     */
    @Delete
    void delete(SilentNumber silentNumber);

    /**
     * Get all silent numbers as LiveData so the UI updates automatically.
     * Results are ordered newest first.
     */
    @Query("SELECT * FROM silent_numbers ORDER BY addedAt DESC")
    LiveData<List<SilentNumber>> getAllNumbers();

    /**
     * Find a number by exact phone number string.
     * Used by BroadcastReceiver to check if an incoming number is silenced.
     * This is a synchronous query — must run on a background thread.
     */
    @Query("SELECT * FROM silent_numbers WHERE phoneNumber = :phoneNumber LIMIT 1")
    SilentNumber findByNumber(String phoneNumber);

    /**
     * Check if a phone number already exists in the silent list.
     * Returns count — 0 means not found, 1 means found.
     */
    @Query("SELECT COUNT(*) FROM silent_numbers WHERE phoneNumber = :phoneNumber")
    int countByNumber(String phoneNumber);
}
