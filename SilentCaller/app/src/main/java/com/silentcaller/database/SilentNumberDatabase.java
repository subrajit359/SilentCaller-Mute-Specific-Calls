package com.silentcaller.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.silentcaller.model.SilentNumber;

/**
 * Room Database singleton for Silent Caller.
 * Thread-safe via volatile + synchronized getInstance().
 */
@Database(entities = {SilentNumber.class}, version = 1, exportSchema = false)
public abstract class SilentNumberDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "silent_caller_db";
    private static volatile SilentNumberDatabase instance;

    public abstract SilentNumberDao silentNumberDao();

    /**
     * Returns the singleton database instance.
     * Creates it if it does not yet exist.
     */
    public static SilentNumberDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (SilentNumberDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SilentNumberDatabase.class,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return instance;
    }
}
