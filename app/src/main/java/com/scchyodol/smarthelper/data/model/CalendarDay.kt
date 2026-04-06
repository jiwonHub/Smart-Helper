package com.scchyodol.smarthelper.data.model

data class CalendarDay(
    val dayNumber: Int,
    val dayOfWeek: Int = 0,     // 0=일, 1=월, 2=화, 3=수, 4=목, 5=금, 6=토
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val hasCheck: Boolean = false,
    val hasClock: Boolean = false
)

data class ScheduleItem(
    val id       : Long   = 0L,
    val time: String,
    val label: String,
    val category: String,
    val value   : String? = null,
    val memo    : String? = null,
    val isDone: Boolean,
    val isRepeat : Boolean = false
)
