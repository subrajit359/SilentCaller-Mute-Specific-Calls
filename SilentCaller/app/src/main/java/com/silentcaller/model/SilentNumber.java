package com.silentcaller.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a phone number that should be silenced.
 * phoneNumber is marked unique to prevent duplicate entries.
 */
@Entity(
    tableName = "silent_numbers",
    indices = {@Index(value = {"phoneNumber"}, unique = true)}
)
public class SilentNumber {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String phoneNumber;

    private String label;       // Optional nickname, may be null

    private long addedAt;       // Timestamp in milliseconds (System.currentTimeMillis())

    public SilentNumber(String phoneNumber, String label, long addedAt) {
        this.phoneNumber = phoneNumber;
        this.label = label;
        this.addedAt = addedAt;
    }

    // --- Getters ---

    public int getId() {
        return id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getLabel() {
        return label;
    }

    public long getAddedAt() {
        return addedAt;
    }

    // --- Setters (Room requires setters or public fields) ---

    public void setId(int id) {
        this.id = id;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setAddedAt(long addedAt) {
        this.addedAt = addedAt;
    }
}
