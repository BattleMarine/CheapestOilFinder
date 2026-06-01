package com.example.cheapestoilfinder.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.example.cheapestoilfinder.map.model.LocationPoint

object DeviceLocationResolver {
    fun resolveCurrentLocation(
        context: Context,
        onSuccess: (LocationPoint) -> Unit,
        onError: (String) -> Unit
    ) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onError("위치 권한이 필요합니다.")
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            onError("위치 서비스를 사용할 수 없습니다.")
            return
        }

        pickBestLastKnownLocation(locationManager)?.let { lastKnownLocation ->
            onSuccess(lastKnownLocation.toLocationPoint())
            return
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            onError("사용 가능한 위치 제공자가 없습니다.")
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onSuccess(location.toLocationPoint())
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (exception: SecurityException) {
            onError("위치 권한을 다시 확인해 주세요.")
        } catch (exception: IllegalArgumentException) {
            onError("위치 제공자를 사용할 수 없습니다.")
        }
    }

    private fun pickBestLastKnownLocation(locationManager: LocationManager): Location? {
        val candidates = listOfNotNull(
            runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
            runCatching { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        )

        return candidates.maxWithOrNull(
            compareBy<Location> { it.time }.thenByDescending { it.accuracy }
        )
    }

    private fun Location.toLocationPoint(): LocationPoint {
        return LocationPoint(
            latitude = latitude,
            longitude = longitude,
            name = "현재 위치",
            address = "GPS"
        )
    }
}
