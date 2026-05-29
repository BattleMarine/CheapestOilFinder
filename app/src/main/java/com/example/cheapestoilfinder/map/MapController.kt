package com.example.cheapestoilfinder.map

import android.app.Activity
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.LocationPoint
import com.example.cheapestoilfinder.map.model.RouteInfo

interface MapController {
    fun bind(activity: Activity)
    fun start()
    fun onResume()
    fun onPause()
    fun moveCamera(point: LocationPoint, zoomLevel: Int)
    fun showStations(stations: List<GasStation>)
    fun showRoute(routeInfo: RouteInfo)
    fun clearMapObjects()
}
