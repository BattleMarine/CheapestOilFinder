package com.example.cheapestoilfinder.entry

import android.app.Activity
import android.Manifest
import android.os.Bundle
import android.util.Log
import android.content.pm.PackageManager
import android.location.Location
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.location.DeviceLocationResolver
import com.example.cheapestoilfinder.map.KakaoMapController
import com.example.cheapestoilfinder.map.MapScreenMode
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.LocationPoint
import com.example.cheapestoilfinder.station.BackendStationRepository
import com.example.cheapestoilfinder.station.BrandLogoResolver
import com.example.cheapestoilfinder.station.StationDisplayMapper
import com.example.cheapestoilfinder.station.api.ApiCallback
import com.example.cheapestoilfinder.station.api.FuelType
import com.example.cheapestoilfinder.station.dto.NearbyStationSearchRequest
import com.example.cheapestoilfinder.station.dto.StationDetailResponse
import com.example.cheapestoilfinder.station.dto.StationSearchResponse
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

class CurrentLocationActivity : Activity() {
    private var mapController: KakaoMapController? = null
    private var stationRepository: BackendStationRepository? = null
    private lateinit var stationListAdapter: StationListAdapter
    private var currentLocationActionsContainer: View? = null
    private var refreshGpsButton: Button? = null
    private var zoomInButton: Button? = null
    private var zoomOutButton: Button? = null
    private var setCurrentLocationButton: Button? = null
    private var stationListContainer: View? = null
    private var stationListSheet: View? = null
    private var stationListHeader: View? = null
    private var stationListDragHandleTouchArea: View? = null
    private var stationListDragHandle: View? = null
    private var stationListScrim: View? = null
    private var stationListRecycler: RecyclerView? = null
    private var stationListEmptyView: View? = null
    private var stationInfoContainer: View? = null
    private var stationInfoSheet: View? = null
    private var stationInfoHeader: View? = null
    private var stationInfoScrim: View? = null
    private var stationInfoTitleView: TextView? = null
    private var stationInfoSubtitleView: TextView? = null
    private var stationInfoBrandLogoView: ImageView? = null
    private var stationInfoNameValueView: TextView? = null
    private var stationInfoAddressValueView: TextView? = null
    private var stationInfoPhoneValueView: TextView? = null
    private var stationInfoFuelContainer: LinearLayout? = null
    private var stationInfoRouteDistanceValueView: TextView? = null
    private var stationInfoRoundTripValueView: TextView? = null
    private var stationInfoRoundTripNoteView: TextView? = null
    private var loadedStations: List<GasStation> = emptyList()
    private var selectedStation: GasStation? = null
    private var pendingGpsActionAfterPermission: (() -> Unit)? = null
    private var currentGpsPoint: LocationPoint? = null
    private var hasRequestedInitialLocation = false
    private var stationSheetCollapsedTranslationY = 0f
    private var stationSheetMinimizedTranslationY = 0f
    private var stationSheetPeekHeightPx = 0
    private var stationSheetDragging = false
    private var stationSheetDragStartY = 0f
    private var stationSheetDragStartTranslationY = 0f
    private var stationSheetState = StationSheetState.HIDDEN
    private var stationInfoSheetState = StationSheetState.HIDDEN
    private var stationInfoSheetHeightPx = 0
    private var stationInfoSheetCollapsedTranslationY = 0f
    private var stationInfoSheetDragging = false
    private var stationInfoSheetDragStartY = 0f
    private var stationInfoSheetDragStartTranslationY = 0f
    private var stationPriceFuelType = FuelType.REGULAR_GASOLINE
    private var lastMinimizedBackPressedAt = 0L
    private val defaultFuelEfficiencyKmPerLiter = 10.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_current_location)

        findViewById<Button>(R.id.button_back).setOnClickListener { finish() }
        setCurrentLocationButton = findViewById(R.id.button_set_current_location)
        setCurrentLocationButton?.setOnClickListener {
            Log.i(TAG, "Current location set button clicked")
            requestStationsAroundCurrentLocation()
        }
        refreshGpsButton = findViewById(R.id.button_refresh_gps)
        refreshGpsButton?.setOnClickListener {
            Log.i(TAG, "GPS refresh button clicked")
            refreshCurrentLocationFromGps()
        }
        zoomInButton = findViewById(R.id.button_zoom_in)
        zoomInButton?.setOnClickListener {
            Log.i(TAG, "Zoom in button clicked")
            mapController?.zoomInFixedStep()
        }
        zoomOutButton = findViewById(R.id.button_zoom_out)
        zoomOutButton?.setOnClickListener {
            Log.i(TAG, "Zoom out button clicked")
            mapController?.zoomOutFixedStep()
        }
        currentLocationActionsContainer = findViewById(R.id.current_location_actions_container)

        configureStationListPanel()
        configureStationInfoPanel()

        mapController = KakaoMapController(R.id.map_container, MapScreenMode.CURRENT_LOCATION).also {
            it.bind(this)
            it.start()
            it.setOnStationSelectedListener { station ->
                openStationInfo(station)
            }
        }

        stationRepository = BackendStationRepository.createDefault()
        requestInitialCurrentLocationIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        mapController?.onResume()
    }

    override fun onPause() {
        mapController?.onPause()
        super.onPause()
    }

    private fun requestInitialCurrentLocationIfNeeded() {
        if (hasRequestedInitialLocation) {
            return
        }

        hasRequestedInitialLocation = true
        resolveCurrentLocation(autoSearchStations = true)
    }

    private fun refreshCurrentLocationFromGps() {
        currentGpsPoint?.let { cachedPoint ->
            Log.i(TAG, "GPS refresh started. Focusing cached current point first.")
            mapController?.focusCurrentLocation(cachedPoint, 15)
        }

        if (hasLocationPermission()) {
            val previousPoint = currentGpsPoint
            resolveCurrentLocation(autoSearchStations = false, forceRefresh = true) { point ->
                if (previousPoint == null || distanceMeters(previousPoint, point) >= 10f) {
                    loadStationsAround(point)
                } else {
                    Log.i(TAG, "GPS update under 10m; skipping station reload")
                }
            }
            return
        }

        pendingGpsActionAfterPermission = {
            currentGpsPoint?.let { cachedPoint ->
                Log.i(TAG, "GPS refresh resumed after permission. Focusing cached current point first.")
                mapController?.focusCurrentLocation(cachedPoint, 15)
            }
            val previousPoint = currentGpsPoint
            resolveCurrentLocation(autoSearchStations = false, forceRefresh = true) { point ->
                if (previousPoint == null || distanceMeters(previousPoint, point) >= 10f) {
                    loadStationsAround(point)
                } else {
                    Log.i(TAG, "GPS update under 10m; skipping station reload")
                }
            }
        }
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
    }

    private fun resolveCurrentLocation(
        autoSearchStations: Boolean,
        forceRefresh: Boolean = false,
        onResolved: ((LocationPoint) -> Unit)? = null
    ) {
        DeviceLocationResolver.resolveCurrentLocation(
            context = this,
            forceRefresh = forceRefresh,
            onSuccess = { point ->
                Log.i(TAG, "GPS location resolved for current location screen: ${point.latitude}, ${point.longitude}")
                currentGpsPoint = point
                val zoomLevel = 15
                mapController?.focusCurrentLocation(point, zoomLevel)
                if (autoSearchStations) {
                    Log.i(TAG, "Auto-searching nearby stations after GPS resolve")
                    loadStationsAround(point)
                }
                onResolved?.invoke(point)
            },
            onError = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Failed to resolve GPS location: $message")
                if (forceRefresh) {
                    currentGpsPoint = null
                }
            }
        )
    }

    private fun loadStationsAround(point: LocationPoint) {
        Log.i(TAG, "Requesting nearby stations around: ${point.latitude}, ${point.longitude}")
        val request = NearbyStationSearchRequest(
            point.latitude,
            point.longitude,
            5000,
            30.0,
            10.0,
            null,
            point.name
        )

        stationRepository?.searchNearbyStations(request, object : ApiCallback<StationSearchResponse> {
            override fun onSuccess(result: StationSearchResponse) {
                val stations: List<GasStation> = StationDisplayMapper.toGasStations(result)
                Log.i(TAG, "Loaded stations around GPS location: ${stations.size}")
                mapController?.showStations(stations)
                showStationList(stations)
            }

            override fun onError(error: Throwable) {
                Log.e(TAG, "Failed to load nearby stations for GPS location", error)
                mapController?.showStations(emptyList())
                if (loadedStations.isNotEmpty()) {
                    collapseStationList(animated = true)
                } else {
                    dismissStationList(animated = true)
                }
            }
        })
    }

    private fun requestStationsAroundCurrentLocation() {
        val point = currentGpsPoint
        if (point != null) {
            Log.i(TAG, "Current GPS point exists. Loading stations around it.")
            mapController?.focusCurrentLocation(point, 15)
            loadStationsAround(point)
            return
        }

        if (hasLocationPermission()) {
            resolveCurrentLocation(autoSearchStations = true, forceRefresh = true)
            return
        }

        pendingGpsActionAfterPermission = {
            resolveCurrentLocation(autoSearchStations = true, forceRefresh = true)
        }
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_LOCATION_PERMISSION) {
            return
        }

        val permissionGranted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            pendingGpsActionAfterPermission?.invoke()
        } else {
            Toast.makeText(this, R.string.location_permission_required_message, Toast.LENGTH_SHORT).show()
        }

        pendingGpsActionAfterPermission = null
    }

    private fun configureStationListPanel() {
        stationListContainer = findViewById(R.id.station_list_scrim_container)
        stationListSheet = findViewById(R.id.station_list_sheet)
        stationListHeader = findViewById(R.id.station_list_header)
        stationListDragHandleTouchArea = findViewById(R.id.station_list_drag_handle_touch_area)
        stationListDragHandle = findViewById(R.id.station_list_drag_handle)
        stationListScrim = findViewById(R.id.station_list_scrim)
        stationListRecycler = findViewById(R.id.station_list_recycler)
        stationListEmptyView = findViewById(R.id.station_list_empty)

        stationListAdapter = StationListAdapter { station ->
            openStationInfo(station)
        }
        stationListRecycler?.apply {
            layoutManager = LinearLayoutManager(this@CurrentLocationActivity)
            adapter = stationListAdapter
            itemAnimator = null
        }
        stationListContainer?.isClickable = false
        stationListSheet?.isClickable = false
        stationListScrim?.isClickable = false
        stationListScrim?.isEnabled = false
        stationListScrim?.visibility = View.GONE

        stationListDragHandleTouchArea?.setOnTouchListener { _, event ->
            handleStationSheetTouch(event)
        }
        stationListDragHandle?.setOnTouchListener { _, event ->
            handleStationSheetTouch(event)
        }
        stationListDragHandleTouchArea?.setOnClickListener {
            when (stationSheetState) {
                StationSheetState.COLLAPSED -> expandStationList()
                StationSheetState.EXPANDED -> collapseStationList(animated = true)
                StationSheetState.MINIMIZED -> presentStationList()
                StationSheetState.HIDDEN -> Unit
            }
        }
        stationListDragHandle?.setOnClickListener {
            when (stationSheetState) {
                StationSheetState.COLLAPSED -> expandStationList()
                StationSheetState.EXPANDED -> collapseStationList(animated = true)
                StationSheetState.MINIMIZED -> presentStationList()
                StationSheetState.HIDDEN -> Unit
            }
        }

        stationListContainer?.visibility = View.INVISIBLE
        stationListContainer?.post {
            val sheet = stationListSheet ?: return@post
            val targetHeight = resources.displayMetrics.heightPixels
            val density = resources.displayMetrics.density
            stationSheetPeekHeightPx = (targetHeight * 0.5f).roundToInt()
            val minimizedHeight = (density * 96f).roundToInt()
            sheet.layoutParams = sheet.layoutParams.apply { height = targetHeight }
            sheet.requestLayout()
            stationSheetCollapsedTranslationY = (targetHeight - stationSheetPeekHeightPx)
                .coerceAtLeast(0)
                .toFloat()
            stationSheetMinimizedTranslationY = (targetHeight - minimizedHeight)
                .coerceAtLeast(0)
                .toFloat()
            sheet.translationY = targetHeight.toFloat()
            updateCurrentLocationActionsVisibility()
            updateStationActionsOffset(0f)
        }
    }

    private fun configureStationInfoPanel() {
        stationInfoContainer = findViewById(R.id.station_info_scrim_container)
        stationInfoSheet = findViewById(R.id.station_info_sheet)
        stationInfoHeader = findViewById(R.id.station_info_header)
        stationInfoScrim = findViewById(R.id.station_info_scrim)
        stationInfoTitleView = findViewById(R.id.station_info_title)
        stationInfoSubtitleView = findViewById(R.id.station_info_subtitle)
        stationInfoBrandLogoView = findViewById(R.id.station_info_brand_logo)
        stationInfoNameValueView = findViewById(R.id.station_info_name_value)
        stationInfoAddressValueView = findViewById(R.id.station_info_address_value)
        stationInfoPhoneValueView = findViewById(R.id.station_info_phone_value)
        stationInfoFuelContainer = findViewById(R.id.station_info_fuel_container)
        stationInfoRouteDistanceValueView = findViewById(R.id.station_info_route_distance_value)
        stationInfoRoundTripValueView = findViewById(R.id.station_info_round_trip_value)
        stationInfoRoundTripNoteView = findViewById(R.id.station_info_round_trip_note)

        stationInfoContainer?.visibility = View.INVISIBLE
        stationInfoContainer?.post {
            val sheet = stationInfoSheet ?: return@post
            val targetHeight = resources.displayMetrics.heightPixels
            stationInfoSheetHeightPx = targetHeight
            stationInfoSheetCollapsedTranslationY = targetHeight / 2f
            sheet.layoutParams = sheet.layoutParams.apply { height = targetHeight }
            sheet.requestLayout()
            sheet.translationY = targetHeight.toFloat()
        }

        stationInfoHeader?.setOnTouchListener { _, event ->
            handleStationInfoSheetTouch(event)
        }
        stationInfoSheet?.setOnTouchListener { _, event ->
            handleStationInfoSheetTouch(event)
        }
        stationInfoScrim?.setOnClickListener {
            hideStationInfo(animated = true)
        }
    }

    private fun showStationInfo(station: GasStation) {
        selectedStation = station
        renderStationInfo(station)

        if (stationInfoSheetHeightPx <= 0) {
            stationInfoContainer?.post {
                showStationInfo(station)
            }
            return
        }

        stationInfoContainer?.visibility = View.VISIBLE
        stationInfoContainer?.bringToFront()
        stationInfoScrim?.visibility = View.VISIBLE
        val sheet = stationInfoSheet ?: return
        sheet.animate().cancel()
        sheet.translationY = stationInfoSheetHeightPx.toFloat()
        sheet.animate()
            .translationY(stationInfoSheetCollapsedTranslationY)
            .setDuration(220L)
            .start()
        stationInfoSheetState = StationSheetState.COLLAPSED
        updateCurrentLocationActionsVisibility()
    }

    private fun renderStationInfo(station: GasStation) {
        stationInfoTitleView?.text = station.name.ifBlank { getString(R.string.station_info_subtitle_placeholder) }
        stationInfoSubtitleView?.text = buildStationInfoSubtitle(station)
        stationInfoBrandLogoView?.setImageResource(BrandLogoResolver.fullLogoResId(station.brand))
        stationInfoNameValueView?.text = station.name.ifBlank { getString(R.string.station_info_subtitle_placeholder) }
        stationInfoAddressValueView?.text = station.locationPoint.address.ifBlank {
            getString(R.string.station_info_body_placeholder)
        }
        stationInfoPhoneValueView?.text = station.phone.ifBlank {
            getString(R.string.station_info_phone_placeholder)
        }
        stationInfoRouteDistanceValueView?.text = formatRouteDistance(station.distanceMeters)
        stationInfoRoundTripValueView?.text = buildRoundTripCostText(station)
        stationInfoRoundTripNoteView?.text = getString(R.string.station_info_round_trip_note)
        renderFuelRows(station)
    }

    private fun buildStationInfoSubtitle(station: GasStation): String {
        val brandText = station.brand.ifBlank { getString(R.string.station_info_subtitle_placeholder) }
        val distanceText = formatRouteDistance(station.distanceMeters)
        return if (distanceText.isBlank()) {
            brandText
        } else {
            "$brandText · $distanceText"
        }
    }

    private fun formatRouteDistance(distanceMeters: Int): String {
        if (distanceMeters <= 0) {
            return getString(R.string.station_info_distance_unavailable)
        }

        return if (distanceMeters < 1000) {
            getString(R.string.distance_format_meter, distanceMeters)
        } else {
            val kilometers = distanceMeters / 1000.0
            getString(R.string.distance_format_kilometer, kilometers)
        }
    }

    private fun formatWon(amount: Int): String {
        return String.format(Locale.KOREA, "%,d원", amount)
    }

    private fun resolveDisplayFuelPrices(station: GasStation): List<Pair<String, Int>> {
        val prices = station.fuelPrices ?: return emptyList()
        return buildList {
            prices.regularGasolineWon?.takeIf { it > 0 }?.let {
                add(getString(R.string.fuel_regular_gasoline) to it)
            }
            prices.premiumGasolineWon?.takeIf { it > 0 }?.let {
                add(getString(R.string.fuel_premium_gasoline) to it)
            }
            prices.dieselWon?.takeIf { it > 0 }?.let {
                add(getString(R.string.fuel_diesel) to it)
            }
        }
    }

    private fun renderFuelRows(station: GasStation) {
        val container = stationInfoFuelContainer ?: return
        container.removeAllViews()

        val rows = resolveDisplayFuelPrices(station)
        if (rows.isEmpty()) {
            val placeholder = TextView(this).apply {
                text = getString(R.string.station_info_fuel_unavailable)
                setTextColor(0xFF1F1A17.toInt())
                textSize = 14f
            }
            container.addView(placeholder)
            return
        }

        rows.forEach { (label, price) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val labelView = TextView(this).apply {
                text = label
                setTextColor(0xFF8A7C71.toInt())
                textSize = 13f
            }

            val priceView = TextView(this).apply {
                text = formatWon(price)
                setTextColor(0xFF1F1A17.toInt())
                textSize = 15f
                setPadding(16, 0, 0, 0)
            }

            row.addView(labelView)
            row.addView(priceView)
            container.addView(row)
        }
    }

    private fun resolveRoundTripFuelPrice(station: GasStation): Int? {
        val prices = station.fuelPrices ?: return null
        return prices.regularGasolineWon?.takeIf { it > 0 }
            ?: prices.premiumGasolineWon?.takeIf { it > 0 }
            ?: prices.dieselWon?.takeIf { it > 0 }
    }

    private fun calculateRoundTripCost(distanceMeters: Int, pricePerLiter: Int, fuelEfficiencyKmPerLiter: Double): Int? {
        if (distanceMeters <= 0 || pricePerLiter <= 0 || fuelEfficiencyKmPerLiter <= 0.0) {
            return null
        }

        val roundTripKilometers = (distanceMeters / 1000.0) * 2.0
        val litersNeeded = roundTripKilometers / fuelEfficiencyKmPerLiter
        return (litersNeeded * pricePerLiter).roundToInt()
    }

    private fun buildRoundTripCostText(station: GasStation): String {
        val price = resolveRoundTripFuelPrice(station)
        if (price == null) {
            return getString(R.string.station_info_round_trip_unavailable)
        }

        val cost = calculateRoundTripCost(station.distanceMeters, price, defaultFuelEfficiencyKmPerLiter)
        return if (cost == null) {
            getString(R.string.station_info_round_trip_unavailable)
        } else {
            formatWon(cost)
        }
    }

    private fun expandStationInfo() {
        val sheet = stationInfoSheet ?: return
        if (stationInfoSheetState == StationSheetState.HIDDEN) {
            return
        }

        sheet.animate().cancel()
        sheet.animate()
            .translationY(0f)
            .setDuration(220L)
            .start()
        stationInfoSheetState = StationSheetState.EXPANDED
    }

    private fun hideStationInfo(animated: Boolean) {
        val sheet = stationInfoSheet ?: return

        val finish = Runnable {
            stationInfoContainer?.visibility = View.INVISIBLE
            stationInfoScrim?.visibility = View.GONE
            stationInfoSheetState = StationSheetState.HIDDEN
            stationInfoSheetDragging = false
            updateCurrentLocationActionsVisibility()
        }

        if (!animated) {
            sheet.translationY = stationInfoSheetHeightPx.toFloat()
            finish.run()
            return
        }

        sheet.animate().cancel()
        sheet.animate()
            .translationY(stationInfoSheetHeightPx.toFloat())
            .setDuration(220L)
            .withEndAction(finish)
            .start()
        stationInfoScrim?.animate()?.alpha(0f)?.setDuration(220L)?.start()
    }

    private fun collapseStationInfo(animated: Boolean) {
        val sheet = stationInfoSheet ?: return
        if (stationInfoSheetState == StationSheetState.HIDDEN && stationInfoContainer?.visibility != View.VISIBLE) {
            return
        }

        val finish = Runnable {
            stationInfoScrim?.visibility = View.VISIBLE
            stationInfoSheetState = StationSheetState.COLLAPSED
            stationInfoSheetDragging = false
        }

        if (!animated) {
            stationInfoContainer?.visibility = View.VISIBLE
            sheet.translationY = stationInfoSheetCollapsedTranslationY
            stationInfoScrim?.alpha = 1f
            finish.run()
            return
        }

        stationInfoContainer?.visibility = View.VISIBLE
        sheet.animate().cancel()
        sheet.animate()
            .translationY(stationInfoSheetCollapsedTranslationY)
            .setDuration(220L)
            .withEndAction(finish)
            .start()
    }

    private fun handleStationInfoSheetTouch(event: MotionEvent): Boolean {
        val sheet = stationInfoSheet ?: return false
        if (stationInfoSheetHeightPx <= 0 || stationInfoSheetState == StationSheetState.HIDDEN) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stationInfoSheetDragging = true
                stationInfoSheetDragStartY = event.rawY
                stationInfoSheetDragStartTranslationY = sheet.translationY
                sheet.animate().cancel()
                stationInfoScrim?.animate()?.cancel()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!stationInfoSheetDragging) {
                    return false
                }
                val delta = event.rawY - stationInfoSheetDragStartY
                val nextTranslation = (stationInfoSheetDragStartTranslationY + delta)
                    .coerceIn(0f, stationInfoSheetHeightPx.toFloat())
                sheet.translationY = nextTranslation
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!stationInfoSheetDragging) {
                    return false
                }
                stationInfoSheetDragging = false
                val dragDelta = sheet.translationY - stationInfoSheetDragStartTranslationY
                val shouldExpand = dragDelta < -12f
                val shouldClose = dragDelta > (stationInfoSheetHeightPx * 0.28f)
                if (shouldExpand) {
                    expandStationInfo()
                } else if (shouldClose) {
                    hideStationInfo(animated = true)
                } else {
                    collapseStationInfo(animated = true)
                }
                return true
            }
        }

        return false
    }

    private fun showStationList(stations: List<GasStation>) {
        if (stationSheetCollapsedTranslationY <= 0f) {
            stationListContainer?.post {
                showStationList(stations)
            }
            return
        }

        val sortedStations = sortStationsForDisplay(stations)
        loadedStations = sortedStations
        stationListAdapter.submitList(sortedStations)
        stationListEmptyView?.visibility = if (sortedStations.isEmpty()) View.VISIBLE else View.GONE
        stationListRecycler?.visibility = if (sortedStations.isEmpty()) View.GONE else View.VISIBLE
        showStationListMinimized()
    }

    private fun openStationInfo(station: GasStation) {
        Log.i(TAG, "Opening station info for: ${station.id}")
        mapController?.focusStation(station.locationPoint, 15)
        showStationInfo(station)
        requestStationDetail(station)
    }

    private fun requestStationDetail(station: GasStation) {
        val stationId = station.id
        if (stationId.isBlank()) {
            return
        }

        stationRepository?.getStationDetail(stationId, object : ApiCallback<StationDetailResponse> {
            override fun onSuccess(result: StationDetailResponse) {
                val detailStation = result.station?.let(StationDisplayMapper::toGasStation) ?: return
                val mergedStation = mergeStationInfo(station, detailStation)
                if (selectedStation?.id != stationId) {
                    return
                }
                selectedStation = mergedStation
                renderStationInfo(mergedStation)
            }

            override fun onError(error: Throwable) {
                Log.w(TAG, "Failed to load station detail for $stationId", error)
            }
        })
    }

    private fun mergeStationInfo(base: GasStation, detail: GasStation): GasStation {
        return base.copy(
            name = detail.name.ifBlank { base.name },
            brand = detail.brand.ifBlank { base.brand },
            fuelType = if (detail.fuelType.isBlank()) base.fuelType else detail.fuelType,
            pricePerLiter = if (detail.pricePerLiter > 0) detail.pricePerLiter else base.pricePerLiter,
            distanceMeters = if (detail.distanceMeters > 0) detail.distanceMeters else base.distanceMeters,
            locationPoint = detail.locationPoint.takeIf {
                it.latitude != 0.0 || it.longitude != 0.0 || it.address.isNotBlank() || it.name.isNotBlank()
            } ?: base.locationPoint,
            fuelPrices = detail.fuelPrices ?: base.fuelPrices,
            phone = detail.phone.ifBlank { base.phone }
        )
    }

    private fun expandStationList() {
        val sheet = stationListSheet ?: return
        stationListContainer?.visibility = View.VISIBLE
        stationListContainer?.bringToFront()
        stationSheetState = StationSheetState.EXPANDED
        updateCurrentLocationActionsVisibility()
        showStationListScrim(alpha = 0f)
        sheet.animate().cancel()
        sheet.animate()
            .translationY(0f)
            .setDuration(220L)
            .start()
        stationListScrim?.animate()?.alpha(1f)?.setDuration(220L)?.start()
        updateStationActionsOffset(0f)
    }

    private fun presentStationList() {
        val sheet = stationListSheet ?: return
        val previousState = stationSheetState
        stationListContainer?.visibility = View.VISIBLE
        stationListContainer?.bringToFront()
        hideStationListScrim()
        stationSheetState = StationSheetState.COLLAPSED
        updateCurrentLocationActionsVisibility()
        sheet.animate().cancel()
        if (previousState == StationSheetState.HIDDEN || sheet.translationY <= 0f) {
            sheet.translationY = stationListSheet?.height?.toFloat() ?: 0f
        }
        sheet.animate()
            .translationY(stationSheetCollapsedTranslationY)
            .setDuration(220L)
            .start()
        updateStationActionsOffset(stationSheetPeekHeightPx.toFloat())
    }

    private fun showStationListMinimized() {
        val container = stationListContainer ?: return
        val sheet = stationListSheet ?: return

        container.visibility = View.VISIBLE
        container.bringToFront()
        sheet.animate().cancel()
        sheet.translationY = stationSheetMinimizedTranslationY
        hideStationListScrim()
        stationSheetDragging = false
        stationSheetState = StationSheetState.MINIMIZED
        updateCurrentLocationActionsVisibility()
        updateStationActionsOffset(0f)
    }

    private fun minimizeStationList(animated: Boolean) {
        val container = stationListContainer ?: return
        val sheet = stationListSheet ?: return
        if (stationSheetState == StationSheetState.HIDDEN && container.visibility != View.VISIBLE) {
            return
        }

        val finish = Runnable {
            hideStationListScrim()
            stationSheetDragging = false
            stationSheetState = StationSheetState.MINIMIZED
            updateCurrentLocationActionsVisibility()
            updateStationActionsOffset(0f)
        }

        if (!animated) {
            container.visibility = View.VISIBLE
            sheet.translationY = stationSheetMinimizedTranslationY
            hideStationListScrim()
            finish.run()
            return
        }

        container.visibility = View.VISIBLE
        sheet.animate().cancel()
        sheet.animate()
            .translationY(stationSheetMinimizedTranslationY)
            .setDuration(220L)
            .withEndAction(finish)
            .start()
        stationListScrim?.animate()?.alpha(0f)?.setDuration(220L)?.start()
    }

    private fun collapseStationList(animated: Boolean) {
        val container = stationListContainer ?: return
        val sheet = stationListSheet ?: return
        if (stationSheetState == StationSheetState.HIDDEN && container.visibility != View.VISIBLE) {
            return
        }

        val finish = Runnable {
            hideStationListScrim()
            stationSheetDragging = false
            stationSheetState = StationSheetState.COLLAPSED
            updateCurrentLocationActionsVisibility()
            updateStationActionsOffset(stationSheetPeekHeightPx.toFloat())
        }

        if (!animated) {
            container.visibility = View.VISIBLE
            sheet.translationY = stationSheetCollapsedTranslationY
            hideStationListScrim()
            finish.run()
            return
        }

        container.visibility = View.VISIBLE
        sheet.animate().cancel()
        sheet.animate()
            .translationY(stationSheetCollapsedTranslationY)
            .setDuration(220L)
            .withEndAction(finish)
            .start()
        stationListScrim?.animate()?.alpha(0f)?.setDuration(220L)?.start()
    }

    private fun dismissStationList(animated: Boolean) {
        val container = stationListContainer ?: return
        val sheet = stationListSheet ?: return

        val finish = Runnable {
            container.visibility = View.INVISIBLE
            hideStationListScrim()
            stationSheetDragging = false
            stationSheetState = StationSheetState.HIDDEN
            updateCurrentLocationActionsVisibility()
            updateStationActionsOffset(0f)
        }

        if (!animated) {
            sheet.translationY = stationListSheet?.height?.toFloat() ?: 0f
            hideStationListScrim()
            finish.run()
            return
        }

        sheet.animate().cancel()
        sheet.animate()
            .translationY(stationListSheet?.height?.toFloat() ?: 0f)
            .setDuration(220L)
            .withEndAction(finish)
            .start()
        stationListScrim?.animate()?.alpha(0f)?.setDuration(220L)?.start()
    }

    private fun settleExpandedStationList() {
        val sheet = stationListSheet ?: return
        stationListContainer?.visibility = View.VISIBLE
        stationListContainer?.bringToFront()
        stationSheetState = StationSheetState.EXPANDED
        updateCurrentLocationActionsVisibility()
        showStationListScrim(alpha = stationListScrim?.alpha ?: 1f)
        sheet.animate().cancel()
        sheet.animate()
            .translationY(0f)
            .setDuration(160L)
            .start()
        stationListScrim?.animate()?.alpha(1f)?.setDuration(160L)?.start()
        updateStationActionsOffset(0f)
    }

    private fun showStationListScrim(alpha: Float = 1f) {
        stationListScrim?.animate()?.cancel()
        stationListScrim?.isClickable = false
        stationListScrim?.isEnabled = false
        stationListScrim?.alpha = alpha
        stationListScrim?.visibility = View.VISIBLE
    }

    private fun hideStationListScrim() {
        stationListScrim?.animate()?.cancel()
        stationListScrim?.isClickable = false
        stationListScrim?.isEnabled = false
        stationListScrim?.alpha = 0f
        stationListScrim?.visibility = View.GONE
    }

    private fun sortStationsForDisplay(stations: List<GasStation>): List<GasStation> {
        return stations.sortedWith(
            compareBy<GasStation> {
                priceForFuelType(it, stationPriceFuelType) ?: Int.MAX_VALUE
            }.thenBy { it.distanceMeters }
                .thenBy { it.name }
        )
    }

    private fun priceForFuelType(station: GasStation, fuelType: FuelType): Int? {
        val prices = station.fuelPrices ?: return null
        return when (fuelType) {
            FuelType.REGULAR_GASOLINE -> prices.regularGasolineWon
            FuelType.PREMIUM_GASOLINE -> prices.premiumGasolineWon
            FuelType.DIESEL -> prices.dieselWon
            FuelType.LPG -> prices.lpgWon
        }?.takeIf { it > 0 }
    }

    private fun handleStationSheetTouch(event: MotionEvent): Boolean {
        val sheet = stationListSheet ?: return false
        if (stationSheetCollapsedTranslationY <= 0f || stationSheetState == StationSheetState.HIDDEN) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stationSheetDragging = true
                stationSheetDragStartY = event.rawY
                stationSheetDragStartTranslationY = sheet.translationY
                sheet.animate().cancel()
                stationListScrim?.animate()?.cancel()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!stationSheetDragging) {
                    return false
                }
                val delta = event.rawY - stationSheetDragStartY
                if (stationSheetState == StationSheetState.EXPANDED && delta < 0f) {
                    return true
                }
                val (minTranslation, maxTranslation) = when (stationSheetState) {
                    StationSheetState.EXPANDED -> 0f to stationSheetCollapsedTranslationY
                    StationSheetState.COLLAPSED -> 0f to stationSheetMinimizedTranslationY
                    StationSheetState.MINIMIZED -> stationSheetCollapsedTranslationY to stationSheetMinimizedTranslationY
                    StationSheetState.HIDDEN -> 0f to 0f
                }
                val nextTranslation = (stationSheetDragStartTranslationY + delta)
                    .coerceIn(minTranslation, maxTranslation)
                sheet.translationY = nextTranslation
                val progress = when (stationSheetState) {
                    StationSheetState.EXPANDED -> 1f - (nextTranslation / stationSheetCollapsedTranslationY)
                    StationSheetState.COLLAPSED, StationSheetState.MINIMIZED -> {
                        1f - ((nextTranslation - stationSheetCollapsedTranslationY) /
                            (stationSheetMinimizedTranslationY - stationSheetCollapsedTranslationY).coerceAtLeast(1f))
                    }
                    StationSheetState.HIDDEN -> 0f
                }
                stationListScrim?.alpha = progress.coerceIn(0f, 1f)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!stationSheetDragging) {
                    return false
                }
                stationSheetDragging = false
                val dragDelta = sheet.translationY - stationSheetDragStartTranslationY
                if (abs(dragDelta) <= STATION_SHEET_TAP_THRESHOLD_PX) {
                    when (stationSheetState) {
                        StationSheetState.MINIMIZED -> presentStationList()
                        StationSheetState.EXPANDED -> settleExpandedStationList()
                        StationSheetState.COLLAPSED -> collapseStationList(animated = true)
                        StationSheetState.HIDDEN -> return false
                    }
                    return true
                }
                when (stationSheetState) {
                    StationSheetState.COLLAPSED -> {
                        when {
                            dragDelta < -20f -> expandStationList()
                            dragDelta > 20f -> minimizeStationList(animated = true)
                            else -> collapseStationList(animated = true)
                        }
                    }
                    StationSheetState.EXPANDED -> {
                        if (dragDelta > 20f) {
                            collapseStationList(animated = true)
                        } else {
                            settleExpandedStationList()
                        }
                    }
                    StationSheetState.MINIMIZED -> {
                        when {
                            dragDelta < -20f -> presentStationList()
                            else -> minimizeStationList(animated = true)
                        }
                    }
                    StationSheetState.HIDDEN -> return false
                }
                return true
            }
        }

        return false
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (stationInfoSheetState != StationSheetState.HIDDEN) {
            hideStationInfo(animated = true)
            return
        }
        when (stationSheetState) {
            StationSheetState.EXPANDED -> {
                lastMinimizedBackPressedAt = 0L
                collapseStationList(animated = true)
                return
            }
            StationSheetState.COLLAPSED -> {
                lastMinimizedBackPressedAt = 0L
                minimizeStationList(animated = true)
                return
            }
            StationSheetState.MINIMIZED -> {
                val now = System.currentTimeMillis()
                if (now - lastMinimizedBackPressedAt <= MINIMIZED_BACK_EXIT_WINDOW_MS) {
                    super.onBackPressed()
                    return
                }
                lastMinimizedBackPressedAt = now
                Toast.makeText(this, R.string.back_to_main_confirm_message, Toast.LENGTH_SHORT).show()
                return
            }
            StationSheetState.HIDDEN -> {
                lastMinimizedBackPressedAt = 0L
            }
        }
        super.onBackPressed()
    }

    private fun updateStationActionsOffset(offsetPx: Float) {
        currentLocationActionsContainer?.translationY = -offsetPx
    }

    private fun updateCurrentLocationActionsVisibility() {
        val shouldShowSetButton =
            stationSheetState == StationSheetState.HIDDEN && stationInfoSheetState == StationSheetState.HIDDEN
        setCurrentLocationButton?.visibility = if (shouldShowSetButton) View.VISIBLE else View.GONE
        refreshGpsButton?.visibility = View.VISIBLE
    }

    private fun distanceMeters(from: LocationPoint, to: LocationPoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            from.latitude,
            from.longitude,
            to.latitude,
            to.longitude,
            results
        )
        return results[0]
    }

    companion object {
        private const val TAG = "CurrentLocationActivity"
        private const val REQUEST_LOCATION_PERMISSION = 2001
        private const val STATION_SHEET_TAP_THRESHOLD_PX = 8f
        private const val MINIMIZED_BACK_EXIT_WINDOW_MS = 2000L
    }
}

private enum class StationSheetState {
    HIDDEN,
    COLLAPSED,
    EXPANDED,
    MINIMIZED
}
