package com.scchyodol.smarthelper.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.scchyodol.smarthelper.data.db.MoodConverter


@Entity(
    tableName = "user_moods",
    indices = [Index(value = ["date"], unique = true)]
)
@TypeConverters(MoodConverter::class)
data class UserMood(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    //  날짜 (밀리세컨드)
    val date: Long,

    //  기분 (Enum → TypeConverter로 String 저장)
    val mood: Mood
)
