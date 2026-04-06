package com.scchyodol.smarthelper.data.remote.dto

import com.google.gson.annotations.SerializedName

//  최상위 응답 전체
data class WeatherResponse(

    @SerializedName("latitude")
    val latitude: Double,

    @SerializedName("longitude")
    val longitude: Double,

    @SerializedName("generationtime_ms")
    val generationTimeMs: Double,

    @SerializedName("utc_offset_seconds")
    val utcOffsetSeconds: Int,

    @SerializedName("timezone")
    val timezone: String,

    @SerializedName("timezone_abbreviation")
    val timezoneAbbreviation: String,

    @SerializedName("elevation")
    val elevation: Double,

    // 단위 정보
    @SerializedName("current_units")
    val currentUnits: CurrentUnits,

    // 실제 현재 날씨 데이터
    @SerializedName("current")
    val current: CurrentWeather
)

//  단위 정보 (°C, % 등)
data class CurrentUnits(

    @SerializedName("time")
    val time: String,               // "iso8601"

    @SerializedName("interval")
    val interval: String,           // "seconds"

    @SerializedName("temperature_2m")
    val temperatureUnit: String,    // "°C"

    @SerializedName("relative_humidity_2m")
    val humidityUnit: String        // "%"
)

//  현재 날씨 실제 수치
data class CurrentWeather(

    @SerializedName("time")
    val time: String,               // "2026-03-27T17:45"

    @SerializedName("interval")
    val interval: Int,              // 900 (초 단위)

    @SerializedName("temperature_2m")
    val temperature: Double,        // 2.9

    @SerializedName("relative_humidity_2m")
    val humidity: Int               // 76
)

val WeatherResponse.temperatureFormatted: String
    get() = "${current.temperature}${currentUnits.temperatureUnit}"

val WeatherResponse.humidityFormatted: String
    get() = "${current.humidity}${currentUnits.humidityUnit}"
