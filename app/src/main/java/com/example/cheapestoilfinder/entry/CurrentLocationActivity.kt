package com.example.cheapestoilfinder.entry

import android.app.Activity
import android.Manifest
import android.os.Bundle
import android.util.Log
import android.content.pm.PackageManager
import android.widget.ImageButton
import android.widget.Button
import android.widget.Toast
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.location.DeviceLocationResolver
import com.example.cheapestoilfinder.map.KakaoMapController
import com.example.cheapestoilfinder.map.MapScreenMode
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.LocationPoint
import com.example.cheapestoilfinder.station.BackendStationRepository
import com.example.cheapestoilfinder.station.StationDisplayMapper
import com.example.cheapestoilfinder.station.api.ApiCallback
import com.example.cheapestoilfinder.station.dto.NearbyStationSearchRequest
import com.example.cheapestoilfinder.station.dto.StationSearchResponse
import java.util.Collections

class CurrentLocationActivity : Activity() {
    private var mapController: KakaoMapController? = null
    private var stationRepository: BackendStationRepository? = null
    private var pendingGpsActionAfterPermission: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_current_location)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.button_refresh_gps).setOnClickListener {
            refreshCurrentLocationFromGps()
        }

        mapController = KakaoMapController(R.id.map_container, MapScreenMode.CURRENT_LOCATION).also {
            it.bind(this)
            it.start()
        }
        mapController?.moveCamera(SAMSUNG_STATION, 14)

        stationRepository = BackendStationRepository.createDefault()
        loadStationsAroundSamsungStation()
    }

    override fun onResume() {
        super.onResume()
        mapController?.onResume()
    }

    override fun onPause() {
        mapController?.onPause()
        super.onPause()
    }

    private fun refreshCurrentLocationFromGps() {
        if (hasLocationPermission()) {
            resolveAndShowCurrentLocation()
            return
        }

        pendingGpsActionAfterPermission = {
            resolveAndShowCurrentLocation()
        }
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
    }

    private fun resolveAndShowCurrentLocation() {
        DeviceLocationResolver.resolveCurrentLocation(
            context = this,
            onSuccess = { point ->
                Log.i(TAG, "GPS location resolved for current location screen: ${point.latitude}, ${point.longitude}")
                mapController?.focusCurrentLocation(point, 15)
                loadStationsAround(point)
            },
            onError = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Failed to resolve GPS location: $message")
            }
        )
    }

    private fun loadStationsAroundSamsungStation() {
        val request = NearbyStationSearchRequest(
            SAMSUNG_STATION.latitude,
            SAMSUNG_STATION.longitude,
            5.0,
            30.0,
            10.0,
            null,
            SAMSUNG_STATION.name
        )

        stationRepository?.searchNearbyStations(request, object : ApiCallback<StationSearchResponse> {
            override fun onSuccess(result: StationSearchResponse) {
                val stations: List<GasStation> = StationDisplayMapper.toGasStations(result)
                Log.i(TAG, "Loaded stations around Samsung Station: ${stations.size}")
                mapController?.moveCamera(SAMSUNG_STATION, 14)
                mapController?.showStations(stations)
            }

            override fun onError(error: Throwable) {
                Log.e(TAG, "Failed to load nearby stations", error)
                mapController?.moveCamera(SAMSUNG_STATION, 14)
                mapController?.showStations(Collections.emptyList())
            }
        })
    }

    private fun loadStationsAround(point: LocationPoint) {
        val request = NearbyStationSearchRequest(
            point.latitude,
            point.longitude,
            5.0,
            30.0,
            10.0,
            null,
            point.name
        )

        stationRepository?.searchNearbyStations(request, object : ApiCallback<StationSearchResponse> {
            override fun onSuccess(result: StationSearchResponse) {
                val stations: List<GasStation> = StationDisplayMapper.toGasStations(result)
                Log.i(TAG, "Loaded stations around GPS location: ${stations.size}")
                mapController?.showStations(stations)
            }

            override fun onError(error: Throwable) {
                Log.e(TAG, "Failed to load nearby stations for GPS location", error)
                mapController?.showStations(Collections.emptyList())
            }
        })
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_LOCATION_PERMISSION) {
            return
        }

        val permissionGranted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            pendingGpsActionAfterPermission?.invoke()
        } else {
            Toast.makeText(this, R.string.location_permission_required_message, Toast.LENGTH_SHORT).show()
        }

        pendingGpsActionAfterPermission = null
    }

    companion object {
        private const val TAG = "CurrentLocationActivity"
        private const val REQUEST_LOCATION_PERMISSION = 2001
        private val SAMSUNG_STATION = LocationPoint(
            37.5089,
            127.0632,
            "삼성역",
            "서울특별시 강남구 삼성동"
        )
    }
}
