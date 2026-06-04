package com.example.cheapestoilfinder.entry

import android.app.Activity
import android.Manifest
import android.os.Bundle
import android.util.Log
import android.content.pm.PackageManager
import android.view.MotionEvent
import android.view.View
import android.widget.Button
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
import com.example.cheapestoilfinder.station.StationDisplayMapper
import com.example.cheapestoilfinder.station.api.ApiCallback
import com.example.cheapestoilfinder.station.api.FuelType
import com.example.cheapestoilfinder.station.dto.NearbyStationSearchRequest
import com.example.cheapestoilfinder.station.dto.StationSearchResponse
import kotlin.math.abs
import kotlin.math.roundToInt

class CurrentLocationActivity : Activity() {
    private var mapController: KakaoMapController? = null
    private var stationRepository: BackendStationRepository? = null
    private lateinit var stationListAdapter: StationListAdapter
    private var currentLocationActionsContainer: View? = null
    private var stationListContainer: View? = null
    private var stationListSheet: View? = null
    private var stationListHeader: View? = null
    private var stationListScrim: View? = null
    private var stationListRecycler: RecyclerView? = null
    private var stationListEmptyView: View? = null
    private var loadedStations: List<GasStation> = emptyList()
    private var pendingGpsActionAfterPermission: (() -> Unit)? = null
    private var currentGpsPoint: LocationPoint? = null
    private var hasRequestedInitialLocation = false
    private var stationSheetCollapsedTranslationY = 0f
    private var stationSheetPeekHeightPx = 0
    private var stationSheetDragging = false
    private var stationSheetDragStartY = 0f
    private var stationSheetDragStartTranslationY = 0f
    private var stationSheetState = StationSheetState.HIDDEN
    private var stationPriceFuelType = FuelType.REGULAR_GASOLINE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_current_location)

        findViewById<Button>(R.id.button_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.button_set_current_location).setOnClickListener {
            Log.i(TAG, "Current location set button clicked")
            requestStationsAroundCurrentLocation()
        }
        findViewById<Button>(R.id.button_refresh_gps).setOnClickListener {
            Log.i(TAG, "GPS refresh button clicked")
            refreshCurrentLocationFromGps()
        }
        currentLocationActionsContainer = findViewById(R.id.current_location_actions_container)

        configureStationListPanel()

        mapController = KakaoMapController(R.id.map_container, MapScreenMode.CURRENT_LOCATION).also {
            it.bind(this)
            it.start()
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
        resolveCurrentLocation(autoSearchStations = false)
    }

    private fun refreshCurrentLocationFromGps() {
        if (hasLocationPermission()) {
            resolveCurrentLocation(autoSearchStations = false, forceRefresh = true)
            return
        }

        pendingGpsActionAfterPermission = {
            resolveCurrentLocation(autoSearchStations = false, forceRefresh = true)
        }
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
    }

    private fun resolveCurrentLocation(
        autoSearchStations: Boolean,
        forceRefresh: Boolean = false
    ) {
        DeviceLocationResolver.resolveCurrentLocation(
            context = this,
            onSuccess = { point ->
                Log.i(TAG, "GPS location resolved for current location screen: ${point.latitude}, ${point.longitude}")
                currentGpsPoint = point
                val zoomLevel = 15
                mapController?.focusCurrentLocation(point, zoomLevel)
                if (autoSearchStations) {
                    Log.i(TAG, "Auto-searching nearby stations after GPS resolve")
                    loadStationsAround(point)
                }
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
        stationListScrim = findViewById(R.id.station_list_scrim)
        stationListRecycler = findViewById(R.id.station_list_recycler)
        stationListEmptyView = findViewById(R.id.station_list_empty)

        stationListAdapter = StationListAdapter()
        stationListRecycler?.apply {
            layoutManager = LinearLayoutManager(this@CurrentLocationActivity)
            adapter = stationListAdapter
            itemAnimator = null
        }

        stationListHeader?.setOnTouchListener { _, event ->
            handleStationSheetTouch(event)
        }
        stationListHeader?.setOnClickListener {
            if (stationSheetState != StationSheetState.HIDDEN) {
                expandStationList()
            }
        }
        stationListSheet?.setOnClickListener {
            if (stationSheetState == StationSheetState.COLLAPSED) {
                expandStationList()
            }
        }
        stationListScrim?.setOnClickListener {
            if (loadedStations.isNotEmpty()) {
                collapseStationList(animated = true)
            } else {
                dismissStationList(animated = true)
            }
        }

        stationListContainer?.visibility = View.INVISIBLE
        stationListContainer?.post {
            val sheet = stationListSheet ?: return@post
            val targetHeight = (resources.displayMetrics.heightPixels * 0.5f).roundToInt()
            val density = resources.displayMetrics.density
            stationSheetPeekHeightPx = (density * 108f).roundToInt()
            sheet.layoutParams = sheet.layoutParams.apply { height = targetHeight }
            sheet.requestLayout()
            stationSheetCollapsedTranslationY = (targetHeight - stationSheetPeekHeightPx)
                .coerceAtLeast(0)
                .toFloat()
            sheet.translationY = targetHeight.toFloat()
            currentLocationActionsContainer?.visibility = View.VISIBLE
            updateStationActionsOffset(0f)
        }
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
        expandStationList()
    }

    private fun expandStationList() {
        val sheet = stationListSheet ?: return
        stationListContainer?.visibility = View.VISIBLE
        stationListContainer?.bringToFront()
        currentLocationActionsContainer?.visibility = View.GONE
        stationListScrim?.alpha = 0f
        stationListScrim?.visibility = View.VISIBLE
        sheet.animate().cancel()
        sheet.translationY = stationSheetCollapsedTranslationY
        sheet.animate()
            .translationY(0f)
            .setDuration(220L)
            .start()
        stationListScrim?.animate()?.alpha(1f)?.setDuration(220L)?.start()
        updateStationActionsOffset(0f)
        stationSheetState = StationSheetState.EXPANDED
    }

    private fun collapseStationList(animated: Boolean) {
        val container = stationListContainer ?: return
        val sheet = stationListSheet ?: return
        if (stationSheetState == StationSheetState.HIDDEN && container.visibility != View.VISIBLE) {
            return
        }

        val finish = Runnable {
            stationListScrim?.visibility = View.GONE
            stationSheetDragging = false
            stationSheetState = StationSheetState.COLLAPSED
            currentLocationActionsContainer?.visibility = View.VISIBLE
            updateStationActionsOffset(stationSheetPeekHeightPx.toFloat())
        }

        if (!animated) {
            container.visibility = View.VISIBLE
            sheet.translationY = stationSheetCollapsedTranslationY
            stationListScrim?.alpha = 0f
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
            stationListScrim?.visibility = View.GONE
            stationSheetDragging = false
            stationSheetState = StationSheetState.HIDDEN
            currentLocationActionsContainer?.visibility = View.VISIBLE
            updateStationActionsOffset(0f)
        }

        if (!animated) {
            sheet.translationY = stationListSheet?.height?.toFloat() ?: 0f
            stationListScrim?.alpha = 0f
            finish.run()
            return
        }

        sheet.animate().cancel()
        sheet.animate()
            .translationY(sheet.height.toFloat())
            .setDuration(220L)
            .withEndAction(finish)
            .start()
        stationListScrim?.animate()?.alpha(0f)?.setDuration(220L)?.start()
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
                val nextTranslation = (stationSheetDragStartTranslationY + delta)
                    .coerceIn(0f, stationSheetCollapsedTranslationY)
                sheet.translationY = nextTranslation
                val progress = 1f - (nextTranslation / stationSheetCollapsedTranslationY)
                stationListScrim?.alpha = progress.coerceIn(0f, 1f)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!stationSheetDragging) {
                    return false
                }
                stationSheetDragging = false
                val dragDistance = abs(sheet.translationY - stationSheetDragStartTranslationY)
                if (stationSheetState == StationSheetState.COLLAPSED && dragDistance < 12f) {
                    expandStationList()
                    return true
                }
                val shouldClose = sheet.translationY > (stationSheetCollapsedTranslationY / 2f)
                if (shouldClose) {
                    collapseStationList(animated = true)
                } else {
                    expandStationList()
                }
                return true
            }
        }

        return false
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (stationSheetState == StationSheetState.EXPANDED) {
            collapseStationList(animated = true)
            return
        }
        super.onBackPressed()
    }

    private fun updateStationActionsOffset(offsetPx: Float) {
        currentLocationActionsContainer?.translationY = -offsetPx
    }

    companion object {
        private const val TAG = "CurrentLocationActivity"
        private const val REQUEST_LOCATION_PERMISSION = 2001
    }
}

private enum class StationSheetState {
    HIDDEN,
    COLLAPSED,
    EXPANDED
}
