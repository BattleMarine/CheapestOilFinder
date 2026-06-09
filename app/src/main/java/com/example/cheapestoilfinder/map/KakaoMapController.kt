package com.example.cheapestoilfinder.map

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.destination.DestinationSearchSuggestion
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.LocationPoint
import com.example.cheapestoilfinder.map.model.RouteInfo
import com.example.cheapestoilfinder.station.BrandLogoResolver
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraPosition
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.CompetitionType
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelManager
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.OrderingType
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.Polyline
import com.kakao.vectormap.shape.PolylineOptions
import com.kakao.vectormap.shape.ShapeLayer
import com.kakao.vectormap.shape.ShapeLayerOptions
import com.kakao.vectormap.shape.ShapeLayerPass
import com.kakao.vectormap.shape.ShapeManager
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

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
    private var shapeManager: ShapeManager? = null
    private var currentLocationRadiusLayer: ShapeLayer? = null
    private var currentLocationRadiusPolyline: Polyline? = null
    private var routeLayer: ShapeLayer? = null
    private var routePolyline: Polyline? = null
    private var stationLabelLayer: LabelLayer? = null
    private var stationPriceLabelLayer: LabelLayer? = null
    private var destinationSearchLabelLayer: LabelLayer? = null
    private var routeDistanceLabelLayer: LabelLayer? = null
    private var currentLocationLabelLayer: LabelLayer? = null
    private var currentLocationLabel: Label? = null
    private var currentLocationLabelStyles: LabelStyles? = null
    private var destinationSearchDefaultLabelStyles: LabelStyles? = null
    private var destinationSearchSelectedLabelStyles: LabelStyles? = null
    private var destinationSearchCommittedLabelStyles: LabelStyles? = null
    private var routeDistanceLabel: Label? = null
    private val stationLabelStylesByResId = mutableMapOf<Int, LabelStyles>()
    private val stationLabelsById = mutableMapOf<String, Label>()
    private val stationPriceLabelsById = mutableMapOf<String, Label>()
    private val destinationSearchLabelsByKey = mutableMapOf<String, Label>()
    private val renderedStations = mutableListOf<GasStation>()
    private val renderedDestinationSearchResults = mutableListOf<DestinationSearchSuggestion>()
    private var destinationSearchResultsBottomPaddingPx: Int = 0
    private var lastCurrentLocationPoint: LocationPoint? = null
    private var lastCameraPoint: LocationPoint? = null
    private var lastCameraShowsCurrentLocation: Boolean = false
    private var renderedRoute: RouteInfo? = null
    private var pendingCameraPoint: LocationPoint? = null
    private var pendingCameraZoomLevel: Int = 15
    private var pendingCameraPaddingBottom: Int = 0
    private var pendingCameraShowsCurrentLocation = false
    private var hasPendingCameraMove = false
    private var currentZoomLevel: Int = 15
    private var currentStationMarkerDisplayMode: StationMarkerDisplayMode = StationMarkerDisplayMode.ALL
    private var currentPriceDisplayMode: StationPriceDisplayMode = StationPriceDisplayMode.FULL
    private var currentPriceOpacity: Float = 1f
    private var currentPriceScale: Float = 1f
    private var stationSelectedListener: ((GasStation) -> Unit)? = null
    private var destinationSearchResultSelectedListener: ((DestinationSearchSuggestion) -> Unit)? = null
    private var selectedDestinationSearchResultKey: String? = null
    private var committedDestinationSearchResultKey: String? = null

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
                    shapeManager = null
                    currentLocationRadiusLayer = null
                    currentLocationRadiusPolyline = null
                    routeLayer = null
                    routePolyline = null
                    stationLabelLayer = null
                    stationPriceLabelLayer = null
                    destinationSearchLabelLayer = null
                    routeDistanceLabelLayer = null
                    routeDistanceLabel = null
                    currentLocationLabelLayer = null
                    stationLabelStylesByResId.clear()
                    currentLocationLabelStyles = null
                    destinationSearchDefaultLabelStyles = null
                    destinationSearchSelectedLabelStyles = null
                    destinationSearchCommittedLabelStyles = null
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
                    registerCameraListeners(readyMap)
                    registerStationClickListener(readyMap)
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

                    renderStationOverlaysForZoom(currentZoomLevel)
                    renderRoute(renderedRoute)
                    renderedRoute?.let {
                        renderRouteDistanceLabel(it, parseRoutePolyline(it.polyline))
                    }
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
        pendingCameraPaddingBottom = 0
        pendingCameraShowsCurrentLocation = false
        hasPendingCameraMove = true

        if (kakaoMap == null) {
            Log.d(TAG, "moveCamera queued. kakaoMap is not ready yet.")
            return
        }

        applyCameraMove(point, zoomLevel, false)
    }

    override fun moveCameraAboveBottomSheet(point: LocationPoint, zoomLevel: Int) {
        pendingCameraPoint = point
        pendingCameraZoomLevel = zoomLevel
        // 하단 절반 시트가 열려 있을 때 선택 지점이 가려지지 않도록
        // 지도상 목표 위치를 전체 화면 기준 약 25% 지점으로 올립니다.
        val height = mapContainer?.height ?: 0
        pendingCameraPaddingBottom = height / 2
        pendingCameraShowsCurrentLocation = false
        hasPendingCameraMove = true

        if (kakaoMap == null) {
            Log.d(TAG, "moveCameraAboveBottomSheet queued. kakaoMap is not ready yet.")
            return
        }

        applyCameraMove(point, zoomLevel, false)
    }

    override fun focusCurrentLocation(point: LocationPoint, zoomLevel: Int) {
        pendingCameraPoint = point
        pendingCameraZoomLevel = zoomLevel
        pendingCameraPaddingBottom = 0
        pendingCameraShowsCurrentLocation = true
        hasPendingCameraMove = true

        if (kakaoMap == null) {
            Log.d(TAG, "focusCurrentLocation queued. kakaoMap is not ready yet.")
            return
        }

        applyCameraMove(point, zoomLevel, true)
    }

    override fun focusStation(point: LocationPoint, zoomLevel: Int) {
        pendingCameraPoint = point
        pendingCameraZoomLevel = zoomLevel
        // 주유소 상세 정보를 아래에서 위로 올릴 때, 주유소 위치가 가려지지 않도록
        // 지도의 중심을 상단 25% 지점으로 옮기기 위해 하단 패딩을 전체 높이의 절반으로 설정합니다.
        val height = mapContainer?.height ?: 0
        pendingCameraPaddingBottom = height / 2
        pendingCameraShowsCurrentLocation = false
        hasPendingCameraMove = true

        if (kakaoMap == null) {
            Log.d(TAG, "focusStation queued. kakaoMap is not ready yet.")
            return
        }

        applyCameraMove(point, zoomLevel, false)
    }

    fun zoomInFixedStep() {
        adjustFixedZoom(zoomingIn = true)
    }

    fun zoomOutFixedStep() {
        adjustFixedZoom(zoomingIn = false)
    }

    fun zoomInOneLevel() {
        adjustOneLevelZoom(delta = 1)
    }

    fun zoomOutOneLevel() {
        adjustOneLevelZoom(delta = -1)
    }

    override fun showStations(stations: List<GasStation>) {
        renderedStations.clear()
        renderedStations.addAll(stations)
        Log.d(TAG, "showStations requested. size=${renderedStations.size}")
        renderStationOverlaysForZoom(currentZoomLevel)
        fitCurrentLocationAndStations()
    }

    override fun showDestinationSearchResults(
        results: List<DestinationSearchSuggestion>,
        bottomPaddingPx: Int
    ) {
        val requestedCount = results.size
        renderedDestinationSearchResults.clear()
        renderedDestinationSearchResults.addAll(
            results.filter { it.latitude != null && it.longitude != null }
        )
        if (renderedDestinationSearchResults.none { it.destinationSearchKey() == selectedDestinationSearchResultKey }) {
            selectedDestinationSearchResultKey = null
        }
        if (renderedDestinationSearchResults.none { it.destinationSearchKey() == committedDestinationSearchResultKey }) {
            committedDestinationSearchResultKey = null
        }
        destinationSearchResultsBottomPaddingPx = bottomPaddingPx.coerceAtLeast(0)
        Log.d(TAG, "showDestinationSearchResults requested. requested=$requestedCount, drawable=${renderedDestinationSearchResults.size}")
        renderDestinationSearchResults()
        fitDestinationSearchResults()
    }

    override fun selectDestinationSearchResult(result: DestinationSearchSuggestion) {
        selectedDestinationSearchResultKey = result.destinationSearchKey()
        committedDestinationSearchResultKey = null
        Log.d(TAG, "selectDestinationSearchResult requested. key=$selectedDestinationSearchResultKey")
        renderDestinationSearchResults()
    }

    fun commitDestinationSearchResult(result: DestinationSearchSuggestion) {
        val key = result.destinationSearchKey()
        selectedDestinationSearchResultKey = key
        committedDestinationSearchResultKey = key
        renderedDestinationSearchResults.clear()
        renderedDestinationSearchResults.add(result)
        destinationSearchResultsBottomPaddingPx = 0
        Log.d(TAG, "commitDestinationSearchResult requested. key=$key")
        renderDestinationSearchResults()
    }

    fun restoreDestinationSearchResults(
        results: List<DestinationSearchSuggestion>,
        selectedResult: DestinationSearchSuggestion?
    ) {
        renderedDestinationSearchResults.clear()
        renderedDestinationSearchResults.addAll(
            results.filter { it.latitude != null && it.longitude != null }
        )
        selectedDestinationSearchResultKey = selectedResult?.destinationSearchKey()
        committedDestinationSearchResultKey = null
        Log.d(TAG, "restoreDestinationSearchResults requested. size=${renderedDestinationSearchResults.size}")
        renderDestinationSearchResults()
    }

    fun clearRoute() {
        renderedRoute = null
        clearRenderedRoute()
    }

    override fun showRoute(routeInfo: RouteInfo) {
        renderedRoute = routeInfo
        Log.d(TAG, "showRoute requested.")
        renderRoute(routeInfo)
        fitRoute(routeInfo)
        renderRouteDistanceLabel(routeInfo, parseRoutePolyline(routeInfo.polyline))
    }

    override fun setOnStationSelectedListener(listener: ((GasStation) -> Unit)?) {
        stationSelectedListener = listener
    }

    override fun setOnDestinationSearchResultSelectedListener(listener: ((DestinationSearchSuggestion) -> Unit)?) {
        destinationSearchResultSelectedListener = listener
    }

    override fun clearMapObjects() {
        clearRenderedStations()
        clearRenderedStationPrices()
        clearRenderedDestinationSearchResults()
        clearRenderedRoute()
        clearCurrentLocationLabel()
        clearCurrentLocationRadius()
        renderedStations.clear()
        renderedDestinationSearchResults.clear()
        renderedRoute = null
        selectedDestinationSearchResultKey = null
        destinationSearchResultsBottomPaddingPx = 0
        Log.d(TAG, "clearMapObjects requested.")
    }

    private fun ensureLabelLayers() {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return
        val shapeManager = map.shapeManager ?: return

        if (this.shapeManager == null) {
            this.shapeManager = shapeManager
        }

        if (currentLocationLabelLayer == null) {
            currentLocationLabelLayer = labelManager.addLayer(
                LabelLayerOptions.from("current-location-layer")
                    .setVisible(true)
                    .setCompetitionType(CompetitionType.None)
                    .setZOrder(6000)
            )
        }

        if (stationLabelLayer == null) {
            stationLabelLayer = labelManager.addLayer(
                LabelLayerOptions.from("station-icon-layer")
                    .setVisible(true)
                    .setCompetitionType(CompetitionType.None)
                    .setOrderingType(OrderingType.Rank)
                    .setZOrder(5000)
            )
        }

        if (stationPriceLabelLayer == null) {
            stationPriceLabelLayer = labelManager.addLayer(
                LabelLayerOptions.from("station-price-layer")
                    .setVisible(true)
                    .setCompetitionType(CompetitionType.None)
                    .setOrderingType(OrderingType.Rank)
                    .setZOrder(5001)
            )
        }

        if (destinationSearchLabelLayer == null) {
            destinationSearchLabelLayer = labelManager.addLayer(
                LabelLayerOptions.from("destination-search-layer")
                    .setVisible(true)
                    .setCompetitionType(CompetitionType.None)
                    .setOrderingType(OrderingType.Rank)
                    .setZOrder(8600)
            )
        }

        if (routeDistanceLabelLayer == null) {
            routeDistanceLabelLayer = labelManager.addLayer(
                LabelLayerOptions.from("route-distance-layer")
                    .setVisible(true)
                    .setCompetitionType(CompetitionType.None)
                    .setZOrder(8700)
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

        if (currentLocationRadiusLayer == null) {
            currentLocationRadiusLayer = shapeManager.addLayer(
                ShapeLayerOptions.from("current-location-radius-layer", 9000, ShapeLayerPass.Overlay)
                    .setVisible(true)
            )
        }

        if (routeLayer == null) {
            routeLayer = shapeManager.addLayer(
                ShapeLayerOptions.from("route-line-layer", 8500, ShapeLayerPass.Overlay)
                    .setVisible(true)
            )
        }
    }

    private fun registerCameraListeners(map: KakaoMap) {
        map.setOnCameraMoveEndListener(object : KakaoMap.OnCameraMoveEndListener {
            override fun onCameraMoveEnd(
                kakaoMap: KakaoMap,
                cameraPosition: CameraPosition,
                gestureType: GestureType
            ) {
                currentZoomLevel = cameraPosition.zoomLevel
                val desiredMarkerMode = resolveStationMarkerDisplayMode(currentZoomLevel)
                val desiredOpacity = resolvePriceOpacity(currentZoomLevel, desiredMarkerMode)
                val desiredScale = resolvePriceScale(currentZoomLevel, desiredMarkerMode)
                Log.d(
                    TAG,
                    "Camera move ended. zoom=${cameraPosition.zoomLevel}, alpha=$desiredOpacity, scale=$desiredScale, markerMode=$desiredMarkerMode, gesture=$gestureType"
                )
                if ((desiredOpacity != currentPriceOpacity ||
                        desiredScale != currentPriceScale ||
                        desiredMarkerMode != currentStationMarkerDisplayMode) &&
                    renderedStations.isNotEmpty()
                ) {
                    renderStationOverlaysForZoom(currentZoomLevel)
                } else if (renderedStations.isNotEmpty()) {
                    renderStationOverlaysForZoom(currentZoomLevel)
                }

                if (renderedDestinationSearchResults.isNotEmpty()) {
                    renderDestinationSearchResults()
                }
            }
        })
    }

    private fun registerStationClickListener(map: KakaoMap) {
        map.setOnLabelClickListener(object : KakaoMap.OnLabelClickListener {
            override fun onLabelClicked(
                kakaoMap: KakaoMap,
                layer: LabelLayer,
                label: Label
            ): Boolean {
                val tag = label.tag
                val station = tag as? GasStation
                if (station != null) {
                    Log.d(TAG, "station label clicked: ${station.id}")
                    stationSelectedListener?.invoke(station)
                    return true
                }

                val destinationSearchResult = tag as? DestinationSearchSuggestion
                if (destinationSearchResult != null) {
                    Log.d(TAG, "destination search label clicked: ${destinationSearchResult.displayText}")
                    destinationSearchResultSelectedListener?.invoke(destinationSearchResult)
                    return true
                }

                return false
            }
        })
    }

    private fun applyCameraMove(point: LocationPoint, zoomLevel: Int, showCurrentLocationMarker: Boolean) {
        val map = kakaoMap ?: return
        lastCurrentLocationPoint = point
        lastCameraPoint = point
        lastCameraShowsCurrentLocation = showCurrentLocationMarker

        // Apply pending padding
        map.setPadding(0, 0, 0, pendingCameraPaddingBottom)

        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(point.latitude, point.longitude)
            )
        )
        map.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel))
        currentZoomLevel = zoomLevel
        Log.d(TAG, "moveCamera requested: ${point.latitude}, ${point.longitude}")

        if (showCurrentLocationMarker) {
            renderCurrentLocationMarker(point)
        }

        if (showCurrentLocationMarker && screenMode == MapScreenMode.CURRENT_LOCATION) {
            renderCurrentLocationRadius(point, CURRENT_LOCATION_RADIUS_METERS)
        }
    }

    private fun adjustFixedZoom(zoomingIn: Boolean) {
        val targetZoom = if (zoomingIn) {
            nextFixedZoomIn(currentZoomLevel)
        } else {
            nextFixedZoomOut(currentZoomLevel)
        }

        if (targetZoom == currentZoomLevel) {
            return
        }

        val point = lastCameraPoint ?: pendingCameraPoint ?: return
        if (kakaoMap == null) {
            pendingCameraPoint = point
            pendingCameraZoomLevel = targetZoom
            pendingCameraShowsCurrentLocation = lastCameraShowsCurrentLocation
            hasPendingCameraMove = true
            return
        }

        applyCameraMove(point, targetZoom, lastCameraShowsCurrentLocation)
    }

    private fun nextFixedZoomIn(currentZoom: Int): Int {
        return fixedZoomLevels.firstOrNull { it > currentZoom } ?: fixedZoomLevels.last()
    }

    private fun nextFixedZoomOut(currentZoom: Int): Int {
        return fixedZoomLevels.lastOrNull { it < currentZoom } ?: fixedZoomLevels.first()
    }

    private fun adjustOneLevelZoom(delta: Int) {
        val targetZoom = (currentZoomLevel + delta)
            .coerceIn(MIN_FREE_ZOOM_LEVEL, MAX_FREE_ZOOM_LEVEL)

        if (targetZoom == currentZoomLevel) {
            return
        }

        val map = kakaoMap ?: return
        map.moveCamera(CameraUpdateFactory.zoomTo(targetZoom))
        currentZoomLevel = targetZoom
        Log.d(TAG, "zoom one level requested. targetZoom=$targetZoom")
    }

    private fun renderStationOverlaysForZoom(zoomLevel: Int) {
        ensureLabelLayers()
        val layer = stationLabelLayer ?: return

        val markerMode = resolveStationMarkerDisplayMode(zoomLevel)
        val desiredPriceMode = if (
            markerMode == StationMarkerDisplayMode.TOP_FIVE_ONLY ||
            markerMode == StationMarkerDisplayMode.TOP_TWENTY ||
            markerMode == StationMarkerDisplayMode.TOP_FIFTY
        ) {
            StationPriceDisplayMode.GASOLINE_ONLY
        } else {
            if (zoomLevel >= 15) {
                StationPriceDisplayMode.FULL
            } else {
                StationPriceDisplayMode.GASOLINE_ONLY
            }
        }
        val desiredOpacity = resolvePriceOpacity(zoomLevel, markerMode)
        val desiredScale = resolvePriceScale(zoomLevel, markerMode)
        currentStationMarkerDisplayMode = markerMode
        currentPriceDisplayMode = desiredPriceMode
        currentPriceOpacity = desiredOpacity
        currentPriceScale = desiredScale

        val stationsToRender = resolveStationsForZoom(markerMode)
        val orderedStations = orderStationsByScreenPosition(stationsToRender)
        val visibleStationIds = orderedStations.mapTo(hashSetOf()) { it.id }

        layer.setVisible(true)
        removeStaleStationLabels(visibleStationIds)
        Log.d(TAG, "renderStations requested. count=${orderedStations.size}, mode=$markerMode")
        orderedStations.forEachIndexed { index, station ->
            val point = station.locationPoint
            Log.d(TAG, "renderStation marker: ${station.id} @ ${point.latitude}, ${point.longitude}")
            val stationLogoStyles = getStationLogoStyles(station.brand) ?: return@forEachIndexed
            val stationRank = 1000L + index
            val label = stationLabelsById[station.id] ?: layer.addLabel(
                LabelOptions.from(
                    LatLng.from(point.latitude, point.longitude)
                )
                    .setRank(stationRank)
                    .setStyles(stationLogoStyles)
                    .setTag(station)
                    .setVisible(true)
            ).also {
                stationLabelsById[station.id] = it
                it.setClickable(true)
            }
            label.setTag(station)
            label.setRank(stationRank)
            label.moveTo(LatLng.from(point.latitude, point.longitude))
            label.changeStyles(stationLogoStyles)
            label.show()
        }

        renderStationPriceLabelsForZoom(
            zoomLevel = zoomLevel,
            stationsToRender = orderedStations,
            desiredMode = desiredPriceMode,
            desiredOpacity = desiredOpacity,
            desiredScale = desiredScale
        )
        Log.d(TAG, "renderStations finished. iconLabels=${stationLabelsById.size}, priceLabels=${stationPriceLabelsById.size}")
    }

    private fun renderStationPriceLabelsForZoom(
        zoomLevel: Int,
        stationsToRender: List<GasStation>,
        desiredMode: StationPriceDisplayMode,
        desiredOpacity: Float,
        desiredScale: Float
    ) {
        ensureLabelLayers()
        val priceLayer = stationPriceLabelLayer ?: return
        val labelManager = kakaoMap?.labelManager ?: return

        if (desiredMode == StationPriceDisplayMode.HIDDEN || desiredOpacity <= 0.01f) {
            priceLayer.setVisible(false)
            clearRenderedStationPrices()
            Log.d(TAG, "Station price labels hidden at zoom=$zoomLevel")
            return
        }

        priceLayer.setVisible(true)
        removeStaleStationPriceLabels(stationsToRender.mapTo(hashSetOf()) { it.id })
        val showDiesel = zoomLevel >= 15
        Log.d(TAG, "Rendering station price labels mode=$desiredMode alpha=$desiredOpacity scale=$desiredScale zoom=$zoomLevel count=${renderedStations.size}")

        stationsToRender.forEachIndexed { index, station ->
            val point = station.locationPoint
            val priceLines = buildFuelPriceLines(station, showDiesel = showDiesel)
            if (priceLines.isEmpty()) {
                return@forEachIndexed
            }

            val stationRank = 1000L + index
            val priceLabelStyles = labelManager.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(buildFuelPriceBitmap(priceLines, desiredOpacity, desiredScale))
                        .setAnchorPoint(0.5f, 1.0f)
                )
            )
            val priceLabel = stationPriceLabelsById[station.id] ?: priceLayer.addLabel(
                LabelOptions.from(
                    LatLng.from(point.latitude, point.longitude)
                )
                    .setRank(stationRank)
                    .setStyles(priceLabelStyles)
                    .setVisible(true)
            ).also {
                stationPriceLabelsById[station.id] = it
            }
            priceLabel.setRank(stationRank)
            priceLabel.moveTo(LatLng.from(point.latitude, point.longitude))
            priceLabel.changeStyles(priceLabelStyles)
            priceLabel.show()
        }

        Log.d(TAG, "renderStationPriceLabelsForZoom finished. priceLabels=${stationPriceLabelsById.size}")
    }

    private fun renderDestinationSearchResults() {
        ensureLabelLayers()
        val layer = destinationSearchLabelLayer ?: return
        val labelManager = kakaoMap?.labelManager ?: return

        if (renderedDestinationSearchResults.isEmpty()) {
            clearRenderedDestinationSearchResults()
            return
        }

        layer.setVisible(true)
        val orderedResults = orderDestinationSearchResultsByScreenPosition(renderedDestinationSearchResults)
        val visibleKeys = orderedResults.mapTo(hashSetOf()) { it.destinationSearchKey() }
        removeStaleDestinationSearchLabels(visibleKeys)

        orderedResults.forEachIndexed { index, result ->
            val point = result.toLocationPointOrNull() ?: return@forEachIndexed
            val key = result.destinationSearchKey()
            val isCommitted = key == committedDestinationSearchResultKey
            val isSelected = key == selectedDestinationSearchResultKey
            val styles = if (isCommitted) {
                destinationSearchCommittedLabelStyles ?: labelManager.addLabelStyles(
                    LabelStyles.from(
                        LabelStyle.from(buildDestinationSearchCommittedMarkerBitmap())
                            .setAnchorPoint(0.3f, 0.82f)
                    )
                )!!.also {
                    destinationSearchCommittedLabelStyles = it
                }
            } else if (isSelected) {
                destinationSearchSelectedLabelStyles ?: labelManager.addLabelStyles(
                    LabelStyles.from(
                        LabelStyle.from(buildDestinationSearchSelectedMarkerBitmap())
                            .setAnchorPoint(0.5f, 0.7f)
                    )
                )!!.also {
                    destinationSearchSelectedLabelStyles = it
                }
            } else {
                destinationSearchDefaultLabelStyles ?: labelManager.addLabelStyles(
                    LabelStyles.from(
                        LabelStyle.from(buildDestinationSearchDefaultMarkerBitmap())
                            .setAnchorPoint(0.5f, 0.5f)
                    )
                )!!.also {
                    destinationSearchDefaultLabelStyles = it
                }
            }

            val rank = if (isCommitted || isSelected) Long.MAX_VALUE - 1 else 2000L + index
            val label = destinationSearchLabelsByKey[key] ?: layer.addLabel(
                LabelOptions.from(LatLng.from(point.latitude, point.longitude))
                    .setRank(rank)
                    .setStyles(styles)
                    .setTag(result)
                    .setVisible(true)
            ).also {
                destinationSearchLabelsByKey[key] = it
            }

            label.setTag(result)
            label.setRank(rank)
            label.moveTo(LatLng.from(point.latitude, point.longitude))
            label.changeStyles(styles)
            label.show()
        }

        Log.d(TAG, "renderDestinationSearchResults finished. labels=${destinationSearchLabelsByKey.size}")
    }

    private fun fitDestinationSearchResults() {
        val map = kakaoMap ?: return
        if (renderedDestinationSearchResults.isEmpty()) {
            return
        }

        val points = renderedDestinationSearchResults.mapNotNull {
            it.toLocationPointOrNull()?.let { point -> LatLng.from(point.latitude, point.longitude) }
        }
        if (points.isEmpty()) {
            return
        }

        // Destination search should center on the result markers themselves.
        // Do not keep bottom-sheet padding here, because it shifts the camera
        // as if another point below the result cluster were included.
        map.setPadding(0, 0, 0, 0)

        if (points.size == 1) {
            val point = points.first()
            Log.d(TAG, "fitDestinationSearchResults requested. single point")
            map.moveCamera(
                CameraUpdateFactory.newCenterPosition(point)
            )
            map.moveCamera(CameraUpdateFactory.zoomTo(15))
            return
        }

        Log.d(TAG, "fitDestinationSearchResults requested. resultOnlyPoints=${points.size}")
        map.moveCamera(
            CameraUpdateFactory.fitMapPoints(
                points.toTypedArray(),
                120,
                15
            )
        )
    }

    private fun fitCurrentLocationAndStations() {
        val map = kakaoMap ?: return
        if (renderedStations.isEmpty()) {
            return
        }

        val points = mutableListOf<LatLng>()
        lastCurrentLocationPoint?.let {
            points.add(LatLng.from(it.latitude, it.longitude))
        }
        renderedStations.forEach { station ->
            val point = station.locationPoint
            points.add(LatLng.from(point.latitude, point.longitude))
        }

        if (points.isEmpty()) {
            return
        }

        Log.d(TAG, "fitCurrentLocationAndStations requested. points=${points.size}")
        map.moveCamera(
            CameraUpdateFactory.fitMapPoints(
                points.toTypedArray(),
                120,
                15
            )
        )
    }

    private fun orderStationsByScreenPosition(stations: List<GasStation>): List<GasStation> {
        val map = kakaoMap ?: return stations
        return stations.sortedWith(
            compareBy<GasStation> {
                val screenPoint = map.toScreenPoint(LatLng.from(it.locationPoint.latitude, it.locationPoint.longitude))
                screenPoint?.y ?: Int.MIN_VALUE
            }.thenBy {
                val screenPoint = map.toScreenPoint(LatLng.from(it.locationPoint.latitude, it.locationPoint.longitude))
                screenPoint?.x ?: Int.MAX_VALUE
            }
        )
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
        stationLabelsById.values.forEach { it.remove() }
        stationLabelsById.clear()
    }

    private fun clearRenderedStationPrices() {
        stationPriceLabelsById.values.forEach { it.remove() }
        stationPriceLabelsById.clear()
    }

    private fun clearRenderedDestinationSearchResults() {
        destinationSearchLabelsByKey.values.forEach { it.remove() }
        destinationSearchLabelsByKey.clear()
        selectedDestinationSearchResultKey = null
        committedDestinationSearchResultKey = null
    }

    private fun removeStaleStationLabels(visibleStationIds: Set<String>) {
        val staleIds = stationLabelsById.keys.filterNot { it in visibleStationIds }
        staleIds.forEach { stationLabelsById.remove(it)?.remove() }
    }

    private fun removeStaleStationPriceLabels(visibleStationIds: Set<String>) {
        val staleIds = stationPriceLabelsById.keys.filterNot { it in visibleStationIds }
        staleIds.forEach { stationPriceLabelsById.remove(it)?.remove() }
    }

    private fun removeStaleDestinationSearchLabels(visibleKeys: Set<String>) {
        val staleKeys = destinationSearchLabelsByKey.keys.filterNot { it in visibleKeys }
        staleKeys.forEach { destinationSearchLabelsByKey.remove(it)?.remove() }
    }

    private fun clearCurrentLocationLabel() {
        currentLocationLabel?.remove()
        currentLocationLabel = null
    }

    private fun clearCurrentLocationRadius() {
        currentLocationRadiusPolyline?.remove()
        currentLocationRadiusPolyline = null
    }

    private fun clearRenderedRoute() {
        routePolyline?.remove()
        routePolyline = null
        routeDistanceLabel?.remove()
        routeDistanceLabel = null
    }

    private fun renderRoute(routeInfo: RouteInfo?) {
        clearRenderedRoute()
        val route = routeInfo ?: return
        if (routeLayer == null) {
            ensureLabelLayers()
        }
        val layer = routeLayer ?: return
        val points = parseRoutePolyline(route.polyline)
        if (points.size < 2) {
            Log.w(TAG, "Skipping route render because polyline has insufficient points.")
            return
        }

        Log.d(TAG, "renderRoute requested: points=${points.size}, distance=${route.distanceMeters}, duration=${route.durationSeconds}")
        val polyline = layer.addPolyline(
            PolylineOptions.from(
                MapPoints.fromLatLng(points),
                9.6f,
                Color.parseColor("#F97316")
            )
        )
        polyline.show()
        routePolyline = polyline
        layer.setVisible(true)
        Log.d(TAG, "renderRoute finished. polyline=${routePolyline != null}")
    }

    private fun renderRouteDistanceLabel(route: RouteInfo, points: List<LatLng>) {
        val map = kakaoMap ?: return
        ensureLabelLayers()
        val layer = routeDistanceLabelLayer ?: return
        val labelManager = map.labelManager ?: return
        if (route.distanceMeters <= 0 || points.isEmpty()) {
            return
        }

        routeDistanceLabel?.remove()
        routeDistanceLabel = null

        val midpoint = points[points.size / 2]
        val screenPoint = map.toScreenPoint(midpoint)
        val containerWidth = mapContainer?.width ?: 0
        val density = activity?.resources?.displayMetrics?.density ?: 1f
        val estimatedLabelWidthPx = 132f * density
        val showOnRight = containerWidth <= 0 ||
            screenPoint == null ||
            screenPoint.x + estimatedLabelWidthPx < containerWidth
        val anchorX = if (showOnRight) 0f else 1f
        val bitmap = buildRouteDistanceBitmap(formatRouteDistance(route.distanceMeters))
        val styles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(bitmap)
                    .setAnchorPoint(anchorX, 0.5f)
            )
        ) ?: return

        routeDistanceLabel = layer.addLabel(
            LabelOptions.from(midpoint)
                .setRank(Long.MAX_VALUE)
                .setStyles(styles)
                .setVisible(true)
        )
        routeDistanceLabel?.show()
        layer.setVisible(true)
    }

    private fun fitRoute(routeInfo: RouteInfo) {
        val map = kakaoMap ?: return
        pendingCameraPaddingBottom = 0
        map.setPadding(0, 0, 0, 0)
        val points = parseRoutePolyline(routeInfo.polyline)
        if (points.size >= 2) {
            Log.d(TAG, "fitRoute requested. points=${points.size}")
            map.moveCamera(
                CameraUpdateFactory.fitMapPoints(
                    points.toTypedArray(),
                    120,
                    15
                )
            )
            return
        }

        Log.w(TAG, "fitRoute skipped because route polyline is insufficient; fitting origin/destination instead.")
        map.moveCamera(
            CameraUpdateFactory.fitMapPoints(
                arrayOf(
                    LatLng.from(routeInfo.origin.latitude, routeInfo.origin.longitude),
                    LatLng.from(routeInfo.destination.latitude, routeInfo.destination.longitude)
                ),
                120,
                15
            )
        )
    }

    private fun parseRoutePolyline(routePolyline: String): List<LatLng> {
        return routePolyline
            .split(';')
            .mapNotNull { token ->
                val parts = token.split(',')
                if (parts.size < 2) {
                    return@mapNotNull null
                }

                val latitude = parts[0].trim().toDoubleOrNull() ?: return@mapNotNull null
                val longitude = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                LatLng.from(latitude, longitude)
            }
    }

    private fun renderCurrentLocationRadius(point: LocationPoint, radiusMeters: Int) {
        clearCurrentLocationRadius()
        ensureLabelLayers()
        val layer = currentLocationRadiusLayer ?: return

        Log.d(TAG, "renderCurrentLocationRadius requested: ${point.latitude}, ${point.longitude}, radius=$radiusMeters")
        val circlePoints = buildCirclePoints(point, radiusMeters)
        val polyline = layer.addPolyline(
            PolylineOptions.from(
                MapPoints.fromLatLng(circlePoints),
                4f,
                Color.parseColor("#663B82F6")
            )
        )
        polyline.show()
        currentLocationRadiusPolyline = polyline
        layer.setVisible(true)
        Log.d(TAG, "renderCurrentLocationRadius finished. polyline=${currentLocationRadiusPolyline != null}")
    }

    private fun buildCirclePoints(point: LocationPoint, radiusMeters: Int): List<LatLng> {
        val earthRadiusMeters = 6_371_000.0
        val centerLatRad = Math.toRadians(point.latitude)
        val centerLngRad = Math.toRadians(point.longitude)
        val angularDistance = radiusMeters / earthRadiusMeters
        val segments = 72
        val points = ArrayList<LatLng>(segments + 1)

        for (index in 0..segments) {
            val bearing = 2.0 * PI * index / segments
            val sinCenterLat = sin(centerLatRad)
            val cosCenterLat = cos(centerLatRad)
            val sinAngular = sin(angularDistance)
            val cosAngular = cos(angularDistance)

            val targetLat = asin(
                sinCenterLat * cosAngular +
                    cosCenterLat * sinAngular * cos(bearing)
            )
            val targetLng = centerLngRad + atan2(
                sin(bearing) * sinAngular * cosCenterLat,
                cosAngular - sinCenterLat * sin(targetLat)
            )

            points.add(
                LatLng.from(
                    Math.toDegrees(targetLat),
                    normalizeLongitude(Math.toDegrees(targetLng))
                )
            )
        }

        return points
    }

    private fun normalizeLongitude(longitude: Double): Double {
        var normalized = longitude
        while (normalized < -180.0) {
            normalized += 360.0
        }
        while (normalized > 180.0) {
            normalized -= 360.0
        }
        return normalized
    }

    private fun resolveStationsForZoom(markerMode: StationMarkerDisplayMode): List<GasStation> {
        return when (markerMode) {
            StationMarkerDisplayMode.ALL -> renderedStations
            StationMarkerDisplayMode.TOP_FIFTY -> renderedStations
                .sortedWith(stationPriceComparator())
                .take(50)
            StationMarkerDisplayMode.TOP_TWENTY -> renderedStations
                .sortedWith(stationPriceComparator())
                .take(20)
            StationMarkerDisplayMode.TOP_FIVE_ONLY -> renderedStations
                .sortedWith(stationPriceComparator())
                .take(5)
        }
    }

    private fun stationPriceComparator(): Comparator<GasStation> {
        return compareBy<GasStation>(
            { it.fuelPrices?.regularGasolineWon?.takeIf { price -> price > 0 } ?: Int.MAX_VALUE },
            { it.pricePerLiter.takeIf { price -> price > 0 } ?: Int.MAX_VALUE },
            { it.distanceMeters }
        )
    }

    private fun getStationLogoStyles(brand: String): LabelStyles? {
        val map = kakaoMap ?: return null
        val labelManager = map.labelManager ?: return null
        val logoResId = BrandLogoResolver.shortLogoResId(brand)
        return stationLabelStylesByResId.getOrPut(logoResId) {
            val bitmap = buildStationMarkerBitmap(logoResId)
            labelManager.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(bitmap)
                        .setAnchorPoint(0.5f, 0.5f)
                )
            )!!
        }
    }

    private fun buildStationMarkerBitmap(logoResId: Int): Bitmap {
        val resources = activity?.resources ?: return createFuelPumpBitmap()
        val source = BitmapFactory.decodeResource(resources, logoResId) ?: return createFuelPumpBitmap()
        return createCenteredLogoBitmap(source, 76)
    }

    private fun buildCurrentLocationMarkerBitmap(): Bitmap {
        return createMarkerBitmap(
            fillColor = Color.parseColor("#2563EB"),
            outerColor = Color.WHITE,
            sizePx = 64,
            innerRatio = 0.42f
        )
    }

    private fun createCenteredLogoBitmap(source: Bitmap, targetSizePx: Int): Bitmap {
        val bitmapSizePx = targetSizePx + 20
        val bitmap = Bitmap.createBitmap(bitmapSizePx, bitmapSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val available = targetSizePx * 0.80f
        val scale = minOf(
            available / source.width.toFloat(),
            available / source.height.toFloat()
        )
        val drawWidth = source.width * scale
        val drawHeight = source.height * scale
        val left = (bitmapSizePx - drawWidth) / 2f
        val top = (bitmapSizePx - drawHeight) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, RectF(left, top, left + drawWidth, top + drawHeight), paint)
        return bitmap
    }

    private fun buildFuelPriceBitmap(lines: List<String>, alpha: Float, scale: Float): Bitmap {
        val clampedAlpha = alpha.coerceIn(0f, 1f)
        val clampedScale = scale.coerceIn(0.75f, 1.35f)
        val textAlpha = (255 * clampedAlpha).toInt().coerceIn(0, 255)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(textAlpha, 0, 0, 0)
            textSize = 25f * clampedScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paddingHorizontal = 18f * clampedScale
        val paddingTop = 13f * clampedScale
        val paddingBottom = 11f * clampedScale
        val lineSpacing = 5f * clampedScale
        val tailHeight = 22f * clampedScale
        val tailBottomGap = 8f * clampedScale
        val tailWidth = 18f * clampedScale
        val bodyShiftX = 12f * clampedScale

        var maxTextWidth = 0f
        lines.forEach { line ->
            maxTextWidth = maxOf(maxTextWidth, textPaint.measureText(line))
        }

        val fontMetrics = textPaint.fontMetrics
        val lineHeight = fontMetrics.descent - fontMetrics.ascent
        val contentHeight = if (lines.isEmpty()) 0f else {
            (lineHeight * lines.size) + (lineSpacing * (lines.size - 1))
        }

        val bodyWidth = ceil(maxTextWidth + (paddingHorizontal * 2f)).toInt().coerceAtLeast((145f * clampedScale).toInt())
        val bodyHeight = ceil(contentHeight + paddingTop + paddingBottom).toInt().coerceAtLeast((56f * clampedScale).toInt())
        val bitmapWidth = (bodyWidth + bodyShiftX.toInt() + (tailWidth * 1.2f).toInt()).coerceAtLeast(bodyWidth)
        val bitmapHeight = bodyHeight + tailHeight.toInt() + tailBottomGap.toInt()
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bodyLeft = bodyShiftX
        val bodyTop = 0f
        val bodyRight = bodyLeft + bodyWidth
        val bodyBottom = bodyTop + bodyHeight

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D1D5DB")
            style = Paint.Style.STROKE
            strokeWidth = 2f * clampedScale
        }

        val bodyRect = RectF(bodyLeft, bodyTop, bodyRight, bodyBottom)
        val cornerRadius = 16f * clampedScale
        canvas.drawRoundRect(bodyRect, cornerRadius, cornerRadius, backgroundPaint)
        canvas.drawRoundRect(bodyRect, cornerRadius, cornerRadius, borderPaint)

        val tailCenterX = bodyLeft + (bodyWidth / 2f)
        val tail = Path().apply {
            moveTo(tailCenterX - tailWidth / 2f, bodyBottom)
            lineTo(tailCenterX + tailWidth / 2f, bodyBottom)
            lineTo(tailCenterX, (bitmapHeight - tailBottomGap.toInt()).toFloat())
            close()
        }
        canvas.drawPath(tail, backgroundPaint)
        canvas.drawPath(tail, borderPaint)

        var baseline = paddingTop - fontMetrics.ascent
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, bodyLeft + paddingHorizontal, baseline, textPaint)
            if (index < lines.lastIndex) {
                baseline += lineHeight + lineSpacing
            }
        }

        return bitmap
    }

    private fun buildFuelPriceLines(
        station: GasStation,
        showDiesel: Boolean
    ): List<String> {
        val summary = station.costSummary
        val selectedFuelLabel = summary?.selectedFuelType?.displayLabel(activity ?: return emptyList())
        val selectedFuelPrice = summary?.selectedFuelPricePerLiter?.takeIf { it > 0 }?.let { formatWon(it) }
        if (selectedFuelLabel != null && selectedFuelPrice != null) {
            return listOf("$selectedFuelLabel $selectedFuelPrice/L")
        }

        val prices = station.fuelPrices ?: return emptyList()
        val regularGasoline = prices.regularGasolineWon?.takeIf { it > 0 }?.let { formatWon(it) }
        val premiumGasoline = prices.premiumGasolineWon?.takeIf { it > 0 }?.let { formatWon(it) }
        val diesel = if (showDiesel) {
            prices.dieselWon?.takeIf { it > 0 }?.let { formatWon(it) }
        } else {
            null
        }

        val fallbackLine = when {
            regularGasoline != null && premiumGasoline != null ->
                "휘발유(고급) $regularGasoline($premiumGasoline)"
            regularGasoline != null ->
                "휘발유 $regularGasoline"
            premiumGasoline != null ->
                "고급휘발유 $premiumGasoline"
            else -> null
        }

        return buildList {
            fallbackLine?.let { add(it) }
            diesel?.let { add("디젤 $it") }
        }
    }

    private fun formatWon(value: Int): String {
        return String.format(Locale.KOREA, "%,d원", value)
    }

    private fun resolvePriceOpacity(zoomLevel: Int, markerMode: StationMarkerDisplayMode): Float {
        return when (markerMode) {
            StationMarkerDisplayMode.TOP_FIVE_ONLY -> when (zoomLevel) {
                11 -> 0.26f
                else -> 0.40f
            }
            StationMarkerDisplayMode.TOP_TWENTY -> when (zoomLevel) {
                12 -> 0.48f
                else -> 0.64f
            }
            StationMarkerDisplayMode.TOP_FIFTY -> when (zoomLevel) {
                13 -> 0.70f
                else -> 0.82f
            }
            StationMarkerDisplayMode.ALL -> when {
                zoomLevel >= 15 -> 1f
                zoomLevel == 14 -> 0.96f
                else -> 0.90f
            }
        }
    }

    private fun resolvePriceScale(zoomLevel: Int, markerMode: StationMarkerDisplayMode): Float {
        return when (markerMode) {
            StationMarkerDisplayMode.TOP_FIVE_ONLY -> when (zoomLevel) {
                11 -> 0.82f
                else -> 0.88f
            }
            StationMarkerDisplayMode.TOP_TWENTY -> when (zoomLevel) {
                12 -> 0.92f
                else -> 0.98f
            }
            StationMarkerDisplayMode.TOP_FIFTY -> when (zoomLevel) {
                13 -> 0.96f
                else -> 1.00f
            }
            StationMarkerDisplayMode.ALL -> when {
                zoomLevel >= 15 -> 1.12f
                zoomLevel == 14 -> 1.04f
                else -> 0.98f
            }
        }
    }

    private fun resolveStationMarkerDisplayMode(zoomLevel: Int): StationMarkerDisplayMode {
        return when {
            zoomLevel <= 11 -> StationMarkerDisplayMode.TOP_FIVE_ONLY
            zoomLevel == 12 -> StationMarkerDisplayMode.TOP_TWENTY
            zoomLevel in 13..14 -> StationMarkerDisplayMode.TOP_FIFTY
            else -> StationMarkerDisplayMode.ALL
        }
    }

    private fun buildDestinationSearchDefaultMarkerBitmap(): Bitmap {
        return createMarkerBitmap(
            fillColor = Color.parseColor("#EF4444"),
            outerColor = Color.WHITE,
            sizePx = 56,
            innerRatio = 0.45f
        )
    }

    private fun buildDestinationSearchSelectedMarkerBitmap(): Bitmap {
        val width = 90
        val height = 108
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF4444")
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val centerX = width / 2f
        val circleCenterY = 36f
        val radius = 29f
        val tailPath = Path().apply {
            moveTo(centerX - 20f, 58f)
            quadTo(centerX, 107f, centerX + 20f, 58f)
            close()
        }
        canvas.drawPath(tailPath, pinPaint)
        canvas.drawCircle(centerX, circleCenterY, radius, pinPaint)
        canvas.drawPath(tailPath, strokePaint)
        canvas.drawCircle(centerX, circleCenterY, radius, strokePaint)
        canvas.drawCircle(centerX, circleCenterY, 10f, innerPaint)
        return bitmap
    }

    private fun buildDestinationSearchCommittedMarkerBitmap(): Bitmap {
        val width = 96
        val height = 104
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 62f
            typeface = Typeface.DEFAULT_BOLD
        }
        val markerText = "🚩"
        val textWidth = textPaint.measureText(markerText)
        val baseline = 70f
        canvas.drawText(markerText, (width - textWidth) / 2f, baseline, textPaint)
        return bitmap
    }

    private fun buildRouteDistanceBitmap(text: String): Bitmap {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F1A17")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paddingHorizontal = 18f
        val paddingVertical = 11f
        val fontMetrics = textPaint.fontMetrics
        val textWidth = textPaint.measureText(text)
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val width = ceil(textWidth + paddingHorizontal * 2f).toInt()
        val height = ceil(textHeight + paddingVertical * 2f).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F97316")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val rect = RectF(1.5f, 1.5f, width - 1.5f, height - 1.5f)
        canvas.drawRoundRect(rect, 18f, 18f, backgroundPaint)
        canvas.drawRoundRect(rect, 18f, 18f, borderPaint)
        val baseline = paddingVertical - fontMetrics.ascent
        canvas.drawText(text, paddingHorizontal, baseline, textPaint)
        return bitmap
    }

    private fun formatRouteDistance(distanceMeters: Int): String {
        val distanceText = if (distanceMeters < 1000) {
            "${distanceMeters}m"
        } else {
            String.format(Locale.KOREA, "%.2fkm", distanceMeters / 1000.0)
        }
        return "총 $distanceText"
    }

    private fun orderDestinationSearchResultsByScreenPosition(
        results: List<DestinationSearchSuggestion>
    ): List<DestinationSearchSuggestion> {
        val map = kakaoMap ?: return results
        return results.sortedWith(
            compareBy<DestinationSearchSuggestion> {
                val point = it.toLocationPointOrNull()
                val screenPoint = point?.let { location ->
                    map.toScreenPoint(LatLng.from(location.latitude, location.longitude))
                }
                screenPoint?.y ?: Int.MIN_VALUE
            }.thenBy {
                val point = it.toLocationPointOrNull()
                val screenPoint = point?.let { location ->
                    map.toScreenPoint(LatLng.from(location.latitude, location.longitude))
                }
                screenPoint?.x ?: Int.MAX_VALUE
            }
        )
    }

    private fun DestinationSearchSuggestion.destinationSearchKey(): String {
        return sourceRef?.takeIf { it.isNotBlank() }
            ?: listOf(
                displayText,
                latitude?.toString().orEmpty(),
                longitude?.toString().orEmpty()
            ).joinToString("|")
    }

    private fun DestinationSearchSuggestion.toLocationPointOrNull(): LocationPoint? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        if (lat == 0.0 && lon == 0.0) {
            return null
        }
        return LocationPoint(
            latitude = lat,
            longitude = lon,
            name = displayText,
            address = description
        )
    }

    private fun createFuelPumpBitmap(): Bitmap {
        val sizePx = 80
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F97316")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F97316")
            style = Paint.Style.FILL
        }
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F97316")
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val center = sizePx / 2f
        canvas.drawCircle(center, center, center - 2f, outerPaint)
        canvas.drawCircle(center, center, center - 4f, ringPaint)

        val bodyLeft = 23f
        val bodyTop = 18f
        val bodyRight = 47f
        val bodyBottom = 55f
        canvas.drawRoundRect(RectF(bodyLeft, bodyTop, bodyRight, bodyBottom), 6f, 6f, bodyPaint)

        canvas.drawRoundRect(RectF(28f, 24f, 42f, 33f), 3f, 3f, whitePaint)
        canvas.drawRect(24f, 39f, 46f, 48f, whitePaint)

        canvas.drawLine(47f, 24f, 55f, 18f, linePaint)
        canvas.drawLine(55f, 18f, 59f, 26f, linePaint)
        canvas.drawLine(59f, 26f, 55f, 33f, linePaint)
        canvas.drawLine(55f, 33f, 55f, 42f, linePaint)

        return bitmap
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
        private const val STATION_PRICE_GASOLINE_ONLY_ZOOM_LEVEL = 11
        private const val STATION_PRICE_FULL_ZOOM_LEVEL = 13
        private const val CURRENT_LOCATION_RADIUS_METERS = 5000
        private const val MIN_FREE_ZOOM_LEVEL = 3
        private const val MAX_FREE_ZOOM_LEVEL = 20
        private val fixedZoomLevels = listOf(11, 12, 13, 15)
    }
}

private enum class StationPriceDisplayMode {
    FULL,
    GASOLINE_ONLY,
    HIDDEN
}

private enum class StationMarkerDisplayMode {
    ALL,
    TOP_FIFTY,
    TOP_TWENTY,
    TOP_FIVE_ONLY
}
