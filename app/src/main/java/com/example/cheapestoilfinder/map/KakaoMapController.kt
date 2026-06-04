package com.example.cheapestoilfinder.map

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.LocationPoint
import com.example.cheapestoilfinder.map.model.RouteInfo
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
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import java.util.Locale
import kotlin.math.ceil

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
    private var stationPriceLabelLayer: LabelLayer? = null
    private var currentLocationLabelLayer: LabelLayer? = null
    private var currentLocationLabel: Label? = null
    private var stationLabelStyles: LabelStyles? = null
    private var currentLocationLabelStyles: LabelStyles? = null
    private val stationLabels = mutableListOf<Label>()
    private val stationPriceLabels = mutableListOf<Label>()
    private val renderedStations = mutableListOf<GasStation>()
    private var lastCurrentLocationPoint: LocationPoint? = null
    private var renderedRoute: RouteInfo? = null
    private var pendingCameraPoint: LocationPoint? = null
    private var pendingCameraZoomLevel: Int = 15
    private var pendingCameraShowsCurrentLocation = false
    private var hasPendingCameraMove = false
    private var currentZoomLevel: Int = 15
    private var currentPriceDisplayMode: StationPriceDisplayMode = StationPriceDisplayMode.FULL
    private var currentPriceOpacity: Float = 1f
    private var currentPriceScale: Float = 1f

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
                    stationPriceLabelLayer = null
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
                    registerCameraListeners(readyMap)
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
        fitCurrentLocationAndStations()
    }

    override fun showRoute(routeInfo: RouteInfo) {
        renderedRoute = routeInfo
        Log.d(TAG, "showRoute requested.")
    }

    override fun clearMapObjects() {
        clearRenderedStations()
        clearRenderedStationPrices()
        clearCurrentLocationLabel()
        renderedStations.clear()
        renderedRoute = null
        Log.d(TAG, "clearMapObjects requested.")
    }

    private fun ensureLabelLayers() {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return

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
                    .setZOrder(5000)
            )
        }

        if (stationPriceLabelLayer == null) {
            stationPriceLabelLayer = labelManager.addLayer(
                LabelLayerOptions.from("station-price-layer")
                    .setVisible(true)
                    .setCompetitionType(CompetitionType.None)
                    .setZOrder(5001)
            )
        }

        if (stationLabelStyles == null) {
            stationLabelStyles = labelManager.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(buildStationMarkerBitmap())
                        .setAnchorPoint(0.5f, 0.5f)
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

    private fun registerCameraListeners(map: KakaoMap) {
        map.setOnCameraMoveEndListener(object : KakaoMap.OnCameraMoveEndListener {
            override fun onCameraMoveEnd(
                kakaoMap: KakaoMap,
                cameraPosition: CameraPosition,
                gestureType: GestureType
            ) {
                currentZoomLevel = cameraPosition.zoomLevel
                val desiredMode = resolvePriceDisplayMode(currentZoomLevel)
                val desiredOpacity = resolvePriceOpacity(currentZoomLevel)
                val desiredScale = resolvePriceScale(currentZoomLevel)
                Log.d(
                    TAG,
                    "Camera move ended. zoom=${cameraPosition.zoomLevel}, mode=$desiredMode, alpha=$desiredOpacity, scale=$desiredScale, gesture=$gestureType"
                )
                if ((desiredMode != currentPriceDisplayMode ||
                        desiredOpacity != currentPriceOpacity ||
                        desiredScale != currentPriceScale) &&
                    renderedStations.isNotEmpty()
                ) {
                    renderStationPriceLabelsForZoom(currentZoomLevel)
                }
            }
        })
    }

    private fun applyCameraMove(point: LocationPoint, zoomLevel: Int, showCurrentLocationMarker: Boolean) {
        val map = kakaoMap ?: return
        lastCurrentLocationPoint = point

        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(point.latitude, point.longitude)
            )
        )
        map.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel))
        currentZoomLevel = zoomLevel
        Log.d(TAG, "moveCamera requested: ${point.latitude}, ${point.longitude}")

        if (showCurrentLocationMarker || screenMode == MapScreenMode.CURRENT_LOCATION) {
            renderCurrentLocationMarker(point)
        }
    }

    private fun renderStations() {
        clearRenderedStations()
        clearRenderedStationPrices()
        ensureLabelLayers()
        val layer = stationLabelLayer ?: return

        layer.setVisible(true)
        Log.d(TAG, "renderStations requested. count=${renderedStations.size}")
        for (station in renderedStations) {
            val point = station.locationPoint
            Log.d(TAG, "renderStation marker: ${station.id} @ ${point.latitude}, ${point.longitude}")
            val label = layer.addLabel(
                LabelOptions.from(
                    LatLng.from(point.latitude, point.longitude)
                )
                    .setRank(100L)
                    .setStyles(stationLabelStyles ?: return)
                    .setVisible(true)
            )
            label.show()
            stationLabels.add(label)
        }

        renderStationPriceLabelsForZoom(currentZoomLevel)
        Log.d(TAG, "renderStations finished. iconLabels=${stationLabels.size}, priceLabels=${stationPriceLabels.size}")
    }

    private fun renderStationPriceLabelsForZoom(zoomLevel: Int) {
        clearRenderedStationPrices()
        ensureLabelLayers()
        val priceLayer = stationPriceLabelLayer ?: return
        val labelManager = kakaoMap?.labelManager ?: return

        val desiredMode = resolvePriceDisplayMode(zoomLevel)
        val desiredOpacity = resolvePriceOpacity(zoomLevel)
        val desiredScale = resolvePriceScale(zoomLevel)
        currentPriceDisplayMode = desiredMode
        currentPriceOpacity = desiredOpacity
        currentPriceScale = desiredScale
        if (desiredMode == StationPriceDisplayMode.HIDDEN || desiredOpacity <= 0.01f) {
            priceLayer.setVisible(false)
            Log.d(TAG, "Station price labels hidden at zoom=$zoomLevel")
            return
        }

        priceLayer.setVisible(true)
        val showDiesel = zoomLevel >= STATION_PRICE_FULL_ZOOM_LEVEL
        Log.d(TAG, "Rendering station price labels mode=$desiredMode alpha=$desiredOpacity scale=$desiredScale zoom=$zoomLevel count=${renderedStations.size}")

        for (station in renderedStations) {
            val point = station.locationPoint
            val priceLines = buildFuelPriceLines(station, showDiesel = showDiesel)
            if (priceLines.isEmpty()) {
                continue
            }

            val priceLabelStyles = labelManager.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(buildFuelPriceBitmap(priceLines, desiredOpacity, desiredScale))
                        .setAnchorPoint(0.5f, 1.0f)
                )
            )
            val priceLabel = priceLayer.addLabel(
                LabelOptions.from(
                    LatLng.from(point.latitude, point.longitude)
                )
                    .setRank(95L)
                    .setStyles(priceLabelStyles)
                    .setVisible(true)
            )
            priceLabel.show()
            stationPriceLabels.add(priceLabel)
        }

        Log.d(TAG, "renderStationPriceLabelsForZoom finished. priceLabels=${stationPriceLabels.size}")
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

    private fun clearRenderedStationPrices() {
        stationPriceLabels.forEach { it.remove() }
        stationPriceLabels.clear()
    }

    private fun clearCurrentLocationLabel() {
        currentLocationLabel?.remove()
        currentLocationLabel = null
    }

    private fun buildStationMarkerBitmap(): Bitmap {
        return createFuelPumpBitmap()
    }

    private fun buildCurrentLocationMarkerBitmap(): Bitmap {
        return createMarkerBitmap(
            fillColor = Color.parseColor("#2563EB"),
            outerColor = Color.WHITE,
            sizePx = 64,
            innerRatio = 0.42f
        )
    }

    private fun buildFuelPriceBitmap(lines: List<String>, alpha: Float, scale: Float): Bitmap {
        val clampedAlpha = alpha.coerceIn(0f, 1f)
        val clampedScale = scale.coerceIn(0.75f, 1.35f)
        val textAlpha = (255 * clampedAlpha).toInt().coerceIn(0, 255)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(textAlpha, 0, 0, 0)
            textSize = 28f * clampedScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paddingHorizontal = 24f * clampedScale
        val paddingTop = 18f * clampedScale
        val paddingBottom = 16f * clampedScale
        val lineSpacing = 6f * clampedScale
        val tailPadding = 18f * clampedScale

        var maxTextWidth = 0f
        lines.forEach { line ->
            maxTextWidth = maxOf(maxTextWidth, textPaint.measureText(line))
        }

        val fontMetrics = textPaint.fontMetrics
        val lineHeight = fontMetrics.descent - fontMetrics.ascent
        val contentHeight = if (lines.isEmpty()) 0f else {
            (lineHeight * lines.size) + (lineSpacing * (lines.size - 1))
        }

        val boxWidth = ceil(maxTextWidth + (paddingHorizontal * 2f)).toInt().coerceAtLeast((160f * clampedScale).toInt())
        val boxHeight = ceil(contentHeight + paddingTop + paddingBottom).toInt().coerceAtLeast((64f * clampedScale).toInt())
        val bitmap = Bitmap.createBitmap(boxWidth, boxHeight + tailPadding.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D1D5DB")
            style = Paint.Style.STROKE
            strokeWidth = 2f * clampedScale
        }

        val rect = RectF(0f, 0f, boxWidth.toFloat(), boxHeight.toFloat())
        val cornerRadius = 20f * clampedScale
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        var baseline = paddingTop - fontMetrics.ascent
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, paddingHorizontal, baseline, textPaint)
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
        val prices = station.fuelPrices ?: return emptyList()
        val lines = mutableListOf<String>()

        val regularGasoline = prices.regularGasolineWon?.takeIf { it > 0 }?.let { formatWon(it) }
        val premiumGasoline = prices.premiumGasolineWon?.takeIf { it > 0 }?.let { formatWon(it) }
        val diesel = if (showDiesel) {
            prices.dieselWon?.takeIf { it > 0 }?.let { formatWon(it) }
        } else {
            null
        }

        val gasolineLine = when {
            regularGasoline != null && premiumGasoline != null ->
                "휘발유(고급) $regularGasoline($premiumGasoline)"
            regularGasoline != null ->
                "휘발유 $regularGasoline"
            premiumGasoline != null ->
                "고급휘발유 $premiumGasoline"
            else -> null
        }

        gasolineLine?.let { lines.add(it) }
        diesel?.let { lines.add("디젤 $it") }

        return lines
    }

    private fun formatWon(value: Int): String {
        return String.format(Locale.KOREA, "%,d원", value)
    }

    private fun resolvePriceDisplayMode(zoomLevel: Int): StationPriceDisplayMode {
        return when {
            zoomLevel >= STATION_PRICE_FULL_ZOOM_LEVEL -> StationPriceDisplayMode.FULL
            zoomLevel >= STATION_PRICE_GASOLINE_ONLY_ZOOM_LEVEL -> StationPriceDisplayMode.GASOLINE_ONLY
            else -> StationPriceDisplayMode.HIDDEN
        }
    }

    private fun resolvePriceOpacity(zoomLevel: Int): Float {
        return when {
            zoomLevel >= 13 -> 1f
            zoomLevel == 12 -> 0.72f
            zoomLevel == 11 -> 0.38f
            else -> 0f
        }
    }

    private fun resolvePriceScale(zoomLevel: Int): Float {
        return when {
            zoomLevel >= 17 -> 1.32f
            zoomLevel == 16 -> 1.18f
            zoomLevel in 13..15 -> 1f
            zoomLevel == 12 -> 0.90f
            zoomLevel == 11 -> 0.82f
            else -> 0f
        }
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
    }
}

private enum class StationPriceDisplayMode {
    FULL,
    GASOLINE_ONLY,
    HIDDEN
}
