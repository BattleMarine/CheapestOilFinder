package com.example.cheapestoilfinder.map

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.graphics.Path
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
    private var stationLabelLayer: LabelLayer? = null
    private var stationPriceLabelLayer: LabelLayer? = null
    private var currentLocationLabelLayer: LabelLayer? = null
    private var currentLocationLabel: Label? = null
    private var currentLocationLabelStyles: LabelStyles? = null
    private val stationLabelStylesByResId = mutableMapOf<Int, LabelStyles>()
    private val stationLabels = mutableListOf<Label>()
    private val stationPriceLabels = mutableListOf<Label>()
    private val renderedStations = mutableListOf<GasStation>()
    private var lastCurrentLocationPoint: LocationPoint? = null
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
                    stationLabelLayer = null
                    stationPriceLabelLayer = null
                    currentLocationLabelLayer = null
                    stationLabelStylesByResId.clear()
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

    override fun showStations(stations: List<GasStation>) {
        renderedStations.clear()
        renderedStations.addAll(stations)
        Log.d(TAG, "showStations requested. size=${renderedStations.size}")
        renderStationOverlaysForZoom(currentZoomLevel)
        fitCurrentLocationAndStations()
    }

    override fun showRoute(routeInfo: RouteInfo) {
        renderedRoute = routeInfo
        Log.d(TAG, "showRoute requested.")
    }

    override fun setOnStationSelectedListener(listener: ((GasStation) -> Unit)?) {
        stationSelectedListener = listener
    }

    override fun clearMapObjects() {
        clearRenderedStations()
        clearRenderedStationPrices()
        clearCurrentLocationLabel()
        clearCurrentLocationRadius()
        renderedStations.clear()
        renderedRoute = null
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
                val station = tag as? GasStation ?: return false
                Log.d(TAG, "station label clicked: ${station.id}")
                stationSelectedListener?.invoke(station)
                return true
            }
        })
    }

    private fun applyCameraMove(point: LocationPoint, zoomLevel: Int, showCurrentLocationMarker: Boolean) {
        val map = kakaoMap ?: return
        lastCurrentLocationPoint = point

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

    private fun renderStationOverlaysForZoom(zoomLevel: Int) {
        clearRenderedStations()
        clearRenderedStationPrices()
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

        layer.setVisible(true)
        Log.d(TAG, "renderStations requested. count=${stationsToRender.size}, mode=$markerMode")
        for (station in stationsToRender) {
            val point = station.locationPoint
            Log.d(TAG, "renderStation marker: ${station.id} @ ${point.latitude}, ${point.longitude}")
            val stationLogoStyles = getStationLogoStyles(station.brand) ?: continue
            val label = layer.addLabel(
                LabelOptions.from(
                    LatLng.from(point.latitude, point.longitude)
                )
                    .setRank(100L)
                    .setStyles(stationLogoStyles)
                    .setTag(station)
                    .setVisible(true)
            )
            label.setClickable(true)
            label.show()
            stationLabels.add(label)
        }

        renderStationPriceLabelsForZoom(
            zoomLevel = zoomLevel,
            stationsToRender = stationsToRender,
            desiredMode = desiredPriceMode,
            desiredOpacity = desiredOpacity,
            desiredScale = desiredScale
        )
        Log.d(TAG, "renderStations finished. iconLabels=${stationLabels.size}, priceLabels=${stationPriceLabels.size}")
    }

    private fun renderStationPriceLabelsForZoom(
        zoomLevel: Int,
        stationsToRender: List<GasStation>,
        desiredMode: StationPriceDisplayMode,
        desiredOpacity: Float,
        desiredScale: Float
    ) {
        clearRenderedStationPrices()
        ensureLabelLayers()
        val priceLayer = stationPriceLabelLayer ?: return
        val labelManager = kakaoMap?.labelManager ?: return

        if (desiredMode == StationPriceDisplayMode.HIDDEN || desiredOpacity <= 0.01f) {
            priceLayer.setVisible(false)
            Log.d(TAG, "Station price labels hidden at zoom=$zoomLevel")
            return
        }

        priceLayer.setVisible(true)
        val showDiesel = zoomLevel >= 15
        Log.d(TAG, "Rendering station price labels mode=$desiredMode alpha=$desiredOpacity scale=$desiredScale zoom=$zoomLevel count=${renderedStations.size}")

        for (station in stationsToRender) {
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

    private fun clearCurrentLocationRadius() {
        currentLocationRadiusPolyline?.remove()
        currentLocationRadiusPolyline = null
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
        val bitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val available = targetSizePx * 0.80f
        val scale = minOf(
            available / source.width.toFloat(),
            available / source.height.toFloat()
        )
        val drawWidth = source.width * scale
        val drawHeight = source.height * scale
        val left = (targetSizePx - drawWidth) / 2f
        val top = (targetSizePx - drawHeight) / 2f
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
        val bitmapHeight = bodyHeight + tailHeight.toInt()
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
            lineTo(tailCenterX, bitmapHeight.toFloat())
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
