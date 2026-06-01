package com.example.cheapestoilfinder.entry

import android.app.Activity
import android.Manifest
import android.os.Bundle
import android.content.pm.PackageManager
import android.util.Log
import android.widget.ImageButton
import android.widget.Button
import android.widget.Toast
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.location.DeviceLocationResolver
import com.example.cheapestoilfinder.map.KakaoMapController
import com.example.cheapestoilfinder.map.MapScreenMode
import com.example.cheapestoilfinder.map.model.LocationPoint

class DestinationActivity : Activity() {
    private var mapController: KakaoMapController? = null
    private var pendingGpsActionAfterPermission: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destination)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.button_destination_gps).setOnClickListener {
            refreshCurrentLocationFromGps()
        }

        mapController = KakaoMapController(
            R.id.destination_map_container,
            MapScreenMode.DESTINATION_ROUTE
        ).also {
            it.bind(this)
            it.start()
        }
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
                updateDestinationMapToCurrentLocation(point)
            },
            onError = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Failed to resolve GPS location: $message")
            }
        )
    }

    private fun updateDestinationMapToCurrentLocation(point: LocationPoint) {
        mapController?.focusCurrentLocation(point, 15)
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
        private const val TAG = "DestinationActivity"
        private const val REQUEST_LOCATION_PERMISSION = 2002
    }
}
