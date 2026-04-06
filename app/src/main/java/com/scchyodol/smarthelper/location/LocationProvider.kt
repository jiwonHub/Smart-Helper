package com.scchyodol.smarthelper.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationProvider(context: Context) {

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    //  현재 위치를 한 번만 가져오는 suspend 함수
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location {
        val cancellationTokenSource = CancellationTokenSource()

        return suspendCancellableCoroutine { continuation ->

            fusedLocationClient
                .getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                )
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(location)
                    } else {
                        continuation.resumeWithException(
                            Exception("위치 정보를 가져올 수 없습니다.")
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }

            //  코루틴 취소 시 위치 요청도 취소
            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    }
}
