package com.example.cheapestoilfinder.entry

import android.app.Activity
import android.Manifest
import android.os.Bundle
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.destination.DestinationAutocompleteRepository
import com.example.cheapestoilfinder.destination.BackendDestinationAutocompleteRepository
import com.example.cheapestoilfinder.destination.BackendDestinationSearchRepository
import com.example.cheapestoilfinder.destination.DestinationSearchSuggestion
import com.example.cheapestoilfinder.destination.DestinationSearchRepository
import com.example.cheapestoilfinder.location.DeviceLocationResolver
import com.example.cheapestoilfinder.map.KakaoMapController
import com.example.cheapestoilfinder.map.MapScreenMode
import com.example.cheapestoilfinder.map.RouteCameraPlacement
import com.example.cheapestoilfinder.map.StationMarkerHighlight
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.LocationPoint
import com.example.cheapestoilfinder.map.model.RouteInfo
import com.example.cheapestoilfinder.map.model.StationCostSummary
import com.example.cheapestoilfinder.station.api.ApiCallback
import com.example.cheapestoilfinder.station.api.StationSearchSortOrder
import com.example.cheapestoilfinder.settings.CostCalculator
import com.example.cheapestoilfinder.settings.UserFuelType
import com.example.cheapestoilfinder.settings.UserPreferenceManager
import com.example.cheapestoilfinder.station.BackendStationRepository
import com.example.cheapestoilfinder.station.StationDisplayMapper
import com.example.cheapestoilfinder.station.StationRepository
import com.example.cheapestoilfinder.station.dto.RouteResultMode
import com.example.cheapestoilfinder.station.dto.RouteStationSearchRequest
import com.example.cheapestoilfinder.station.dto.StationSearchResponse
import kotlin.math.abs
import kotlin.math.roundToInt

open class DestinationActivity : Activity() {
    private val autocompleteHandler = Handler(Looper.getMainLooper())
    protected var mapController: KakaoMapController? = null
    private var pendingGpsActionAfterPermission: (() -> Unit)? = null
    private var hasRequestedInitialLocation = false
    private var destinationSearchQuery: String = ""
    private lateinit var destinationSearchOverlay: View
    private lateinit var destinationSearchInput: EditText
    private lateinit var destinationAutocompleteRecycler: RecyclerView
    private lateinit var destinationAutocompleteEmptyView: TextView
    private lateinit var destinationAutocompleteAdapter: DestinationAutocompleteAdapter
    private lateinit var destinationSearchResultsContainer: View
    private lateinit var destinationSearchResultsSheet: View
    private lateinit var destinationSearchResultsRecycler: RecyclerView
    private lateinit var destinationSearchResultsEmptyView: TextView
    private lateinit var destinationSearchResultsCountView: TextView
    private lateinit var setDestinationButton: Button
    private lateinit var destinationRouteLoadingOverlay: View
    private lateinit var destinationZoomControlsContainer: View
    private lateinit var destinationGpsButton: Button
    private lateinit var settingsOverlay: View
    private lateinit var settingsOverlayFuelTypeSpinner: Spinner
    private lateinit var settingsOverlayFuelEfficiencyEditText: EditText
    private lateinit var settingsOverlayRefuelAmountEditText: EditText
    private lateinit var destinationSearchResultsAdapter: DestinationAutocompleteAdapter
    private lateinit var destinationSearchResultsHandleTouchArea: View
    private lateinit var destinationSearchResultsHandle: View
    private lateinit var routeRecommendationContainer: View
    private lateinit var routeRecommendationSheet: View
    private lateinit var routeRecommendationRecycler: RecyclerView
    private lateinit var routeRecommendationEmptyView: TextView
    private lateinit var routeRecommendationCountView: TextView
    private lateinit var routeRecommendationAdapter: StationListAdapter
    private lateinit var routeRecommendationHandleTouchArea: View
    private lateinit var routeRecommendationHandle: View
    private val destinationAutocompleteRepository: DestinationAutocompleteRepository by lazy {
        BackendDestinationAutocompleteRepository.createDefault(this)
    }
    private val destinationSearchRepository: DestinationSearchRepository by lazy {
        BackendDestinationSearchRepository.createDefault(this)
    }
    protected val stationRepository: StationRepository by lazy {
        BackendStationRepository.createDefault(this)
    }
    protected val userPreferenceManager by lazy { UserPreferenceManager.create(this) }
    private var pendingAutocompleteRunnable: Runnable? = null
    private var autocompleteRequestVersion: Int = 0
    private var destinationSearchRequestVersion: Int = 0
    private var destinationSearchResultsSheetHeightPx: Int = 0
    private var destinationSearchResultsCollapsedTranslationY: Float = 0f
    private var destinationSearchResultsMinimizedTranslationY: Float = 0f
    private var destinationSearchResultsPeekHeightPx: Int = 0
    private var destinationSearchResultsDragging: Boolean = false
    private var destinationSearchResultsDragStartY: Float = 0f
    private var destinationSearchResultsDragStartTranslationY: Float = 0f
    private var destinationSearchResultsSheetState = DestinationResultsSheetState.HIDDEN
    private var cachedDestinationSearchResults: List<DestinationSearchSuggestion> = emptyList()
    protected var currentGpsPoint: LocationPoint? = null
    private var selectedDestinationSuggestion: DestinationSearchSuggestion? = null
    private var selectedDestinationPoint: LocationPoint? = null
    private var isDestinationConfirmed: Boolean = false
    private var destinationResultsStateBeforeConfirm = DestinationResultsSheetState.HIDDEN
    private var confirmedBaseRouteInfo: RouteInfo? = null
    private var routeRecommendationSheetCollapsedTranslationY: Float = 0f
    private var routeRecommendationSheetMinimizedTranslationY: Float = 0f
    private var routeRecommendationSheetDragging: Boolean = false
    private var routeRecommendationSheetDragStartY: Float = 0f
    private var routeRecommendationSheetDragStartTranslationY: Float = 0f
    private var routeRecommendationSheetState = DestinationResultsSheetState.HIDDEN
    private var lastExitBackPressedAt = 0L

    protected open val contentLayoutResId: Int = R.layout.activity_destination
    protected open val mapContainerViewId: Int = R.id.destination_map_container
    protected open val mapScreenMode: MapScreenMode = MapScreenMode.DESTINATION_ROUTE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentLayoutResId)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        findViewById<Button>(R.id.button_back).setOnClickListener { onBackPressed() }
        findViewById<Button>(R.id.button_open_settings_overlay).setOnClickListener {
            openSettingsOverlay()
        }
        destinationZoomControlsContainer = findViewById(R.id.destination_zoom_controls_container)
        destinationGpsButton = findViewById(R.id.button_destination_gps)
        destinationGpsButton.setOnClickListener {
            refreshCurrentLocationFromGps()
        }
        findViewById<Button>(R.id.button_zoom_in).setOnClickListener {
            mapController?.zoomInOneLevel()
        }
        findViewById<Button>(R.id.button_zoom_out).setOnClickListener {
            mapController?.zoomOutOneLevel()
        }
        findViewById<View>(R.id.destination_search_bar).setOnClickListener {
            openDestinationSearchOverlay()
        }

        destinationSearchOverlay = findViewById(R.id.destination_search_overlay)
        initializeSettingsOverlay()
        destinationSearchInput = findViewById(R.id.edittext_destination_search)
        destinationAutocompleteRecycler = findViewById(R.id.recycler_destination_autocomplete)
        destinationAutocompleteEmptyView = findViewById(R.id.text_destination_search_empty)
        destinationAutocompleteAdapter = DestinationAutocompleteAdapter { suggestion ->
            destinationSearchInput.setText(suggestion.displayText)
            destinationSearchInput.setSelection(suggestion.displayText.length)
            destinationSearchQuery = suggestion.displayText
        }
        destinationSearchResultsContainer = findViewById(R.id.destination_search_results_container)
        destinationSearchResultsSheet = findViewById(R.id.destination_search_results_sheet)
        destinationSearchResultsRecycler = findViewById(R.id.recycler_destination_search_results)
        destinationSearchResultsEmptyView = findViewById(R.id.destination_search_results_empty)
        destinationSearchResultsCountView = findViewById(R.id.destination_search_results_count)
        setDestinationButton = findViewById(R.id.button_set_destination)
        destinationRouteLoadingOverlay = findViewById(R.id.destination_route_loading_overlay)
        setDestinationButton.visibility = View.GONE
        setDestinationButton.setOnClickListener {
            setSelectedDestinationAndDrawRoute()
        }
        destinationSearchResultsHandleTouchArea = findViewById(R.id.destination_search_results_drag_handle_touch_area)
        destinationSearchResultsHandle = findViewById(R.id.destination_search_results_drag_handle)
        routeRecommendationContainer = findViewById(R.id.route_recommendation_container)
        routeRecommendationSheet = findViewById(R.id.route_recommendation_sheet)
        routeRecommendationRecycler = findViewById(R.id.recycler_route_recommendations)
        routeRecommendationEmptyView = findViewById(R.id.route_recommendation_empty)
        routeRecommendationCountView = findViewById(R.id.route_recommendation_count)
        routeRecommendationHandleTouchArea = findViewById(R.id.route_recommendation_drag_handle_touch_area)
        routeRecommendationHandle = findViewById(R.id.route_recommendation_drag_handle)
        routeRecommendationAdapter = StationListAdapter { station ->
            if (routeRecommendationSheetState == DestinationResultsSheetState.EXPANDED) {
                collapseRouteRecommendationPanel(animated = true)
            }
            showDetourRouteForStation(station)
        }
        routeRecommendationRecycler.layoutManager = LinearLayoutManager(this)
        routeRecommendationRecycler.adapter = routeRecommendationAdapter
        routeRecommendationContainer.visibility = View.GONE
        configureRouteRecommendationPanel()
        destinationSearchResultsAdapter = DestinationAutocompleteAdapter { suggestion ->
            selectDestinationSearchResult(suggestion, moveCamera = true)
            if (destinationSearchResultsSheetState == DestinationResultsSheetState.EXPANDED) {
                collapseDestinationSearchResultsPanel(animated = true)
            }
        }
        destinationSearchResultsRecycler.layoutManager = LinearLayoutManager(this)
        destinationSearchResultsRecycler.adapter = destinationSearchResultsAdapter
        configureDestinationSearchResultsPanel()
        destinationAutocompleteRecycler.layoutManager = LinearLayoutManager(this)
        destinationAutocompleteRecycler.adapter = destinationAutocompleteAdapter
        findViewById<View>(R.id.button_destination_search_submit).setOnClickListener {
            Log.d(TAG, "Destination search button clicked. currentQuery=$destinationSearchQuery")
            submitDestinationSearch()
        }
        findViewById<View>(R.id.button_search_overlay_close).setOnClickListener {
            closeDestinationSearchOverlay()
        }
        destinationSearchInput.setOnEditorActionListener { _, actionId, event ->
            val isKeyRelease = event?.action == KeyEvent.ACTION_UP
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH && !isKeyRelease
            val isEnterKey = event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER
            if (isSearchAction || isEnterKey) {
                submitDestinationSearch()
                true
            } else {
                false
            }
        }
        destinationSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                destinationSearchQuery = s?.toString().orEmpty()
                scheduleAutocompleteSearch(destinationSearchQuery)
            }
        })

        mapController = KakaoMapController(
            mapContainerViewId,
            mapScreenMode
        ).also {
            it.bind(this)
            it.setOnDestinationSearchResultSelectedListener { suggestion ->
                selectDestinationSearchResult(suggestion, moveCamera = false)
            }
            it.setOnStationSelectedListener { station ->
                showDetourRouteForStation(station)
            }
            it.start()
        }

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

    override fun onDestroy() {
        autocompleteHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (settingsOverlay.visibility == View.VISIBLE) {
            resetExitBackConfirmation()
            closeSettingsOverlay()
            return
        }
        if (destinationSearchOverlay.visibility == View.VISIBLE) {
            resetExitBackConfirmation()
            closeDestinationSearchOverlay()
            return
        }
        if (isDestinationConfirmed) {
            if (handleRouteRecommendationBack()) {
                resetExitBackConfirmation()
                return
            }
            resetExitBackConfirmation()
            restoreDestinationBeforeConfirmState()
            return
        }
        if (destinationSearchResultsContainer.visibility == View.VISIBLE) {
            resetExitBackConfirmation()
            handleDestinationResultsBack()
            return
        }
        confirmExitBackPressed()
    }

    protected fun resetExitBackConfirmation() {
        lastExitBackPressedAt = 0L
    }

    @Suppress("DEPRECATION")
    protected fun confirmExitBackPressed() {
        val now = System.currentTimeMillis()
        if (now - lastExitBackPressedAt <= EXIT_BACK_PRESS_WINDOW_MS) {
            lastExitBackPressedAt = 0L
            super.onBackPressed()
            return
        }

        lastExitBackPressedAt = now
        Toast.makeText(this, R.string.back_to_main_confirm_message, Toast.LENGTH_SHORT).show()
    }

    private fun initializeSettingsOverlay() {
        settingsOverlay = findViewById(R.id.settings_overlay)
        settingsOverlay.visibility = View.GONE
        settingsOverlayFuelTypeSpinner = findViewById(R.id.spinner_settings_overlay_fuel_type)
        settingsOverlayFuelEfficiencyEditText = findViewById(R.id.edit_settings_overlay_fuel_efficiency)
        settingsOverlayRefuelAmountEditText = findViewById(R.id.edit_settings_overlay_refuel_amount)

        val labels = UserFuelType.spinnerLabels(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        settingsOverlayFuelTypeSpinner.adapter = adapter

        findViewById<Button>(R.id.button_settings_overlay_close).setOnClickListener {
            closeSettingsOverlay()
        }
        findViewById<Button>(R.id.button_settings_overlay_save).setOnClickListener {
            saveSettingsFromOverlay()
        }
    }

    private fun openSettingsOverlay() {
        populateSettingsOverlay()
        settingsOverlay.visibility = View.VISIBLE
        settingsOverlay.bringToFront()
        settingsOverlay.elevation = SETTINGS_OVERLAY_ELEVATION
    }

    private fun closeSettingsOverlay() {
        settingsOverlay.visibility = View.GONE
        clearSettingsOverlayErrors()
    }

    private fun populateSettingsOverlay() {
        val settings = userPreferenceManager.loadSettings()
        settingsOverlayFuelTypeSpinner.setSelection(UserFuelType.spinnerIndexFor(settings.fuelType))
        settingsOverlayFuelEfficiencyEditText.setText(formatSettingsDouble(settings.fuelEfficiencyKmPerLiter))
        settingsOverlayRefuelAmountEditText.setText(formatSettingsDouble(settings.refuelAmountLiter))
        clearSettingsOverlayErrors()
    }

    private fun saveSettingsFromOverlay() {
        clearSettingsOverlayErrors()

        val selectedFuelType = UserFuelType.fromSpinnerIndex(settingsOverlayFuelTypeSpinner.selectedItemPosition)
        val fuelEfficiency = settingsOverlayFuelEfficiencyEditText.text
            ?.toString()
            ?.trim()
            ?.toDoubleOrNull()
        val refuelAmount = settingsOverlayRefuelAmountEditText.text
            ?.toString()
            ?.trim()
            ?.toDoubleOrNull()

        var isValid = true
        if (settingsOverlayFuelTypeSpinner.selectedItemPosition !in 0 until UserFuelType.spinnerLabels(this).size) {
            Toast.makeText(this, R.string.settings_validation_fuel_type_required, Toast.LENGTH_SHORT).show()
            isValid = false
        }
        if (fuelEfficiency == null || fuelEfficiency <= 0.0) {
            settingsOverlayFuelEfficiencyEditText.error = getString(R.string.settings_validation_efficiency_positive)
            isValid = false
        }
        if (refuelAmount == null || refuelAmount <= 0.0) {
            settingsOverlayRefuelAmountEditText.error = getString(R.string.settings_validation_refuel_positive)
            isValid = false
        }
        if (!isValid) {
            return
        }

        userPreferenceManager.saveSettings(
            fuelType = selectedFuelType,
            fuelEfficiencyKmPerLiter = fuelEfficiency ?: UserPreferenceManager.DEFAULT_FUEL_EFFICIENCY_KM_PER_LITER,
            refuelAmountLiter = refuelAmount ?: UserPreferenceManager.DEFAULT_REFUEL_AMOUNT_LITER
        )
        Toast.makeText(this, R.string.settings_saved_message, Toast.LENGTH_SHORT).show()
        closeSettingsOverlay()
    }

    private fun clearSettingsOverlayErrors() {
        settingsOverlayFuelEfficiencyEditText.error = null
        settingsOverlayRefuelAmountEditText.error = null
    }

    private fun formatSettingsDouble(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }

    private fun requestInitialCurrentLocationIfNeeded() {
        if (hasRequestedInitialLocation) {
            return
        }

        hasRequestedInitialLocation = true
        refreshCurrentLocationFromGps()
    }

    protected fun refreshCurrentLocationFromGps() {
        if (hasLocationPermission()) {
            resolveAndShowCurrentLocation()
            return
        }

        pendingGpsActionAfterPermission = {
            resolveAndShowCurrentLocation()
        }
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
    }

    private fun resolveAndShowCurrentLocation() {
        DeviceLocationResolver.resolveCurrentLocation(
            context = this,
            onSuccess = { point ->
                updateDestinationMapToCurrentLocation(point)
            },
            onError = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Failed to resolve GPS location: $message")
            }
        )
    }

    protected open fun updateDestinationMapToCurrentLocation(point: LocationPoint) {
        currentGpsPoint = point
        mapController?.focusCurrentLocation(point, 15)
    }

    protected open fun openDestinationSearchOverlay() {
        destinationSearchOverlay.visibility = View.VISIBLE
        destinationSearchOverlay.bringToFront()
        destinationSearchOverlay.elevation = SEARCH_OVERLAY_ELEVATION
        destinationSearchInput.post {
            destinationSearchInput.requestFocus()
            destinationSearchInput.setSelection(destinationSearchInput.text?.length ?: 0)
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(destinationSearchInput, InputMethodManager.SHOW_IMPLICIT)
        }
        scheduleAutocompleteSearch(destinationSearchInput.text?.toString().orEmpty())
        Log.d(TAG, "Destination search overlay opened. query=$destinationSearchQuery")
    }

    protected open fun closeDestinationSearchOverlay(restoreMapAfterClose: Boolean = true) {
        destinationSearchOverlay.visibility = View.GONE
        destinationAutocompleteAdapter.submitList(emptyList())
        destinationAutocompleteEmptyView.visibility = View.GONE
        destinationAutocompleteRecycler.visibility = View.GONE
        pendingAutocompleteRunnable?.let { autocompleteHandler.removeCallbacks(it) }
        pendingAutocompleteRunnable = null
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(destinationSearchInput.windowToken, 0)
        destinationSearchInput.clearFocus()
        if (restoreMapAfterClose) {
            onDestinationSearchOverlayClosed()
        }
    }

    protected open fun onDestinationSearchOverlayClosed() = Unit

    private fun submitDestinationSearch() {
        val query = destinationSearchInput.text?.toString().orEmpty().trim()
        Log.d(TAG, "submitDestinationSearch entered. query='$query', overlayVisible=${destinationSearchOverlay.visibility == View.VISIBLE}")
        if (query.length < 2) {
            Toast.makeText(this, "검색어를 2글자 이상 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        pendingAutocompleteRunnable?.let { autocompleteHandler.removeCallbacks(it) }
        pendingAutocompleteRunnable = null
        autocompleteRequestVersion++

        val requestVersion = ++destinationSearchRequestVersion
        destinationSearchRepository.search(query, object : ApiCallback<List<DestinationSearchSuggestion>> {
            override fun onSuccess(result: List<DestinationSearchSuggestion>) {
                if (requestVersion != destinationSearchRequestVersion || destinationSearchOverlay.visibility != View.VISIBLE) {
                    return
                }

                val suggestions = result
                destinationAutocompleteAdapter.submitList(suggestions)
                destinationAutocompleteRecycler.visibility = if (suggestions.isEmpty()) View.GONE else View.VISIBLE
                destinationAutocompleteEmptyView.text = "검색 결과가 없습니다."
                destinationAutocompleteEmptyView.visibility = if (suggestions.isEmpty()) View.VISIBLE else View.GONE
                cachedDestinationSearchResults = suggestions
                val coordinateCount = suggestions.count { it.latitude != null && it.longitude != null }
                closeDestinationSearchOverlay(restoreMapAfterClose = false)
                showDestinationSearchResultsPanel(suggestions)
                Log.d(TAG, "Destination search results updated. query='$query' size=${suggestions.size}, coordinateCount=$coordinateCount")
            }

            override fun onError(error: Throwable) {
                if (requestVersion != destinationSearchRequestVersion || destinationSearchOverlay.visibility != View.VISIBLE) {
                    return
                }

                destinationAutocompleteAdapter.submitList(emptyList())
                destinationAutocompleteRecycler.visibility = View.GONE
                destinationAutocompleteEmptyView.text = "검색 결과가 없습니다."
                destinationAutocompleteEmptyView.visibility = View.VISIBLE
                cachedDestinationSearchResults = emptyList()
                mapController?.showDestinationSearchResults(emptyList())
                Log.w(TAG, "Destination search failed for query='$query'", error)
            }
        })
    }

    private fun scheduleAutocompleteSearch(query: String) {
        pendingAutocompleteRunnable?.let { autocompleteHandler.removeCallbacks(it) }
        val runnable = Runnable {
            performAutocompleteSearch(query)
        }
        pendingAutocompleteRunnable = runnable
        autocompleteHandler.postDelayed(runnable, AUTOCOMPLETE_DEBOUNCE_MILLIS)
    }

    private fun performAutocompleteSearch(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) {
            destinationAutocompleteAdapter.submitList(emptyList())
            destinationAutocompleteEmptyView.visibility = View.GONE
            destinationAutocompleteRecycler.visibility = View.GONE
            Log.d(TAG, "Destination autocomplete skipped. query too short: '$trimmedQuery'")
            return
        }

        val requestVersion = ++autocompleteRequestVersion
        destinationAutocompleteRepository.search(trimmedQuery, object : ApiCallback<List<DestinationSearchSuggestion>> {
            override fun onSuccess(result: List<com.example.cheapestoilfinder.destination.DestinationSearchSuggestion>) {
                if (requestVersion != autocompleteRequestVersion || destinationSearchOverlay.visibility != View.VISIBLE) {
                    return
                }

                val suggestions = result.take(4)
                destinationAutocompleteAdapter.submitList(suggestions)
                destinationAutocompleteRecycler.visibility = if (suggestions.isEmpty()) View.GONE else View.VISIBLE
                destinationAutocompleteEmptyView.text = "검색어에 맞는 자동완성 후보가 없습니다."
                destinationAutocompleteEmptyView.visibility = if (suggestions.isEmpty()) View.VISIBLE else View.GONE
                Log.d(TAG, "Destination autocomplete updated. query='$trimmedQuery' size=${suggestions.size}")
            }

            override fun onError(error: Throwable) {
                if (requestVersion != autocompleteRequestVersion || destinationSearchOverlay.visibility != View.VISIBLE) {
                    return
                }

                destinationAutocompleteAdapter.submitList(emptyList())
                destinationAutocompleteRecycler.visibility = View.GONE
                destinationAutocompleteEmptyView.text = "검색어에 맞는 자동완성 후보가 없습니다."
                destinationAutocompleteEmptyView.visibility = View.VISIBLE
                Log.w(TAG, "Destination autocomplete failed for query='$trimmedQuery'", error)
            }
        })
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun configureDestinationSearchResultsPanel() {
        destinationSearchResultsContainer.visibility = View.GONE
        destinationSearchResultsContainer.isClickable = false
        destinationSearchResultsSheet.isClickable = false

        destinationSearchResultsHandleTouchArea.setOnTouchListener { _, event ->
            handleDestinationSearchResultsSheetTouch(event)
        }
        destinationSearchResultsHandle.setOnTouchListener { _, event ->
            handleDestinationSearchResultsSheetTouch(event)
        }
        destinationSearchResultsHandleTouchArea.setOnClickListener {
            when (destinationSearchResultsSheetState) {
                DestinationResultsSheetState.MINIMIZED -> collapseDestinationSearchResultsPanel(animated = true)
                DestinationResultsSheetState.COLLAPSED -> expandDestinationSearchResultsPanel()
                DestinationResultsSheetState.EXPANDED -> collapseDestinationSearchResultsPanel(animated = true)
                DestinationResultsSheetState.HIDDEN -> Unit
            }
        }
        destinationSearchResultsHandle.setOnClickListener {
            when (destinationSearchResultsSheetState) {
                DestinationResultsSheetState.MINIMIZED -> collapseDestinationSearchResultsPanel(animated = true)
                DestinationResultsSheetState.COLLAPSED -> expandDestinationSearchResultsPanel()
                DestinationResultsSheetState.EXPANDED -> collapseDestinationSearchResultsPanel(animated = true)
                DestinationResultsSheetState.HIDDEN -> Unit
            }
        }

        destinationSearchResultsContainer.post {
            measureDestinationSearchResultsPanel()
        }
    }

    private fun configureRouteRecommendationPanel() {
        routeRecommendationContainer.visibility = View.GONE
        routeRecommendationContainer.isClickable = false
        routeRecommendationSheet.isClickable = false

        routeRecommendationHandleTouchArea.setOnTouchListener { _, event ->
            handleRouteRecommendationSheetTouch(event)
        }
        routeRecommendationHandle.setOnTouchListener { _, event ->
            handleRouteRecommendationSheetTouch(event)
        }
        routeRecommendationHandleTouchArea.setOnClickListener {
            toggleRouteRecommendationPanelByTap()
        }
        routeRecommendationHandle.setOnClickListener {
            toggleRouteRecommendationPanelByTap()
        }
    }

    private fun toggleRouteRecommendationPanelByTap() {
        when (routeRecommendationSheetState) {
            DestinationResultsSheetState.MINIMIZED -> collapseRouteRecommendationPanel(animated = true)
            DestinationResultsSheetState.COLLAPSED -> expandRouteRecommendationPanel()
            DestinationResultsSheetState.EXPANDED -> collapseRouteRecommendationPanel(animated = true)
            DestinationResultsSheetState.HIDDEN -> Unit
        }
    }

    private fun measureDestinationSearchResultsPanel() {
        val targetHeight = resources.displayMetrics.heightPixels
        val density = resources.displayMetrics.density
        destinationSearchResultsSheetHeightPx = targetHeight
        destinationSearchResultsPeekHeightPx = (targetHeight * 0.5f).roundToInt()
        val minimizedHeight = (density * 96f).roundToInt()
        destinationSearchResultsCollapsedTranslationY = (targetHeight - destinationSearchResultsPeekHeightPx)
            .coerceAtLeast(0)
            .toFloat()
        destinationSearchResultsMinimizedTranslationY = (targetHeight - minimizedHeight)
            .coerceAtLeast(0)
            .toFloat()
        destinationSearchResultsSheet.layoutParams =
            destinationSearchResultsSheet.layoutParams.apply { height = targetHeight }
        destinationSearchResultsSheet.requestLayout()
        routeRecommendationSheet.layoutParams =
            routeRecommendationSheet.layoutParams.apply { height = targetHeight }
        routeRecommendationSheet.requestLayout()
        routeRecommendationSheetCollapsedTranslationY = destinationSearchResultsCollapsedTranslationY
        routeRecommendationSheetMinimizedTranslationY = destinationSearchResultsMinimizedTranslationY
        if (routeRecommendationContainer.visibility != View.VISIBLE) {
            routeRecommendationSheet.translationY = targetHeight.toFloat()
        }
        if (destinationSearchResultsSheetState == DestinationResultsSheetState.HIDDEN) {
            destinationSearchResultsSheet.translationY = targetHeight.toFloat()
        }
        updateSetDestinationButtonPosition()
        updateDestinationFloatingControlsPosition()
    }

    private fun showDestinationSearchResultsPanel(results: List<DestinationSearchSuggestion>) {
        destinationSearchResultsContainer.visibility = View.VISIBLE
        destinationSearchResultsContainer.bringToFront()
        destinationSearchResultsSheet.post {
            if (destinationSearchResultsSheetHeightPx <= 0) {
                measureDestinationSearchResultsPanel()
            }

            destinationSearchResultsCountView.text = getString(
                R.string.destination_search_results_count_format,
                results.size
            )
            destinationSearchResultsAdapter.submitList(results)
            destinationSearchResultsEmptyView.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
            destinationSearchResultsRecycler.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
            mapController?.showDestinationSearchResults(
                cachedDestinationSearchResults,
                destinationSearchResultsPeekHeightPx
            )
            collapseDestinationSearchResultsPanel(animated = true)
        }
    }

    private fun selectDestinationSearchResult(
        suggestion: DestinationSearchSuggestion,
        moveCamera: Boolean
    ) {
        isDestinationConfirmed = false
        val latitude = suggestion.latitude
        val longitude = suggestion.longitude
        if (latitude == null || longitude == null) {
            Log.w(TAG, "Destination search result selected without coordinates: ${suggestion.displayText}")
            return
        }

        val point = LocationPoint(
            latitude = latitude,
            longitude = longitude,
            name = suggestion.displayText,
            address = suggestion.description
        )
        selectedDestinationSuggestion = suggestion
        selectedDestinationPoint = point
        mapController?.selectDestinationSearchResult(suggestion)
        if (moveCamera) {
            mapController?.moveCameraAboveBottomSheet(point, 15)
        }
        showSetDestinationButton()
        destinationSearchInput.setText(suggestion.displayText)
        destinationSearchInput.setSelection(suggestion.displayText.length)
        destinationSearchQuery = suggestion.displayText
        Log.d(TAG, "Destination search result selected: ${suggestion.displayText}, $latitude, $longitude, moveCamera=$moveCamera")
    }

    private fun showSetDestinationButton() {
        setDestinationButton.visibility = View.VISIBLE
        setDestinationButton.bringToFront()
        updateSetDestinationButtonPosition()
    }

    private fun updateSetDestinationButtonPosition() {
        if (selectedDestinationPoint == null || setDestinationButton.visibility == View.GONE) {
            return
        }

        if (destinationSearchResultsSheetState == DestinationResultsSheetState.EXPANDED) {
            setDestinationButton.visibility = View.INVISIBLE
            return
        }

        val parentHeight = (setDestinationButton.parent as? View)?.height ?: return
        if (parentHeight <= 0) {
            setDestinationButton.post { updateSetDestinationButtonPosition() }
            return
        }

        setDestinationButton.visibility = View.VISIBLE
        setDestinationButton.bringToFront()
        val density = resources.displayMetrics.density
        val bottomMarginPx = 20f * density
        val gapPx = 12f * density
        val sheetTop = when (destinationSearchResultsSheetState) {
            DestinationResultsSheetState.MINIMIZED,
            DestinationResultsSheetState.COLLAPSED,
            DestinationResultsSheetState.EXPANDED -> destinationSearchResultsSheet.translationY
            DestinationResultsSheetState.HIDDEN -> parentHeight.toFloat()
        }
        val currentButtonBottom = parentHeight - bottomMarginPx
        val desiredButtonBottom = (sheetTop - gapPx).coerceAtLeast(
            setDestinationButton.height.toFloat() + bottomMarginPx
        )
        setDestinationButton.translationY = desiredButtonBottom - currentButtonBottom
    }

    protected fun updateDestinationFloatingControlsPosition() {
        if (destinationSearchResultsSheetState == DestinationResultsSheetState.EXPANDED) {
            return
        }
        if (routeRecommendationSheetState == DestinationResultsSheetState.EXPANDED) {
            return
        }

        val parentHeight = (destinationGpsButton.parent as? View)?.height ?: return
        if (parentHeight <= 0 || destinationGpsButton.bottom <= 0) {
            destinationGpsButton.post { updateDestinationFloatingControlsPosition() }
            return
        }

        val activeSheetTop = when {
            destinationSearchResultsSheetState == DestinationResultsSheetState.MINIMIZED ||
                destinationSearchResultsSheetState == DestinationResultsSheetState.COLLAPSED ->
                destinationSearchResultsSheet.translationY
            routeRecommendationSheetState == DestinationResultsSheetState.MINIMIZED ||
                routeRecommendationSheetState == DestinationResultsSheetState.COLLAPSED ->
                routeRecommendationSheet.translationY
            else -> null
        }
        val targetTranslationY = activeSheetTop?.let { calculateFloatingControlsTranslation(it) } ?: 0f

        setDestinationFloatingControlsTranslation(targetTranslationY, animated = false)
    }

    protected fun updateDestinationFloatingControlsPositionForSheetTop(
        sheetTop: Float,
        animated: Boolean,
        durationMillis: Long
    ) {
        if (destinationGpsButton.bottom <= 0) {
            destinationGpsButton.post {
                updateDestinationFloatingControlsPositionForSheetTop(sheetTop, animated, durationMillis)
            }
            return
        }

        val targetTranslationY = calculateFloatingControlsTranslation(sheetTop)
        setDestinationFloatingControlsTranslation(targetTranslationY, animated, durationMillis)
    }

    private fun calculateFloatingControlsTranslation(sheetTop: Float): Float {
        val density = resources.displayMetrics.density
        val gapPx = 12f * density
        val currentGpsBottom = destinationGpsButton.bottom.toFloat()
        val desiredGpsBottom = (sheetTop - gapPx)
            .coerceAtLeast(destinationGpsButton.height.toFloat() + gapPx)
        return desiredGpsBottom - currentGpsBottom
    }

    private fun setDestinationFloatingControlsTranslation(
        translationY: Float,
        animated: Boolean,
        durationMillis: Long = 0L
    ) {
        destinationGpsButton.animate().cancel()
        destinationZoomControlsContainer.animate().cancel()
        if (animated) {
            destinationGpsButton.animate()
                .translationY(translationY)
                .setDuration(durationMillis)
                .start()
            destinationZoomControlsContainer.animate()
                .translationY(translationY)
                .setDuration(durationMillis)
                .start()
        } else {
            destinationGpsButton.translationY = translationY
            destinationZoomControlsContainer.translationY = translationY
        }
    }

    private fun setSelectedDestinationAndDrawRoute() {
        val origin = currentGpsPoint
        val destination = selectedDestinationPoint
        val suggestion = selectedDestinationSuggestion

        if (origin == null) {
            Toast.makeText(this, "현재 위치를 먼저 확인해 주세요.", Toast.LENGTH_SHORT).show()
            refreshCurrentLocationFromGps()
            return
        }

        if (destination == null || suggestion == null) {
            Toast.makeText(this, "목적지를 먼저 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val settings = userPreferenceManager.loadSettings()
        val request = RouteStationSearchRequest(
            originLatitude = origin.latitude,
            originLongitude = origin.longitude,
            destinationLatitude = destination.latitude,
            destinationLongitude = destination.longitude,
            routePolyline = null,
            radiusKm = 5.0,
            fuelAmountLiters = settings.refuelAmountLiter,
            fuelEfficiencyKmPerLiter = settings.fuelEfficiencyKmPerLiter,
            fuelTypes = listOf(
                UserFuelType.GAS_HIGH.backendFuelType,
                UserFuelType.GAS_LOW.backendFuelType,
                UserFuelType.DIESEL.backendFuelType
            ),
            sortOrder = StationSearchSortOrder.ESTIMATED_TOTAL_COST_ASC,
            routeResultMode = RouteResultMode.ROUTE_WITH_STATIONS,
            originLabel = origin.name.ifBlank { "현재 위치" },
            destinationLabel = suggestion.displayText
        )

        setDestinationButton.isEnabled = false
        showDestinationRouteLoading()
        stationRepository.searchRouteStations(request, object : ApiCallback<StationSearchResponse> {
            override fun onSuccess(result: StationSearchResponse) {
                val route = result.route
                val routePolyline = route?.routePolyline?.takeIf { it.isNotBlank() }
                    ?: buildFallbackRoutePolyline(origin, destination)
                val routeInfo = RouteInfo(
                    origin = origin,
                    destination = destination,
                    distanceMeters = route?.distanceMeters ?: 0,
                    durationSeconds = route?.durationSeconds ?: 0,
                    tollFeeWon = route?.tollFeeWon ?: 0,
                    polyline = routePolyline
                )
                val recommendedStations = prepareRouteRecommendationStations(
                    StationDisplayMapper.toGasStations(result),
                    routeInfo
                ).take(5)
                hideDestinationRouteLoading()
                enterDestinationConfirmedMode(suggestion, routeInfo, recommendedStations)
                Log.i(
                    TAG,
                    "Destination route loaded: destination=${suggestion.displayText}, distance=${route?.distanceMeters}, duration=${route?.durationSeconds}, recommendations=${recommendedStations.size}"
                )
            }

            override fun onError(error: Throwable) {
                hideDestinationRouteLoading()
                setDestinationButton.isEnabled = true
                Log.w(TAG, "Failed to load destination route.", error)
                Toast.makeText(this@DestinationActivity, "목적지 경로를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDestinationRouteLoading() {
        destinationRouteLoadingOverlay.visibility = View.VISIBLE
        destinationRouteLoadingOverlay.bringToFront()
    }

    private fun hideDestinationRouteLoading() {
        destinationRouteLoadingOverlay.visibility = View.GONE
    }

    private fun enterDestinationConfirmedMode(
        suggestion: DestinationSearchSuggestion,
        routeInfo: RouteInfo,
        recommendedStations: List<GasStation>
    ) {
        destinationResultsStateBeforeConfirm = destinationSearchResultsSheetState
        isDestinationConfirmed = true
        confirmedBaseRouteInfo = routeInfo
        destinationSearchResultsSheet.animate().cancel()
        destinationSearchResultsContainer.visibility = View.GONE
        destinationSearchResultsSheetState = DestinationResultsSheetState.HIDDEN
        setDestinationButton.isEnabled = true
        setDestinationButton.visibility = View.GONE
        mapController?.commitDestinationSearchResult(suggestion)
        mapController?.showRoute(routeInfo, RouteCameraPlacement.CENTER)
        mapController?.showRouteRecommendedStations(recommendedStations)
        showRouteRecommendationPanel(recommendedStations)
        updateDestinationFloatingControlsPosition()
    }

    private fun restoreDestinationBeforeConfirmState() {
        val selectedSuggestion = selectedDestinationSuggestion
        isDestinationConfirmed = false
        confirmedBaseRouteInfo = null
        mapController?.clearRoute()
        mapController?.clearDetourRoute()
        mapController?.highlightSelectedStation(null)
        mapController?.showRouteRecommendedStations(emptyList())
        mapController?.restoreDestinationSearchResults(cachedDestinationSearchResults, selectedSuggestion)
        hideRouteRecommendationPanel()

        destinationSearchResultsContainer.visibility = View.VISIBLE
        destinationSearchResultsContainer.bringToFront()
        destinationSearchResultsAdapter.submitList(cachedDestinationSearchResults)
        destinationSearchResultsCountView.text = getString(
            R.string.destination_search_results_count_format,
            cachedDestinationSearchResults.size
        )
        destinationSearchResultsEmptyView.visibility =
            if (cachedDestinationSearchResults.isEmpty()) View.VISIBLE else View.GONE
        destinationSearchResultsRecycler.visibility =
            if (cachedDestinationSearchResults.isEmpty()) View.GONE else View.VISIBLE
        setDestinationButton.visibility = if (selectedSuggestion == null) View.GONE else View.VISIBLE
        setDestinationButton.isEnabled = true

        restoreDestinationSearchResultsPanelState(destinationResultsStateBeforeConfirm)
        updateSetDestinationButtonPosition()
        updateDestinationFloatingControlsPosition()
    }

    private fun showRouteRecommendationPanel(stations: List<GasStation>) {
        if (routeRecommendationSheetCollapsedTranslationY <= 0f) {
            measureDestinationSearchResultsPanel()
        }
        routeRecommendationContainer.visibility = View.VISIBLE
        routeRecommendationContainer.bringToFront()
        routeRecommendationCountView.text = getString(
            R.string.destination_search_results_count_format,
            stations.size
        )
        routeRecommendationAdapter.submitList(stations)
        routeRecommendationEmptyView.visibility = if (stations.isEmpty()) View.VISIBLE else View.GONE
        routeRecommendationRecycler.visibility = if (stations.isEmpty()) View.GONE else View.VISIBLE
        routeRecommendationSheet.animate().cancel()
        collapseRouteRecommendationPanel(animated = false)
    }

    private fun hideRouteRecommendationPanel() {
        routeRecommendationSheet.animate().cancel()
        routeRecommendationContainer.visibility = View.GONE
        routeRecommendationAdapter.submitList(emptyList())
        routeRecommendationEmptyView.visibility = View.GONE
        routeRecommendationRecycler.visibility = View.GONE
        routeRecommendationSheet.translationY = destinationSearchResultsSheetHeightPx.toFloat()
        routeRecommendationSheetState = DestinationResultsSheetState.HIDDEN
        updateDestinationFloatingControlsPosition()
    }

    private fun expandRouteRecommendationPanel() {
        if (routeRecommendationSheetState == DestinationResultsSheetState.HIDDEN) {
            return
        }

        routeRecommendationContainer.visibility = View.VISIBLE
        routeRecommendationContainer.bringToFront()
        routeRecommendationSheet.animate().cancel()
        routeRecommendationSheetState = DestinationResultsSheetState.EXPANDED
        routeRecommendationSheet.animate()
            .translationY(0f)
            .setDuration(200L)
            .start()
    }

    private fun collapseRouteRecommendationPanel(animated: Boolean) {
        if (destinationSearchResultsSheetHeightPx <= 0) {
            measureDestinationSearchResultsPanel()
        }

        routeRecommendationContainer.visibility = View.VISIBLE
        routeRecommendationContainer.bringToFront()
        routeRecommendationSheet.animate().cancel()
        routeRecommendationSheetState = DestinationResultsSheetState.COLLAPSED
        val target = routeRecommendationSheetCollapsedTranslationY
        updateDestinationFloatingControlsPositionForSheetTop(target, animated, 220L)
        if (animated) {
            routeRecommendationSheet.animate()
                .translationY(target)
                .setDuration(220L)
                .start()
        } else {
            routeRecommendationSheet.translationY = target
        }
    }

    protected fun minimizeRouteRecommendationPanel(animated: Boolean) {
        if (routeRecommendationSheetState == DestinationResultsSheetState.HIDDEN) {
            return
        }

        routeRecommendationSheet.animate().cancel()
        routeRecommendationSheetState = DestinationResultsSheetState.MINIMIZED
        val target = routeRecommendationSheetMinimizedTranslationY
        updateDestinationFloatingControlsPositionForSheetTop(target, animated, 180L)
        if (animated) {
            routeRecommendationSheet.animate()
                .translationY(target)
                .setDuration(180L)
                .start()
        } else {
            routeRecommendationSheet.translationY = target
        }
    }

    private fun handleRouteRecommendationBack(): Boolean {
        return when (routeRecommendationSheetState) {
            DestinationResultsSheetState.EXPANDED -> {
                collapseRouteRecommendationPanel(animated = true)
                true
            }
            DestinationResultsSheetState.COLLAPSED -> {
                minimizeRouteRecommendationPanel(animated = true)
                true
            }
            DestinationResultsSheetState.MINIMIZED,
            DestinationResultsSheetState.HIDDEN -> false
        }
    }

    private fun handleRouteRecommendationSheetTouch(event: MotionEvent): Boolean {
        if (routeRecommendationSheetState == DestinationResultsSheetState.HIDDEN) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                routeRecommendationSheetDragging = true
                routeRecommendationSheetDragStartY = event.rawY
                routeRecommendationSheetDragStartTranslationY = routeRecommendationSheet.translationY
                routeRecommendationSheet.animate().cancel()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!routeRecommendationSheetDragging) {
                    return false
                }

                val delta = event.rawY - routeRecommendationSheetDragStartY
                if (routeRecommendationSheetState == DestinationResultsSheetState.EXPANDED && delta < 0f) {
                    return true
                }

                val (minTranslation, maxTranslation) = when (routeRecommendationSheetState) {
                    DestinationResultsSheetState.EXPANDED -> 0f to routeRecommendationSheetCollapsedTranslationY
                    DestinationResultsSheetState.COLLAPSED -> 0f to routeRecommendationSheetMinimizedTranslationY
                    DestinationResultsSheetState.MINIMIZED -> routeRecommendationSheetCollapsedTranslationY to routeRecommendationSheetMinimizedTranslationY
                    DestinationResultsSheetState.HIDDEN -> return false
                }
                val nextTranslation = (routeRecommendationSheetDragStartTranslationY + delta)
                    .coerceIn(minTranslation, maxTranslation)
                routeRecommendationSheet.translationY = nextTranslation
                if (nextTranslation >= routeRecommendationSheetCollapsedTranslationY) {
                    updateDestinationFloatingControlsPositionForSheetTop(
                        nextTranslation,
                        animated = false,
                        durationMillis = 0L
                    )
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (!routeRecommendationSheetDragging) {
                    return false
                }

                routeRecommendationSheetDragging = false
                val dragDelta = routeRecommendationSheet.translationY -
                    routeRecommendationSheetDragStartTranslationY
                if (abs(dragDelta) < 24f) {
                    settleRouteRecommendationPanel()
                    return true
                }

                if (dragDelta < 0f) {
                    when (routeRecommendationSheetState) {
                        DestinationResultsSheetState.MINIMIZED -> collapseRouteRecommendationPanel(animated = true)
                        DestinationResultsSheetState.COLLAPSED -> expandRouteRecommendationPanel()
                        DestinationResultsSheetState.EXPANDED -> expandRouteRecommendationPanel()
                        DestinationResultsSheetState.HIDDEN -> Unit
                    }
                } else {
                    when (routeRecommendationSheetState) {
                        DestinationResultsSheetState.EXPANDED -> collapseRouteRecommendationPanel(animated = true)
                        DestinationResultsSheetState.COLLAPSED -> minimizeRouteRecommendationPanel(animated = true)
                        DestinationResultsSheetState.MINIMIZED -> minimizeRouteRecommendationPanel(animated = true)
                        DestinationResultsSheetState.HIDDEN -> Unit
                    }
                }
                return true
            }
        }

        return false
    }

    private fun settleRouteRecommendationPanel() {
        when (routeRecommendationSheetState) {
            DestinationResultsSheetState.EXPANDED -> expandRouteRecommendationPanel()
            DestinationResultsSheetState.COLLAPSED -> collapseRouteRecommendationPanel(animated = true)
            DestinationResultsSheetState.MINIMIZED -> minimizeRouteRecommendationPanel(animated = true)
            DestinationResultsSheetState.HIDDEN -> Unit
        }
    }

    protected open fun showDetourRouteForStation(station: GasStation) {
        if (!isDestinationConfirmed) {
            return
        }
        val baseRoute = confirmedBaseRouteInfo ?: return
        val detour = station.detourRoute
        val detourPolyline = detour?.routePolyline?.takeIf { it.isNotBlank() }
        if (detourPolyline == null) {
            Toast.makeText(this, "경유 경로 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Selected route recommendation without detour polyline: ${station.id}")
            return
        }

        val detourRouteInfo = RouteInfo(
            origin = baseRoute.origin,
            destination = baseRoute.destination,
            distanceMeters = detour.distanceMeters,
            durationSeconds = detour.durationSeconds,
            tollFeeWon = detour.tollFeeWon,
            polyline = detourPolyline
        )
        val extraDistanceMeters = station.routeExtraDistanceMeters
            ?: (detour.distanceMeters - baseRoute.distanceMeters).takeIf { it >= 0 }
        mapController?.highlightSelectedStation(station.id, StationMarkerHighlight.ROUTE_RECOMMENDATION)
        mapController?.showDetourRoute(detourRouteInfo, extraDistanceMeters, station.locationPoint)
        mapController?.fitRouteWithWaypoints(
            detourRouteInfo,
            listOf(station.locationPoint),
            RouteCameraPlacement.CENTER
        )
        Log.d(TAG, "Detour route selected: station=${station.id}, extraDistance=$extraDistanceMeters")
    }

    private fun prepareRouteRecommendationStations(
        stations: List<GasStation>,
        baseRouteInfo: RouteInfo
    ): List<GasStation> {
        val settings = userPreferenceManager.loadSettings()
        val selectedFuelType = settings.fuelType
        return stations
            .mapNotNull { station ->
                val selectedFuelPrice = resolveSelectedFuelPrice(station, selectedFuelType)
                if (selectedFuelPrice == null) {
                    return@mapNotNull null
                }
                val detourDistanceMeters = station.routeExtraDistanceMeters
                    ?: station.detourRoute?.distanceMeters
                        ?.minus(baseRouteInfo.distanceMeters)
                        ?.takeIf { it >= 0 }
                val effectiveDistanceMeters = detourDistanceMeters ?: station.distanceMeters.takeIf { it > 0 }
                val moveCost = CostCalculator.calculateMoveCost(
                    effectiveDistanceMeters,
                    settings.fuelEfficiencyKmPerLiter,
                    selectedFuelPrice
                )
                val refuelCost = CostCalculator.calculateRefuelCost(
                    settings.refuelAmountLiter,
                    selectedFuelPrice
                )
                val totalCost = CostCalculator.calculateTotalExpectedCost(moveCost, refuelCost)
                station.copy(
                    fuelType = selectedFuelType.backendFuelType.name,
                    pricePerLiter = selectedFuelPrice,
                    distanceMeters = effectiveDistanceMeters ?: station.distanceMeters,
                    costSummary = StationCostSummary(
                        selectedFuelType = selectedFuelType,
                        selectedFuelPricePerLiter = selectedFuelPrice,
                        distanceMeters = effectiveDistanceMeters,
                        distanceKm = effectiveDistanceMeters?.div(1000.0),
                        moveCostWon = moveCost,
                        refuelCostWon = refuelCost,
                        totalExpectedCostWon = totalCost,
                        unavailableReason = if (totalCost == null) {
                            getString(R.string.station_info_cost_unavailable)
                        } else {
                            null
                        }
                    )
                )
            }
            .sortedWith(
                compareBy<GasStation> { it.costSummary?.moveCostWon ?: Int.MAX_VALUE }
                    .thenBy { it.routeExtraDistanceMeters ?: Int.MAX_VALUE }
                    .thenBy { it.costSummary?.totalExpectedCostWon ?: Int.MAX_VALUE }
                    .thenBy { it.name }
            )
    }

    private fun resolveSelectedFuelPrice(station: GasStation, selectedFuelType: UserFuelType): Int? {
        val prices = station.fuelPrices ?: return null
        return when (selectedFuelType) {
            UserFuelType.GAS_HIGH -> prices.premiumGasolineWon
            UserFuelType.GAS_LOW -> prices.regularGasolineWon
            UserFuelType.DIESEL -> prices.dieselWon
            UserFuelType.LPG -> prices.lpgWon
        }?.takeIf { it > 0 }
    }

    private fun restoreDestinationSearchResultsPanelState(
        previousState: DestinationResultsSheetState
    ) {
        if (destinationSearchResultsSheetHeightPx <= 0) {
            measureDestinationSearchResultsPanel()
        }

        destinationSearchResultsSheet.animate().cancel()
        destinationSearchResultsContainer.visibility = View.VISIBLE
        destinationSearchResultsContainer.bringToFront()
        val restoredState = when (previousState) {
            DestinationResultsSheetState.HIDDEN -> DestinationResultsSheetState.MINIMIZED
            else -> previousState
        }
        destinationSearchResultsSheetState = restoredState
        destinationSearchResultsSheet.translationY = when (restoredState) {
            DestinationResultsSheetState.MINIMIZED -> destinationSearchResultsMinimizedTranslationY
            DestinationResultsSheetState.COLLAPSED -> destinationSearchResultsCollapsedTranslationY
            DestinationResultsSheetState.EXPANDED -> 0f
            DestinationResultsSheetState.HIDDEN -> destinationSearchResultsSheetHeightPx.toFloat()
        }
    }

    private fun hideDestinationSearchResultsPanel(animated: Boolean) {
        if (destinationSearchResultsContainer.visibility != View.VISIBLE) {
            return
        }

        val sheet = destinationSearchResultsSheet
            if (destinationSearchResultsSheetHeightPx <= 0) {
                destinationSearchResultsContainer.visibility = View.GONE
                mapController?.showDestinationSearchResults(emptyList())
                selectedDestinationSuggestion = null
                selectedDestinationPoint = null
                setDestinationButton.visibility = View.GONE
                onDestinationSearchResultsHidden()
                return
            }

        sheet.animate().cancel()
        if (animated) {
            sheet.animate()
                .translationY(destinationSearchResultsSheetHeightPx.toFloat())
                .setDuration(180L)
                .withEndAction {
                    destinationSearchResultsContainer.visibility = View.GONE
                    destinationSearchResultsAdapter.submitList(emptyList())
                    destinationSearchResultsEmptyView.visibility = View.GONE
                    destinationSearchResultsRecycler.visibility = View.GONE
                    mapController?.showDestinationSearchResults(emptyList())
                    destinationSearchResultsSheetState = DestinationResultsSheetState.HIDDEN
                    selectedDestinationSuggestion = null
                    selectedDestinationPoint = null
                    setDestinationButton.visibility = View.GONE
                    updateDestinationFloatingControlsPosition()
                    onDestinationSearchResultsHidden()
                }
                .start()
        } else {
            sheet.translationY = destinationSearchResultsSheetHeightPx.toFloat()
            destinationSearchResultsContainer.visibility = View.GONE
            destinationSearchResultsAdapter.submitList(emptyList())
            destinationSearchResultsEmptyView.visibility = View.GONE
            destinationSearchResultsRecycler.visibility = View.GONE
            mapController?.showDestinationSearchResults(emptyList())
            destinationSearchResultsSheetState = DestinationResultsSheetState.HIDDEN
            selectedDestinationSuggestion = null
            selectedDestinationPoint = null
            setDestinationButton.visibility = View.GONE
            updateDestinationFloatingControlsPosition()
            onDestinationSearchResultsHidden()
        }
    }

    protected open fun onDestinationSearchResultsHidden() = Unit

    private fun expandDestinationSearchResultsPanel() {
        if (destinationSearchResultsSheetState == DestinationResultsSheetState.HIDDEN) {
            return
        }

        destinationSearchResultsSheet.animate().cancel()
        destinationSearchResultsSheetState = DestinationResultsSheetState.EXPANDED
        destinationSearchResultsSheet.animate()
            .translationY(0f)
                .setDuration(200L)
                .withEndAction {
                    updateSetDestinationButtonPosition()
                    updateDestinationFloatingControlsPosition()
                }
                .start()
        updateSetDestinationButtonPosition()
    }

    private fun collapseDestinationSearchResultsPanel(animated: Boolean) {
        if (destinationSearchResultsSheetHeightPx <= 0) {
            measureDestinationSearchResultsPanel()
        }

        destinationSearchResultsContainer.visibility = View.VISIBLE
        destinationSearchResultsContainer.bringToFront()
        destinationSearchResultsSheet.animate().cancel()
        destinationSearchResultsSheetState = DestinationResultsSheetState.COLLAPSED
        val target = destinationSearchResultsCollapsedTranslationY
        updateDestinationFloatingControlsPositionForSheetTop(target, animated, 220L)
        if (animated) {
            destinationSearchResultsSheet.animate()
                .translationY(target)
                .setDuration(220L)
                .withEndAction {
                    updateSetDestinationButtonPosition()
                    updateDestinationFloatingControlsPosition()
                }
                .start()
        } else {
            destinationSearchResultsSheet.translationY = target
            updateSetDestinationButtonPosition()
            updateDestinationFloatingControlsPosition()
        }
    }

    private fun minimizeDestinationSearchResultsPanel(animated: Boolean) {
        if (destinationSearchResultsSheetState == DestinationResultsSheetState.HIDDEN) {
            return
        }

        destinationSearchResultsSheet.animate().cancel()
        destinationSearchResultsSheetState = DestinationResultsSheetState.MINIMIZED
        updateDestinationFloatingControlsPositionForSheetTop(
            destinationSearchResultsMinimizedTranslationY,
            animated,
            180L
        )
        if (animated) {
            destinationSearchResultsSheet.animate()
                .translationY(destinationSearchResultsMinimizedTranslationY)
                .setDuration(180L)
                .withEndAction {
                    updateSetDestinationButtonPosition()
                    updateDestinationFloatingControlsPosition()
                }
                .start()
        } else {
            destinationSearchResultsSheet.translationY = destinationSearchResultsMinimizedTranslationY
            updateSetDestinationButtonPosition()
            updateDestinationFloatingControlsPosition()
        }
    }

    private fun handleDestinationResultsBack() {
        when (destinationSearchResultsSheetState) {
            DestinationResultsSheetState.EXPANDED -> collapseDestinationSearchResultsPanel(animated = true)
            DestinationResultsSheetState.COLLAPSED -> minimizeDestinationSearchResultsPanel(animated = true)
            DestinationResultsSheetState.MINIMIZED -> hideDestinationSearchResultsPanel(animated = true)
            DestinationResultsSheetState.HIDDEN -> Unit
        }
    }

    private fun handleDestinationSearchResultsSheetTouch(event: MotionEvent): Boolean {
        if (destinationSearchResultsSheetState == DestinationResultsSheetState.HIDDEN) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                destinationSearchResultsDragging = true
                destinationSearchResultsDragStartY = event.rawY
                destinationSearchResultsDragStartTranslationY = destinationSearchResultsSheet.translationY
                destinationSearchResultsSheet.animate().cancel()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!destinationSearchResultsDragging) {
                    return false
                }

                val delta = event.rawY - destinationSearchResultsDragStartY
                if (destinationSearchResultsSheetState == DestinationResultsSheetState.EXPANDED && delta < 0f) {
                    return true
                }

                val (minTranslation, maxTranslation) = when (destinationSearchResultsSheetState) {
                    DestinationResultsSheetState.EXPANDED -> 0f to destinationSearchResultsCollapsedTranslationY
                    DestinationResultsSheetState.COLLAPSED -> 0f to destinationSearchResultsMinimizedTranslationY
                    DestinationResultsSheetState.MINIMIZED -> destinationSearchResultsCollapsedTranslationY to destinationSearchResultsMinimizedTranslationY
                    DestinationResultsSheetState.HIDDEN -> return false
                }
                val nextTranslation = (destinationSearchResultsDragStartTranslationY + delta)
                    .coerceIn(minTranslation, maxTranslation)
                destinationSearchResultsSheet.translationY = nextTranslation
                updateSetDestinationButtonPosition()
                updateDestinationFloatingControlsPosition()
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (!destinationSearchResultsDragging) {
                    return false
                }

                destinationSearchResultsDragging = false
                val dragDelta = destinationSearchResultsSheet.translationY - destinationSearchResultsDragStartTranslationY
                if (abs(dragDelta) < 24f) {
                    settleDestinationSearchResultsPanel()
                    return true
                }

                if (dragDelta < 0f) {
                    when (destinationSearchResultsSheetState) {
                        DestinationResultsSheetState.MINIMIZED -> collapseDestinationSearchResultsPanel(animated = true)
                        DestinationResultsSheetState.COLLAPSED -> expandDestinationSearchResultsPanel()
                        DestinationResultsSheetState.EXPANDED -> expandDestinationSearchResultsPanel()
                        DestinationResultsSheetState.HIDDEN -> Unit
                    }
                } else {
                    when (destinationSearchResultsSheetState) {
                        DestinationResultsSheetState.EXPANDED -> collapseDestinationSearchResultsPanel(animated = true)
                        DestinationResultsSheetState.COLLAPSED -> minimizeDestinationSearchResultsPanel(animated = true)
                        DestinationResultsSheetState.MINIMIZED -> minimizeDestinationSearchResultsPanel(animated = true)
                        DestinationResultsSheetState.HIDDEN -> Unit
                    }
                }
                return true
            }
        }

        return false
    }

    private fun settleDestinationSearchResultsPanel() {
        when (destinationSearchResultsSheetState) {
            DestinationResultsSheetState.EXPANDED -> expandDestinationSearchResultsPanel()
            DestinationResultsSheetState.COLLAPSED -> collapseDestinationSearchResultsPanel(animated = true)
            DestinationResultsSheetState.MINIMIZED -> minimizeDestinationSearchResultsPanel(animated = true)
            DestinationResultsSheetState.HIDDEN -> Unit
        }
    }

    private fun buildFallbackRoutePolyline(origin: LocationPoint, destination: LocationPoint): String {
        return listOf(
            origin.latitude to origin.longitude,
            destination.latitude to destination.longitude
        ).joinToString(";") { (latitude, longitude) ->
            "$latitude,$longitude"
        }
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

    companion object {
        private const val TAG = "DestinationActivity"
        private const val REQUEST_LOCATION_PERMISSION = 2002
        private const val AUTOCOMPLETE_DEBOUNCE_MILLIS = 450L
        private const val SEARCH_OVERLAY_ELEVATION = 48f
        private const val SETTINGS_OVERLAY_ELEVATION = 64f
        private const val EXIT_BACK_PRESS_WINDOW_MS = 2000L
    }
}

private enum class DestinationResultsSheetState {
    HIDDEN,
    MINIMIZED,
    COLLAPSED,
    EXPANDED
}

