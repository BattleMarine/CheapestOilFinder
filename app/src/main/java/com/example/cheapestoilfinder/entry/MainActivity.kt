package com.example.cheapestoilfinder.entry

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.settings.SettingsActivity

class MainActivity : Activity() {
    private var pendingActionAfterLocationPermission: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val currentLocationButton = findViewById<Button>(R.id.button_current_location)
        val destinationButton = findViewById<Button>(R.id.button_destination)
        val settingsButton = findViewById<Button>(R.id.button_settings)

        currentLocationButton.setOnClickListener {
            openCurrentLocationScreen()
        }

        destinationButton.setOnClickListener {
            startActivity(Intent(this, DestinationActivity::class.java))
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        ensureLocationPermissionOnStartup()
    }

    private fun ensureLocationPermissionOnStartup() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
        }
    }

    private fun openCurrentLocationScreen() {
        if (hasLocationPermission()) {
            startActivity(Intent(this, CurrentLocationActivity::class.java))
            return
        }

        pendingActionAfterLocationPermission = {
            startActivity(Intent(this, CurrentLocationActivity::class.java))
        }
        requestLocationPermission()
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
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
            pendingActionAfterLocationPermission?.invoke()
        } else {
            Toast.makeText(
                this,
                R.string.location_permission_required_message,
                Toast.LENGTH_SHORT
            ).show()
        }

        pendingActionAfterLocationPermission = null
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1001
    }
}
