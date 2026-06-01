package com.example.cheapestoilfinder.map

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.CompetitionType
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
    private var stationLabelLayer: LabelLayer? = null
    private var currentLocationLabelLayer: LabelLayer? = null
    private var currentLocationLabel: Label? = null
    private var stationLabelStyles: LabelStyles? = null
    private var currentLocationLabelStyles: LabelStyles? = null
    private val stationLabels = mutableListOf<Label>()
    private val renderedStations = mutableListOf<GasStation>()
    private var renderedRoute: RouteInfo? = null
    private var pendingCameraPoint: LocationPoint? = null
    private var pendingCameraZoomLevel: Int = 15
    private var pendingCameraShowsCurrentLocation = false
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
                    stationLabelLayer = null
                    currentLocationLabelLayer = null
                    stationLabelStyles = null
                    currentLocationLabelStyles = null
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
                    ensureLabelLayers()
                    Log.i(TAG, "Kakao map ready for mode: $screenMode")

                    mapPlaceholder?.visibility = View.GONE

                    if (hasPendingCameraMove && pendingCameraPoint != null) {
                        applyCameraMove(
                            pendingCameraPoint!!,
                            pendingCameraZoomLevel,
                            pendingCameraShowsCurrentLocation
                        )
                    } else if (screenMode == MapScreenMode.CURRENT_LOCATION) {
                        applyCameraMove(
                            LocationPoint(37.5665, 126.9780, "서울시청", "서울특별시 중구 세종대로 110"),
                            15,
                            true
                        )
                    } else {
                        applyCameraMove(
                            LocationPoint(37.5665, 126.9780, "출발지", "서울특별시 중구 세종대로 110"),
                            13,
                            false
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
        pendingCameraShowsCurrentLocation = false
        hasPendingCameraMove = true

        if (kakaoMap == null) {
            Log.d(TAG, "moveCamera queued. kakaoMap is not ready yet.")
            return
        }

        applyCameraMove(point, zoomLevel, false)
    }

    override fun focusCurrentLocation(point: LocationPoint, zoomLevel: Int) {
        pendingCameraPoint = point
        pendingCameraZoomLevel = zoomLevel
        pendingCameraShowsCurrentLocation = true
        hasPendingCameraMove = true

        if (kakaoMap == null) {
            Log.d(TAG, "focusCurrentLocation queued. kakaoMap is not ready yet.")
            return
        }

        applyCameraMove(point, zoomLevel, true)
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

    private fun ensureLabelLayers() {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return

        if (stationLabelLayer == null) {
            stationLabelLayer = labelManager.addLayer(
                LabelLayerOptions.from("station-layer")
                    .setVisible(true)
                    .setCompetitionType(CompetitionType.None)
                    .setZOrder(5000)
            )
        }

        if (currentLocationLabelLayer == null) {
            currentLocationLabelLayer = labelManager.addLayer(
                LabelLayerOptions.from("current-location-layer")
                    .setVisible(true)
                    .setCompetitionType(CompetitionType.None)
                    .setZOrder(6000)
            )
        }

        if (stationLabelStyles == null) {
            stationLabelStyles = labelManager.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(buildStationMarkerBitmap())
                        .setAnchorPoint(0.5f, 1.0f)
                )
            )
        }

        if (currentLocationLabelStyles == null) {
            currentLocationLabelStyles = labelManager.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(buildCurrentLocationMarkerBitmap())
                        .setAnchorPoint(0.5f, 0.5f)
                )
            )
        }
    }

    private fun applyCameraMove(point: LocationPoint, zoomLevel: Int, showCurrentLocationMarker: Boolean) {
        val map = kakaoMap ?: return

        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(point.latitude, point.longitude)
            )
        )
        map.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel))
        Log.d(TAG, "moveCamera requested: ${point.latitude}, ${point.longitude}")

        if (showCurrentLocationMarker || screenMode == MapScreenMode.CURRENT_LOCATION) {
            renderCurrentLocationMarker(point)
        }
    }

    private fun renderStations() {
        clearRenderedStations()
        ensureLabelLayers()
        val layer = stationLabelLayer ?: return

        layer.setVisible(true)
        for (station in renderedStations) {
            val point = station.locationPoint
            val label = layer.addLabel(
                LabelOptions.from(
                    LatLng.from(point.latitude, point.longitude)
                )
                    .setStyles(stationLabelStyles ?: return)
                    .setVisible(true)
            )
            stationLabels.add(label)
        }

        Log.d(TAG, "renderStations finished. labels=${stationLabels.size}")
    }

    private fun renderCurrentLocationMarker(point: LocationPoint) {
        clearCurrentLocationLabel()
        ensureLabelLayers()
        val layer = currentLocationLabelLayer ?: return

        Log.d(TAG, "renderCurrentLocationMarker requested: ${point.latitude}, ${point.longitude}")
        val options = LabelOptions.from(
            LatLng.from(point.latitude, point.longitude)
        )
            .setStyles(currentLocationLabelStyles ?: return)
            .setVisible(true)
            .setRank(Long.MAX_VALUE)

        currentLocationLabel = layer.addLabel(options)
        layer.setVisible(true)
        Log.d(TAG, "renderCurrentLocationMarker finished. label=${currentLocationLabel != null}")
    }

    private fun clearRenderedStations() {
        stationLabels.forEach { it.remove() }
        stationLabels.clear()
    }

    private fun clearCurrentLocationLabel() {
        currentLocationLabel?.remove()
        currentLocationLabel = null
    }

    private fun buildStationMarkerBitmap(): Bitmap {
        return createMarkerBitmap(fillColor = Color.parseColor("#F97316"), outerColor = Color.WHITE, sizePx = 72, innerRatio = 0.48f)
    }

    private fun buildCurrentLocationMarkerBitmap(): Bitmap {
        return createMarkerBitmap(fillColor = Color.parseColor("#2563EB"), outerColor = Color.WHITE, sizePx = 64, innerRatio = 0.42f)
    }

    private fun createMarkerBitmap(
        fillColor: Int,
        outerColor: Int,
        sizePx: Int,
        innerRatio: Float
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outerColor
            style = Paint.Style.FILL
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }

        val center = sizePx / 2f
        canvas.drawCircle(center, center, center, outerPaint)
        canvas.drawCircle(center, center, center * innerRatio, innerPaint)
        return bitmap
    }

    companion object {
        private const val TAG = "KakaoMapController"
    }
}
