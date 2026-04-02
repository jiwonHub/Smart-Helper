package com.scchyodol.smarthelper.data.model

enum class CareCategory(val displayName: String) {
    MEDICATION("투약"),
    SLEEP("수면"),
    MEAL("식사"),
    EXCRETION("배변"),
    TEMPERATURE("체온"),
    OTHER("기타");

    companion object {
        // displayName으로 역검색 (DB 저장값 → Enum 변환 시 사용)
        fun fromDisplayName(name: String): CareCategory {
            return entries.find { it.displayName == name } ?: OTHER
        }
    }
}
