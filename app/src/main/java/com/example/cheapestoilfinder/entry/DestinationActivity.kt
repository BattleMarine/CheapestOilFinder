package com.example.cheapestoilfinder.entry

import android.app.Activity
import android.os.Bundle
import android.widget.ImageButton
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.map.KakaoMapController
import com.example.cheapestoilfinder.map.MapScreenMode

class DestinationActivity : Activity() {
    private var mapController: KakaoMapController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destination)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }

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
}
