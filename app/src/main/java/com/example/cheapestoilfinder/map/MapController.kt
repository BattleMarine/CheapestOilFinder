package com.example.cheapestoilfinder.map

import android.app.Activity
import com.example.cheapestoilfinder.destination.DestinationSearchSuggestion
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.LocationPoint
import com.example.cheapestoilfinder.map.model.RouteInfo

interface MapController {
    fun bind(activity: Activity)
    fun start()
    fun onResume()
    fun onPause()
    fun moveCamera(point: LocationPoint, zoomLevel: Int)
    fun moveCameraAboveBottomSheet(point: LocationPoint, zoomLevel: Int)
    fun focusCurrentLocation(point: LocationPoint, zoomLevel: Int)
    fun focusStation(point: LocationPoint, zoomLevel: Int)
    fun showStations(stations: List<GasStation>)
    fun showDestinationSearchResults(
        results: List<DestinationSearchSuggestion>,
        bottomPaddingPx: Int = 0
    )
    fun selectDestinationSearchResult(result: DestinationSearchSuggestion)
    fun showRoute(routeInfo: RouteInfo)
    fun setOnStationSelectedListener(listener: ((GasStation) -> Unit)?)
    fun setOnDestinationSearchResultSelectedListener(listener: ((DestinationSearchSuggestion) -> Unit)?)
    fun clearMapObjects()
}
