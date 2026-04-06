package com.scchyodol.smarthelper.data.remote.repository

import android.content.Context
import com.scchyodol.smarthelper.data.remote.RetrofitClient
import com.scchyodol.smarthelper.data.remote.dto.WeatherResponse
import com.scchyodol.smarthelper.location.LocationProvider
import com.scchyodol.smarthelper.util.Result
import kotlinx.coroutines.CancellationException

class WeatherRepository(context: Context) {

    private val weatherApi = RetrofitClient.weatherApi
    private val locationProvider = LocationProvider(context)

    suspend fun getWeatherByCurrentLocation(): Result<WeatherResponse> {
        return try {
            val location = locationProvider.getCurrentLocation()

            val response = weatherApi.getWeather(
                latitude = location.latitude,
                longitude = location.longitude
            )

            Result.Success(response)

        } catch (e: CancellationException) {
            //  코루틴 취소는 반드시 다시 던져야 함
            // 삼키면 코루틴이 정상 종료인 줄 알고 이상하게 동작함
            throw e

        } catch (e: Exception) {
            Result.Error(
                message = e.message ?: "날씨 정보를 불러오지 못했습니다.",
                throwable = e
            )
        }
    }
}
