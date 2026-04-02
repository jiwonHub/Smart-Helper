package com.scchyodol.smarthelper.data.db

import androidx.room.TypeConverter
import com.scchyodol.smarthelper.data.model.Mood

class MoodConverter {

    // Enum → String (저장 시)
    @TypeConverter
    fun fromMood(mood: Mood): String {
        return mood.name  // "HAPPY", "CALM" 등
    }

    // String → Enum (불러올 시)
    @TypeConverter
    fun toMood(name: String): Mood {
        return Mood.valueOf(name)
    }
}
