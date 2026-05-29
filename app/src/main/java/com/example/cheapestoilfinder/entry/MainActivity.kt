package com.example.cheapestoilfinder.entry

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.settings.SettingsActivity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val currentLocationButton = findViewById<Button>(R.id.button_current_location)
        val destinationButton = findViewById<Button>(R.id.button_destination)
        val settingsButton = findViewById<Button>(R.id.button_settings)

        currentLocationButton.setOnClickListener {
            startActivity(Intent(this, CurrentLocationActivity::class.java))
        }

        destinationButton.setOnClickListener {
            startActivity(Intent(this, DestinationActivity::class.java))
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
