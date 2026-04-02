package com.scchyodol.smarthelper.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.scchyodol.smarthelper.data.dao.CareRecordDao
import com.scchyodol.smarthelper.data.dao.UserMoodDao
import com.scchyodol.smarthelper.data.model.CareRecord
import com.scchyodol.smarthelper.data.model.UserMood

@Database(
    entities = [
        CareRecord::class,
        UserMood::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(
    CareCategoryConverter::class,
    MoodConverter::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun careRecordDao(): CareRecordDao
    abstract fun userMoodDao(): UserMoodDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_helper.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
