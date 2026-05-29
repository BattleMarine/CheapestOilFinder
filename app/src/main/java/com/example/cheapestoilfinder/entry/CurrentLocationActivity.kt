package com.example.cheapestoilfinder.entry

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import com.example.cheapestoilfinder.R
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_current_location)

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }

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

    companion object {
        private const val TAG = "CurrentLocationActivity"
        private val SAMSUNG_STATION = LocationPoint(
            37.5089,
            127.0632,
            "삼성역",
            "서울특별시 강남구 삼성동"
        )
    }
}
