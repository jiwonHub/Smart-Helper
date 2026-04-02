package com.scchyodol.smarthelper.data.remote.api

import com.scchyodol.smarthelper.data.remote.dto.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {

    @GET("v1/forecast")
    suspend fun getWeather(
        // 위도
        @Query("latitude") latitude: Double,

        // 경도
        @Query("longitude") longitude: Double,

        // 원하는 현재 데이터 항목
        @Query("current") current: String = "temperature_2m,relative_humidity_2m",

        // 타임존
        @Query("timezone") timezone: String = "Asia/Tokyo"
    ): WeatherResponse
}
