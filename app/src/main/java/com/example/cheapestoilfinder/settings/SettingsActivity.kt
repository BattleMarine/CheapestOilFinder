package com.example.cheapestoilfinder.settings

import android.app.Activity
import android.os.Bundle
import android.widget.ImageButton
import com.example.cheapestoilfinder.R

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
    }
}
