package com.example.cheapestoilfinder.map

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.LocationPoint
import com.example.cheapestoilfinder.map.model.RouteInfo
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

class KakaoMapController(
    private val containerResId: Int,
    private val screenMode: MapScreenMode
) : MapController {
    private var activity: Activity? = null
    private var mapContainer: FrameLayout? = null
    private var mapPlaceholder: View? = null
    private var mapPlaceholderBody: TextView? = null
    private var mapView: MapView? = null
    private var kakaoMap: KakaoMap? = null
    private var labelLayer: LabelLayer? = null
    private var stationLabelStyles: LabelStyles? = null
    private var currentLocationLabelStyles: LabelStyles? = null
    private var currentLocationLabel: Label? = null
    private val stationLabels = mutableListOf<Label>()
    private val renderedStations = mutableListOf<GasStation>()
    private var renderedRoute: RouteInfo? = null
    private var pendingCameraPoint: LocationPoint? = null
    private var pendingCameraZoomLevel: Int = 15
    private var hasPendingCameraMove = false

    override fun bind(activity: Activity) {
        this.activity = activity
        mapContainer = activity.findViewById(containerResId)
        mapPlaceholder = activity.findViewById(R.id.map_placeholder)
        mapPlaceholderBody = activity.findViewById(R.id.map_placeholder_body)
    }

    override fun start() {
        val container = mapContainer ?: run {
            Log.w(TAG, "Map container is not bound yet.")
            return
        }

        if (!KakaoMapConfig.isConfigured()) {
            Log.i(TAG, "Kakao native app key is empty. Map startup is deferred.")
            return
        }

        if (mapView != null) {
            return
        }

        mapView = MapView(container.context).also { view ->
            container.addView(
                view,
                0,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        mapView?.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    Log.i(TAG, "Kakao map destroyed for mode: $screenMode")
                    kakaoMap = null
                    labelLayer = null
                    mapPlaceholder?.visibility = View.VISIBLE
                }

                override fun onMapError(error: Exception) {
                    Log.e(TAG, "Kakao map error", error)
                    mapPlaceholderBody?.setText(R.string.map_auth_error_message)
                    mapPlaceholder?.visibility = View.VISIBLE
                    activity?.let {
                        Toast.makeText(it, R.string.map_auth_error_message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(readyMap: KakaoMap) {
                    kakaoMap = readyMap
                    labelLayer = kakaoMap?.labelManager?.layer
                    ensureLabelStyles()
                    Log.i(TAG, "Kakao map ready for mode: $screenMode")

                    mapPlaceholder?.visibility = View.GONE

                    if (hasPendingCameraMove && pendingCameraPoint != null) {
                        applyCameraMove(pendingCameraPoint!!, pendingCameraZoomLevel)
                    } else if (screenMode == MapScreenMode.CURRENT_LOCATION) {
                        applyCameraMove(
                            LocationPoint(37.5665, 126.9780, "서울시청", "서울특별시 중구 세종대로 110"),
                            15
                        )
                    } else {
                        applyCameraMove(
                            LocationPoint(37.5665, 126.9780, "출발지", "서울특별시 중구 세종대로 110"),
                            13
                        )
                    }

                    renderStations()
                }
            }
        )
    }

    override fun onResume() {
        mapView?.resume()
    }

    override fun onPause() {
        mapView?.pause()
    }

    override fun moveCamera(point: LocationPoint, zoomLevel: Int) {
        pendingCameraPoint = point
        pendingCameraZoomLevel = zoomLevel
        hasPendingCameraMove = true

        if (kakaoMap == null) {
            Log.d(TAG, "moveCamera queued. kakaoMap is not ready yet.")
            return
        }

        applyCameraMove(point, zoomLevel)
    }

    override fun showStations(stations: List<GasStation>) {
        renderedStations.clear()
        renderedStations.addAll(stations)
        Log.d(TAG, "showStations requested. size=${renderedStations.size}")
        renderStations()
    }

    override fun showRoute(routeInfo: RouteInfo) {
        renderedRoute = routeInfo
        Log.d(TAG, "showRoute requested.")
    }

    override fun clearMapObjects() {
        clearRenderedStations()
        clearCurrentLocationLabel()
        renderedStations.clear()
        renderedRoute = null
        Log.d(TAG, "clearMapObjects requested.")
    }

    private fun ensureLabelStyles() {
        val map = kakaoMap ?: return

        if (stationLabelStyles == null) {
            stationLabelStyles = map.labelManager?.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(R.drawable.ic_marker_station)
                        .setAnchorPoint(0.5f, 1.0f)
                )
            )
        }

        if (currentLocationLabelStyles == null) {
            currentLocationLabelStyles = map.labelManager?.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(R.drawable.ic_marker_current_location)
                        .setAnchorPoint(0.5f, 1.0f)
                )
            )
        }
    }

    private fun applyCameraMove(point: LocationPoint, zoomLevel: Int) {
        val map = kakaoMap ?: return

        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(point.latitude, point.longitude)
            )
        )
        map.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel))
        Log.d(TAG, "moveCamera requested: ${point.latitude}, ${point.longitude}")

        if (screenMode == MapScreenMode.CURRENT_LOCATION) {
            renderCurrentLocationMarker(point)
        }
    }

    private fun renderStations() {
        val map = kakaoMap ?: return
        val layer = labelLayer ?: return

        clearRenderedStations()
        ensureLabelStyles()

        for (station in renderedStations) {
            val point = station.locationPoint
            val label = layer.addLabel(
                LabelOptions.from(
                    station.id,
                    LatLng.from(point.latitude, point.longitude)
                ).setStyles(stationLabelStyles)
            )
            stationLabels.add(label)
        }

        Log.d(TAG, "renderStations finished. labels=${stationLabels.size}")
    }

    private fun renderCurrentLocationMarker(point: LocationPoint) {
        val map = kakaoMap ?: return
        val layer = labelLayer ?: return
        if (screenMode != MapScreenMode.CURRENT_LOCATION) return

        clearCurrentLocationLabel()
        ensureLabelStyles()

        currentLocationLabel = layer.addLabel(
            LabelOptions.from(
                "current-location",
                LatLng.from(point.latitude, point.longitude)
            ).setStyles(currentLocationLabelStyles)
        )
        Log.d(TAG, "renderCurrentLocationMarker finished.")
    }

    private fun clearRenderedStations() {
        stationLabels.forEach { it.remove() }
        stationLabels.clear()
    }

    private fun clearCurrentLocationLabel() {
        currentLocationLabel?.remove()
        currentLocationLabel = null
    }

    companion object {
        private const val TAG = "KakaoMapController"
    }
}
