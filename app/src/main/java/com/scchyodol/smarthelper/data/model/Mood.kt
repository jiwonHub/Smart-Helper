package com.scchyodol.smarthelper.data.model

enum class Mood(val displayName: String, val emoji: String) {
    HAPPY("행복", "😊"),
    NORMAL("평온", "😌"),
    TIRED("지침", "😩"),
    SAD("슬픔", "😢");

    companion object {
        fun fromDisplayName(name: String): Mood {
            return entries.find { it.displayName == name } ?: NORMAL
        }
    }
}
