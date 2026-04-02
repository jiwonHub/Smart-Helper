package com.scchyodol.smarthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.scchyodol.smarthelper.data.db.CareCategoryConverter

@Entity(tableName = "care_records")
@TypeConverters(CareCategoryConverter::class)
data class CareRecord(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 저장 시간 (밀리세컨드) — 반복 시에는 기준 시각(시:분)만 사용
    val timestamp: Long,

    // 카테고리
    val category: CareCategory,

    // 값
    val value: String,

    // 메모
    val memo: String = "",

    // ───── 반복 스케줄 필드 ─────

    // 반복 여부
    val isRepeat: Boolean = false,

    // 반복 요일 리스트 (0=월 ~ 6=일) → Room TypeConverter로 String 변환
    // 예: [0,2,4] → "0,2,4"
    val repeatDays: String = ""   // TypeConverter 없이 String으로 직접 저장
)
