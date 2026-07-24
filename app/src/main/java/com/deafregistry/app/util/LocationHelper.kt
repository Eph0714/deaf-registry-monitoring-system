package com.deafregistry.app.util

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GpsPoint(val latitude: Double, val longitude: Double)

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): GpsPoint? = suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        client.getCurrentLocation(request, null)
            .addOnSuccessListener { location ->
                if (cont.isActive) {
                    cont.resume(location?.let { GpsPoint(it.latitude, it.longitude) })
                }
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }
}
