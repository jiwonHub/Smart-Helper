package com.scchyodol.smarthelper.data.db

import androidx.room.TypeConverter
import com.scchyodol.smarthelper.data.model.CareCategory

class CareCategoryConverter {

    // Enum → String (저장 시)
    @TypeConverter
    fun fromCategory(category: CareCategory): String {
        return category.name  // "MEDICATION", "SLEEP" 등 저장
    }

    // String → Enum (불러올 시)
    @TypeConverter
    fun toCategory(name: String): CareCategory {
        return CareCategory.valueOf(name)
    }
}
