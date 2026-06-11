package com.example.cheapestoilfinder.entry

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.map.MapScreenMode
import com.example.cheapestoilfinder.map.RouteCameraPlacement
import com.example.cheapestoilfinder.map.StationMarkerHighlight
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.LocationPoint
import com.example.cheapestoilfinder.map.model.RouteInfo
import com.example.cheapestoilfinder.map.model.StationCostSummary
import com.example.cheapestoilfinder.settings.CostCalculator
import com.example.cheapestoilfinder.settings.UserFuelType
import com.example.cheapestoilfinder.station.BrandLogoResolver
import com.example.cheapestoilfinder.station.StationDisplayMapper
import com.example.cheapestoilfinder.station.api.ApiCallback
import com.example.cheapestoilfinder.station.api.StationSearchSortOrder
import com.example.cheapestoilfinder.station.dto.NearbyStationSearchRequest
import com.example.cheapestoilfinder.station.dto.RouteResultMode
import com.example.cheapestoilfinder.station.dto.RouteStationSearchRequest
import com.example.cheapestoilfinder.station.dto.StationDetailResponse
import com.example.cheapestoilfinder.station.dto.StationSearchResponse
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class IntegratedMapActivity : DestinationActivity() {
    override val mapScreenMode: MapScreenMode = MapScreenMode.CURRENT_LOCATION

    private var nearbyRequestVersion = 0
    private var nearbyRouteRequestVersion = 0
    private var latestNearbyStations: List<GasStation> = emptyList()
    private var destinationModeActive = false
    private lateinit var nearbyStationContainer: View
    private lateinit var nearbyStationSheet: View
    private lateinit var nearbyStationHandleTouchArea: View
    private lateinit var nearbyStationHandle: View
    private lateinit var nearbyStationRecycler: RecyclerView
    private lateinit var nearbyStationEmptyView: TextView
    private lateinit var nearbyStationCountView: TextView
    private lateinit var nearbyStationFuelTypeSpinner: Spinner
    private lateinit var nearbyStationRadiusSpinner: Spinner
    private lateinit var nearbyStationAdapter: StationListAdapter
    private var nearbyStationSheetState = IntegratedNearbySheetState.HIDDEN
    private var nearbyStationSheetHeightPx = 0
    private var nearbyStationCollapsedTranslationY = 0f
    private var nearbyStationMinimizedTranslationY = 0f
    private var nearbyStationDragging = false
    private var nearbyStationDragStartY = 0f
    private var nearbyStationDragStartTranslationY = 0f
    private var rawNearbyStations: List<GasStation> = emptyList()
    private var temporaryNearbyFuelType: UserFuelType? = null
    private var selectedNearbyRadiusMeters: Int = DEFAULT_NEARBY_RADIUS_METERS
    private lateinit var destinationSearchBar: View
    private lateinit var stationInfoContainer: View
    private lateinit var stationInfoSheet: View
    private lateinit var stationInfoHeader: View
    private lateinit var stationInfoScrim: View
    private lateinit var stationInfoTitleView: TextView
    private lateinit var stationInfoSubtitleView: TextView
    private lateinit var stationInfoBrandLogoView: ImageView
    private lateinit var stationInfoNameValueView: TextView
    private lateinit var stationInfoAddressValueView: TextView
    private lateinit var stationInfoPhoneValueView: TextView
    private lateinit var stationInfoFuelContainer: LinearLayout
    private lateinit var stationInfoRouteDistanceValueView: TextView
    private lateinit var stationInfoCostContainer: LinearLayout
    private lateinit var stationInfoOpenMapButton: Button
    private lateinit var stationInfoCallButton: Button
    private var stationInfoSheetState = IntegratedStationInfoSheetState.HIDDEN
    private var stationInfoSheetHeightPx = 0
    private var stationInfoCollapsedTranslationY = 0f
    private var stationInfoMinimizedTranslationY = 0f
    private var stationInfoDragging = false
    private var stationInfoDragStartY = 0f
    private var stationInfoDragStartTranslationY = 0f
    private var selectedNearbyStation: GasStation? = null
    private var stationInfoSelectionMode = IntegratedStationInfoSelectionMode.NONE
    private var stationDetailRequestVersion = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destinationSearchBar = findViewById(R.id.destination_search_bar)
        initializeNearbyStationPanel()
        initializeStationInfoPanel()
        Log.i(TAG, "Integrated map screen started.")
    }

    override fun updateDestinationMapToCurrentLocation(point: LocationPoint) {
        super.updateDestinationMapToCurrentLocation(point)
        if (!destinationModeActive) {
            loadNearbyStations(point)
        }
    }

    override fun openDestinationSearchOverlay() {
        enterDestinationSearchMode()
        super.openDestinationSearchOverlay()
    }

    override fun onDestinationSearchOverlayClosed() {
        restoreNearbyModeAfterDestinationSearchCancelled()
    }

    override fun onDestinationSearchResultsHidden() {
        restoreNearbyModeAfterDestinationSearchCancelled()
    }

    override fun showDetourRouteForStation(station: GasStation) {
        if (!destinationModeActive) {
            handleNearbyStationSelected(station)
            return
        }

        minimizeRouteRecommendationPanel(animated = true)
        showStationInfo(station, IntegratedStationInfoSelectionMode.ROUTE_RECOMMENDATION)
        requestStationDetail(station)
        mapController?.highlightSelectedStation(station.id, StationMarkerHighlight.ROUTE_RECOMMENDATION)
        if (!station.detourRoute?.routePolyline.isNullOrBlank()) {
            super.showDetourRouteForStation(station)
            return
        }

        drawRouteToNearbyStation(station)
    }

    private fun initializeNearbyStationPanel() {
        nearbyStationContainer = findViewById(R.id.integrated_nearby_station_container)
        nearbyStationSheet = findViewById(R.id.integrated_nearby_station_sheet)
        nearbyStationHandleTouchArea = findViewById(R.id.integrated_nearby_station_drag_handle_touch_area)
        nearbyStationHandle = findViewById(R.id.integrated_nearby_station_drag_handle)
        nearbyStationRecycler = findViewById(R.id.recycler_integrated_nearby_stations)
        nearbyStationEmptyView = findViewById(R.id.integrated_nearby_station_empty)
        nearbyStationCountView = findViewById(R.id.integrated_nearby_station_count)
        nearbyStationFuelTypeSpinner = findViewById(R.id.spinner_integrated_nearby_fuel_type)
        nearbyStationRadiusSpinner = findViewById(R.id.spinner_integrated_nearby_radius)
        nearbyStationAdapter = StationListAdapter { station ->
            handleNearbyStationSelected(station)
        }
        nearbyStationRecycler.layoutManager = LinearLayoutManager(this)
        nearbyStationRecycler.adapter = nearbyStationAdapter
        configureNearbyStationFilters()
        configureNearbyStationPanelGestures()
        nearbyStationContainer.post {
            measureNearbyStationPanel()
        }
    }

    private fun configureNearbyStationFilters() {
        val settings = userPreferenceManager.loadSettings()
        val fuelTypeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            UserFuelType.spinnerLabels(this)
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        nearbyStationFuelTypeSpinner.adapter = fuelTypeAdapter
        nearbyStationFuelTypeSpinner.setSelection(
            UserFuelType.spinnerIndexFor(temporaryNearbyFuelType ?: settings.fuelType),
            false
        )
        nearbyStationFuelTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedFuelType = UserFuelType.fromSpinnerIndex(position)
                if (temporaryNearbyFuelType == selectedFuelType) {
                    return
                }

                temporaryNearbyFuelType = selectedFuelType
                Log.i(TAG, "Integrated nearby fuel type changed temporarily to $selectedFuelType")
                refreshNearbyStationsForCurrentFilters("temporary fuel type changed")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val radiusAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            SEARCH_RADIUS_OPTIONS.map { getString(it.labelResId) }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        nearbyStationRadiusSpinner.adapter = radiusAdapter
        nearbyStationRadiusSpinner.setSelection(
            SEARCH_RADIUS_OPTIONS.indexOfFirst { it.radiusMeters == selectedNearbyRadiusMeters }
                .takeIf { it >= 0 } ?: DEFAULT_NEARBY_RADIUS_INDEX,
            false
        )
        nearbyStationRadiusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val option = SEARCH_RADIUS_OPTIONS.getOrNull(position) ?: return
                if (selectedNearbyRadiusMeters == option.radiusMeters) {
                    return
                }

                selectedNearbyRadiusMeters = option.radiusMeters
                Log.i(TAG, "Integrated nearby radius changed to ${option.radiusMeters}m")
                clearNearbyRouteForRefresh("radius changed")
                currentGpsPoint?.let { point ->
                    mapController?.setCurrentLocationRadiusMeters(selectedNearbyRadiusMeters)
                    loadNearbyStations(point)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun configureNearbyStationPanelGestures() {
        nearbyStationHandleTouchArea.setOnClickListener {
            when (nearbyStationSheetState) {
                IntegratedNearbySheetState.MINIMIZED -> collapseNearbyStationPanel(animated = true)
                IntegratedNearbySheetState.COLLAPSED -> expandNearbyStationPanel()
                IntegratedNearbySheetState.EXPANDED -> collapseNearbyStationPanel(animated = true)
                IntegratedNearbySheetState.HIDDEN -> Unit
            }
        }
        nearbyStationHandle.setOnClickListener {
            nearbyStationHandleTouchArea.performClick()
        }
        nearbyStationHandleTouchArea.setOnTouchListener { _, event ->
            handleNearbyStationSheetTouch(event)
        }
    }

    private fun initializeStationInfoPanel() {
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
        stationInfoCostContainer = findViewById(R.id.station_info_cost_container)
        stationInfoCallButton = findViewById(R.id.button_station_info_call)
        stationInfoCallButton.setOnClickListener {
            openSelectedStationPhoneDialer()
        }
        stationInfoOpenMapButton = findViewById(R.id.button_station_info_open_map)
        stationInfoOpenMapButton.setOnClickListener {
            openSelectedStationInExternalMap()
        }

        stationInfoContainer.visibility = View.GONE
        stationInfoContainer.post {
            val targetHeight = resources.displayMetrics.heightPixels
            val minimizedHeight = (resources.displayMetrics.density * 96f).roundToInt()
            stationInfoSheetHeightPx = targetHeight
            stationInfoCollapsedTranslationY = targetHeight / 2f
            stationInfoMinimizedTranslationY = (targetHeight - minimizedHeight)
                .coerceAtLeast(0)
                .toFloat()
            stationInfoSheet.layoutParams = stationInfoSheet.layoutParams.apply {
                height = targetHeight
            }
            stationInfoSheet.requestLayout()
            stationInfoSheet.translationY = targetHeight.toFloat()
        }

        stationInfoHeader.setOnTouchListener { _, event ->
            handleStationInfoSheetTouch(event)
        }
        stationInfoSheet.setOnTouchListener { _, event ->
            handleStationInfoSheetTouch(event)
        }
        stationInfoScrim.visibility = View.GONE
        stationInfoScrim.isClickable = false
        stationInfoScrim.isFocusable = false
    }

    private fun handleNearbyStationSelected(station: GasStation) {
        if (
            nearbyStationSheetState != IntegratedNearbySheetState.HIDDEN &&
            nearbyStationSheetState != IntegratedNearbySheetState.MINIMIZED
        ) {
            minimizeNearbyStationPanel(animated = true)
        }

        val displayStation = latestNearbyStations.firstOrNull { it.id == station.id } ?: station
        if (selectedNearbyStation?.id != displayStation.id) {
            clearSelectedNearbyRouteAndHighlight("another nearby station selected")
        }
        mapController?.focusStation(displayStation.locationPoint, 15)
        mapController?.highlightSelectedStation(displayStation.id, StationMarkerHighlight.NEARBY)
        showStationInfo(displayStation, IntegratedStationInfoSelectionMode.NEARBY)
        requestStationDetail(displayStation)
        drawRouteToNearbyStation(displayStation)
    }

    private fun showStationInfo(
        station: GasStation,
        selectionMode: IntegratedStationInfoSelectionMode
    ) {
        selectedNearbyStation = station
        stationInfoSelectionMode = selectionMode
        renderStationInfo(station)

        if (stationInfoSheetHeightPx <= 0) {
            stationInfoContainer.post { showStationInfo(station, selectionMode) }
            return
        }

        stationInfoContainer.visibility = View.VISIBLE
        stationInfoContainer.bringToFront()
        hideSearchBarForStationInfo()
        stationInfoSheet.animate().cancel()
        stationInfoSheet.translationY = stationInfoSheetHeightPx.toFloat()
        stationInfoSheet.animate()
            .translationY(stationInfoCollapsedTranslationY)
            .setDuration(220L)
            .start()
        stationInfoSheetState = IntegratedStationInfoSheetState.COLLAPSED
    }

    private fun renderStationInfo(station: GasStation) {
        stationInfoTitleView.text = station.name.ifBlank { getString(R.string.station_info_subtitle_placeholder) }
        stationInfoSubtitleView.text = buildStationInfoSubtitle(station)
        stationInfoBrandLogoView.setImageResource(BrandLogoResolver.fullLogoResId(station.brand))
        stationInfoNameValueView.text = station.name.ifBlank { getString(R.string.station_info_subtitle_placeholder) }
        stationInfoAddressValueView.text = station.locationPoint.address.ifBlank {
            getString(R.string.station_info_body_placeholder)
        }
        val displayPhone = normalizeStationPhone(station.phone)
        stationInfoPhoneValueView.text = displayPhone ?: getString(R.string.station_info_phone_placeholder)
        updateStationInfoCallButton(displayPhone)
        stationInfoRouteDistanceValueView.text = formatRouteDistance(station.distanceMeters)
        val hasValidLocation = isValidMapLocation(station.locationPoint)
        stationInfoOpenMapButton.isEnabled = hasValidLocation
        stationInfoOpenMapButton.alpha = if (hasValidLocation) 1f else 0.45f
        renderFuelRows(station)
        renderCostRows(station)
    }

    private fun updateStationInfoCallButton(displayPhone: String?) {
        val hasPhone = !displayPhone.isNullOrBlank()
        stationInfoCallButton.isEnabled = hasPhone
        stationInfoCallButton.alpha = if (hasPhone) 1f else 0.72f
        stationInfoCallButton.setBackgroundResource(
            if (hasPhone) R.drawable.bg_external_map_button else R.drawable.bg_external_action_button_disabled
        )
    }

    private fun openSelectedStationPhoneDialer() {
        val phone = normalizeStationPhone(selectedNearbyStation?.phone)
            ?: return
        val phoneUri = Uri.parse("tel:${Uri.encode(phone)}")
        val intent = Intent(Intent.ACTION_DIAL, phoneUri)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                getString(R.string.station_info_call_no_phone),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun normalizeStationPhone(rawPhone: String?): String? {
        val phone = rawPhone?.trim().orEmpty()
        if (phone.isBlank()) {
            return null
        }

        val compactPhone = phone.replace(" ", "")
        val isPlaceholder = compactPhone == "-" ||
            compactPhone.contains("000-0000") ||
            compactPhone.contains("0000-0000")
        return phone.takeUnless { isPlaceholder }
    }

    private fun openSelectedStationInExternalMap() {
        val station = selectedNearbyStation
        if (station == null || !isValidMapLocation(station.locationPoint)) {
            Toast.makeText(
                this,
                getString(R.string.station_info_open_map_invalid_location),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val point = station.locationPoint
        val label = Uri.encode(station.name.ifBlank { point.name.ifBlank { getString(R.string.station_info_title) } })
        val geoUri = Uri.parse("geo:0,0?q=${point.latitude},${point.longitude}($label)")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                getString(R.string.station_info_open_map_no_app),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun isValidMapLocation(point: LocationPoint): Boolean {
        return point.latitude in -90.0..90.0 &&
            point.longitude in -180.0..180.0 &&
            (point.latitude != 0.0 || point.longitude != 0.0)
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

    private fun renderFuelRows(station: GasStation) {
        stationInfoFuelContainer.removeAllViews()
        val rows = resolveDisplayFuelPrices(station)
        if (rows.isEmpty()) {
            stationInfoFuelContainer.addView(TextView(this).apply {
                text = getString(R.string.station_info_fuel_unavailable)
                setTextColor(0xFF1F1A17.toInt())
                textSize = 14f
            })
            return
        }

        rows.forEach { (label, price) ->
            stationInfoFuelContainer.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                addView(TextView(context).apply {
                    text = label
                    setTextColor(0xFF8A7C71.toInt())
                    textSize = 13f
                })
                addView(TextView(context).apply {
                    text = formatWon(price)
                    setTextColor(0xFF1F1A17.toInt())
                    textSize = 15f
                    setPadding(16, 0, 0, 0)
                })
            })
        }
    }

    private fun renderCostRows(station: GasStation) {
        stationInfoCostContainer.removeAllViews()
        val costSummary = station.costSummary
        val rows = listOf(
            getString(R.string.station_info_selected_fuel_label) to (
                costSummary?.selectedFuelType?.displayLabel(this)
                    ?: getString(R.string.station_info_cost_unavailable)
                ),
            getString(R.string.station_info_selected_fuel_price_label) to (
                costSummary?.selectedFuelPricePerLiter?.takeIf { it > 0 }?.let {
                    "${formatWon(it)}/L"
                } ?: getString(R.string.station_info_cost_unavailable)
                ),
            getString(R.string.station_info_route_distance_label) to formatRouteDistance(station.distanceMeters),
            getString(R.string.station_info_move_cost_label) to formatCostValue(costSummary?.moveCostWon),
            getString(R.string.station_info_refuel_cost_label) to formatCostValue(costSummary?.refuelCostWon),
            getString(R.string.station_info_total_expected_cost_label) to formatCostValue(costSummary?.totalExpectedCostWon)
        )

        rows.forEach { (label, value) ->
            stationInfoCostContainer.addView(buildCostRowView(label, value))
        }
    }

    private fun buildCostRowView(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }

            addView(TextView(context).apply {
                text = label
                setTextColor(0xFF8A7C71.toInt())
                textSize = 12f
            })

            addView(TextView(context).apply {
                text = value
                setTextColor(0xFF1F1A17.toInt())
                textSize = 15f
                setPadding(0, 4, 0, 0)
            })
        }
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
            prices.lpgWon?.takeIf { it > 0 }?.let {
                add(getString(R.string.fuel_type_lpg) to it)
            }
        }
    }

    private fun requestStationDetail(station: GasStation) {
        val stationId = station.id
        if (stationId.isBlank()) {
            return
        }

        val requestVersion = ++stationDetailRequestVersion
        stationRepository.getStationDetail(stationId, object : ApiCallback<StationDetailResponse> {
            override fun onSuccess(result: StationDetailResponse) {
                if (requestVersion != stationDetailRequestVersion || selectedNearbyStation?.id != stationId) {
                    return
                }

                val detailStation = result.station?.let(StationDisplayMapper::toGasStation) ?: return
                val mergedStation = mergeStationInfo(station, detailStation)
                val origin = currentGpsPoint
                val decoratedStation = if (origin != null) {
                    attachCostSummary(mergedStation, origin) ?: mergedStation
                } else {
                    mergedStation
                }
                selectedNearbyStation = decoratedStation
                renderStationInfo(decoratedStation)
            }

            override fun onError(error: Throwable) {
                Log.w(TAG, "Failed to load integrated station detail for $stationId", error)
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
            phone = detail.phone.ifBlank { base.phone },
            costSummary = base.costSummary,
            routeExtraDistanceMeters = base.routeExtraDistanceMeters,
            detourRoute = base.detourRoute
        )
    }

    private fun expandStationInfo() {
        if (stationInfoSheetState == IntegratedStationInfoSheetState.HIDDEN) {
            return
        }

        stationInfoSheet.animate().cancel()
        stationInfoSheetState = IntegratedStationInfoSheetState.EXPANDED
        stationInfoSheet.animate()
            .translationY(0f)
            .setDuration(220L)
            .start()
    }

    private fun collapseStationInfo(animated: Boolean) {
        if (stationInfoSheetState == IntegratedStationInfoSheetState.HIDDEN) {
            return
        }

        stationInfoSheet.animate().cancel()
        stationInfoSheetState = IntegratedStationInfoSheetState.COLLAPSED
        if (animated) {
            stationInfoSheet.animate()
                .translationY(stationInfoCollapsedTranslationY)
                .setDuration(220L)
                .start()
        } else {
            stationInfoSheet.translationY = stationInfoCollapsedTranslationY
        }
    }

    private fun minimizeStationInfo(animated: Boolean) {
        if (stationInfoSheetState == IntegratedStationInfoSheetState.HIDDEN) {
            return
        }

        stationInfoSheet.animate().cancel()
        stationInfoSheetState = IntegratedStationInfoSheetState.MINIMIZED
        stationInfoDragging = false
        if (animated) {
            stationInfoSheet.animate()
                .translationY(stationInfoMinimizedTranslationY)
                .setDuration(220L)
                .start()
        } else {
            stationInfoSheet.translationY = stationInfoMinimizedTranslationY
        }
    }

    private fun hideStationInfo(animated: Boolean) {
        if (stationInfoSheetState == IntegratedStationInfoSheetState.HIDDEN) {
            stationInfoContainer.visibility = View.GONE
            showSearchBarAfterStationInfoHidden(animated = false)
            return
        }

        stationInfoSheet.animate().cancel()
        stationInfoSheetState = IntegratedStationInfoSheetState.HIDDEN
        stationDetailRequestVersion++

        val target = stationInfoSheetHeightPx.toFloat()
        val finish = Runnable {
            stationInfoContainer.visibility = View.GONE
            stationInfoDragging = false
            showSearchBarAfterStationInfoHidden(animated = animated)
            when (stationInfoSelectionMode) {
                IntegratedStationInfoSelectionMode.NEARBY -> {
                    if (selectedNearbyStation != null) {
                        clearSelectedNearbyRouteAndHighlight("station info panel hidden")
                    }
                }
                IntegratedStationInfoSelectionMode.ROUTE_RECOMMENDATION -> {
                    mapController?.clearDetourRoute()
                    mapController?.highlightSelectedStation(null)
                    selectedNearbyStation = null
                    Log.i(TAG, "Cleared integrated route recommendation detail selection.")
                }
                IntegratedStationInfoSelectionMode.NONE -> {
                    selectedNearbyStation = null
                }
            }
            stationInfoSelectionMode = IntegratedStationInfoSelectionMode.NONE
        }
        if (animated) {
            stationInfoSheet.animate()
                .translationY(target)
                .setDuration(220L)
                .withEndAction(finish)
                .start()
        } else {
            stationInfoSheet.translationY = target
            finish.run()
        }
    }

    private fun hideSearchBarForStationInfo() {
        if (!::destinationSearchBar.isInitialized) {
            return
        }

        destinationSearchBar.animate().cancel()
        destinationSearchBar.post {
            val hiddenTranslationY = -(destinationSearchBar.height + 24f * resources.displayMetrics.density)
            destinationSearchBar.animate()
                .translationY(hiddenTranslationY)
                .alpha(0f)
                .setDuration(180L)
                .start()
        }
    }

    private fun showSearchBarAfterStationInfoHidden(animated: Boolean) {
        if (!::destinationSearchBar.isInitialized) {
            return
        }

        destinationSearchBar.animate().cancel()
        destinationSearchBar.visibility = View.VISIBLE
        if (animated) {
            destinationSearchBar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(180L)
                .start()
        } else {
            destinationSearchBar.translationY = 0f
            destinationSearchBar.alpha = 1f
        }
    }

    private fun handleStationInfoSheetTouch(event: MotionEvent): Boolean {
        if (stationInfoSheetHeightPx <= 0 || stationInfoSheetState == IntegratedStationInfoSheetState.HIDDEN) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stationInfoDragging = true
                stationInfoDragStartY = event.rawY
                stationInfoDragStartTranslationY = stationInfoSheet.translationY
                stationInfoSheet.animate().cancel()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!stationInfoDragging) {
                    return false
                }

                val delta = event.rawY - stationInfoDragStartY
                val (minTranslation, maxTranslation) = when (stationInfoSheetState) {
                    IntegratedStationInfoSheetState.EXPANDED -> 0f to stationInfoCollapsedTranslationY
                    IntegratedStationInfoSheetState.COLLAPSED -> 0f to stationInfoMinimizedTranslationY
                    IntegratedStationInfoSheetState.MINIMIZED -> stationInfoCollapsedTranslationY to stationInfoMinimizedTranslationY
                    IntegratedStationInfoSheetState.HIDDEN -> return false
                }
                stationInfoSheet.translationY = (stationInfoDragStartTranslationY + delta)
                    .coerceIn(minTranslation, maxTranslation)
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (!stationInfoDragging) {
                    return false
                }

                stationInfoDragging = false
                val dragDelta = event.rawY - stationInfoDragStartY
                if (kotlin.math.abs(dragDelta) < 24f * resources.displayMetrics.density) {
                    settleStationInfoPanel()
                    return true
                }

                if (dragDelta < 0f) {
                    when (stationInfoSheetState) {
                        IntegratedStationInfoSheetState.MINIMIZED -> collapseStationInfo(animated = true)
                        IntegratedStationInfoSheetState.COLLAPSED -> expandStationInfo()
                        IntegratedStationInfoSheetState.EXPANDED -> expandStationInfo()
                        IntegratedStationInfoSheetState.HIDDEN -> Unit
                    }
                } else {
                    when (stationInfoSheetState) {
                        IntegratedStationInfoSheetState.EXPANDED -> collapseStationInfo(animated = true)
                        IntegratedStationInfoSheetState.COLLAPSED -> minimizeStationInfo(animated = true)
                        IntegratedStationInfoSheetState.MINIMIZED -> minimizeStationInfo(animated = true)
                        IntegratedStationInfoSheetState.HIDDEN -> Unit
                    }
                }
                return true
            }
        }
        return false
    }

    private fun settleStationInfoPanel() {
        when (stationInfoSheetState) {
            IntegratedStationInfoSheetState.EXPANDED -> expandStationInfo()
            IntegratedStationInfoSheetState.COLLAPSED -> collapseStationInfo(animated = true)
            IntegratedStationInfoSheetState.MINIMIZED -> minimizeStationInfo(animated = true)
            IntegratedStationInfoSheetState.HIDDEN -> Unit
        }
    }

    private fun showNearbyStationPanel(stations: List<GasStation>) {
        val previousState = nearbyStationSheetState
        nearbyStationAdapter.submitList(stations)
        nearbyStationCountView.text = getString(R.string.destination_search_results_count_format, stations.size)
        nearbyStationEmptyView.visibility = if (stations.isEmpty()) View.VISIBLE else View.GONE
        nearbyStationRecycler.visibility = if (stations.isEmpty()) View.GONE else View.VISIBLE
        nearbyStationContainer.visibility = View.VISIBLE
        nearbyStationContainer.bringToFront()
        nearbyStationSheet.post {
            measureNearbyStationPanel()
            when (previousState) {
                IntegratedNearbySheetState.HIDDEN -> minimizeNearbyStationPanel(animated = false)
                IntegratedNearbySheetState.MINIMIZED -> minimizeNearbyStationPanel(animated = false)
                IntegratedNearbySheetState.COLLAPSED -> collapseNearbyStationPanel(animated = false)
                IntegratedNearbySheetState.EXPANDED -> expandNearbyStationPanel()
            }
        }
    }

    private fun hideNearbyStationPanel(animated: Boolean) {
        if (nearbyStationSheetState == IntegratedNearbySheetState.HIDDEN) {
            nearbyStationContainer.visibility = View.GONE
            return
        }

        nearbyStationSheet.animate().cancel()
        nearbyStationSheetState = IntegratedNearbySheetState.HIDDEN
        if (nearbyStationSheetHeightPx <= 0) {
            measureNearbyStationPanel()
        }
        val target = nearbyStationSheetHeightPx.toFloat()
        if (animated) {
            nearbyStationSheet.animate()
                .translationY(target)
                .setDuration(180L)
                .withEndAction {
                    nearbyStationContainer.visibility = View.GONE
                    updateDestinationFloatingControlsPosition()
                }
                .start()
        } else {
            nearbyStationSheet.translationY = target
            nearbyStationContainer.visibility = View.GONE
            updateDestinationFloatingControlsPosition()
        }
    }

    private fun expandNearbyStationPanel() {
        if (nearbyStationSheetState == IntegratedNearbySheetState.HIDDEN) {
            return
        }
        nearbyStationSheet.animate().cancel()
        nearbyStationSheetState = IntegratedNearbySheetState.EXPANDED
        nearbyStationSheet.animate()
            .translationY(0f)
            .setDuration(200L)
            .start()
    }

    private fun collapseNearbyStationPanel(animated: Boolean) {
        if (nearbyStationSheetState == IntegratedNearbySheetState.HIDDEN) {
            nearbyStationContainer.visibility = View.VISIBLE
        }
        nearbyStationContainer.visibility = View.VISIBLE
        nearbyStationSheet.animate().cancel()
        nearbyStationSheetState = IntegratedNearbySheetState.COLLAPSED
        updateDestinationFloatingControlsPositionForSheetTop(
            nearbyStationCollapsedTranslationY,
            animated,
            200L
        )
        if (animated) {
            nearbyStationSheet.animate()
                .translationY(nearbyStationCollapsedTranslationY)
                .setDuration(200L)
                .start()
        } else {
            nearbyStationSheet.translationY = nearbyStationCollapsedTranslationY
        }
    }

    private fun minimizeNearbyStationPanel(animated: Boolean) {
        if (nearbyStationSheetState == IntegratedNearbySheetState.HIDDEN) {
            nearbyStationContainer.visibility = View.VISIBLE
        }
        nearbyStationContainer.visibility = View.VISIBLE
        nearbyStationSheet.animate().cancel()
        nearbyStationSheetState = IntegratedNearbySheetState.MINIMIZED
        updateDestinationFloatingControlsPositionForSheetTop(
            nearbyStationMinimizedTranslationY,
            animated,
            180L
        )
        if (animated) {
            nearbyStationSheet.animate()
                .translationY(nearbyStationMinimizedTranslationY)
                .setDuration(180L)
                .start()
        } else {
            nearbyStationSheet.translationY = nearbyStationMinimizedTranslationY
        }
    }

    private fun measureNearbyStationPanel() {
        val targetHeight = nearbyStationSheet.height.takeIf { it > 0 }
            ?: nearbyStationContainer.height
        if (targetHeight <= 0) {
            return
        }

        nearbyStationSheetHeightPx = targetHeight
        nearbyStationCollapsedTranslationY = targetHeight * 0.5f
        val peekHeightPx = 104f * resources.displayMetrics.density
        nearbyStationMinimizedTranslationY = (targetHeight - peekHeightPx)
            .coerceAtLeast(nearbyStationCollapsedTranslationY)
        if (nearbyStationSheetState == IntegratedNearbySheetState.HIDDEN) {
            nearbyStationSheet.translationY = targetHeight.toFloat()
        }
    }

    private fun handleNearbyStationSheetTouch(event: MotionEvent): Boolean {
        if (nearbyStationSheetState == IntegratedNearbySheetState.HIDDEN) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                nearbyStationDragging = true
                nearbyStationDragStartY = event.rawY
                nearbyStationDragStartTranslationY = nearbyStationSheet.translationY
                nearbyStationSheet.animate().cancel()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!nearbyStationDragging) {
                    return false
                }

                val delta = event.rawY - nearbyStationDragStartY
                if (nearbyStationSheetState == IntegratedNearbySheetState.EXPANDED && delta < 0f) {
                    return true
                }
                val (minTranslation, maxTranslation) = when (nearbyStationSheetState) {
                    IntegratedNearbySheetState.EXPANDED -> 0f to nearbyStationCollapsedTranslationY
                    IntegratedNearbySheetState.COLLAPSED -> 0f to nearbyStationMinimizedTranslationY
                    IntegratedNearbySheetState.MINIMIZED -> nearbyStationCollapsedTranslationY to nearbyStationMinimizedTranslationY
                    IntegratedNearbySheetState.HIDDEN -> return false
                }
                val nextTranslation = (nearbyStationDragStartTranslationY + delta)
                    .coerceIn(minTranslation, maxTranslation)
                nearbyStationSheet.translationY = nextTranslation
                if (nextTranslation >= nearbyStationCollapsedTranslationY) {
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
                if (!nearbyStationDragging) {
                    return false
                }
                nearbyStationDragging = false
                val dragDelta = event.rawY - nearbyStationDragStartY
                if (kotlin.math.abs(dragDelta) < 24f * resources.displayMetrics.density) {
                    settleNearbyStationPanel()
                    return true
                }

                if (dragDelta < 0f) {
                    when (nearbyStationSheetState) {
                        IntegratedNearbySheetState.MINIMIZED -> collapseNearbyStationPanel(animated = true)
                        IntegratedNearbySheetState.COLLAPSED -> expandNearbyStationPanel()
                        IntegratedNearbySheetState.EXPANDED -> expandNearbyStationPanel()
                        IntegratedNearbySheetState.HIDDEN -> Unit
                    }
                } else {
                    when (nearbyStationSheetState) {
                        IntegratedNearbySheetState.EXPANDED -> collapseNearbyStationPanel(animated = true)
                        IntegratedNearbySheetState.COLLAPSED -> minimizeNearbyStationPanel(animated = true)
                        IntegratedNearbySheetState.MINIMIZED -> minimizeNearbyStationPanel(animated = true)
                        IntegratedNearbySheetState.HIDDEN -> Unit
                    }
                }
                return true
            }
        }
        return false
    }

    private fun settleNearbyStationPanel() {
        when (nearbyStationSheetState) {
            IntegratedNearbySheetState.EXPANDED -> expandNearbyStationPanel()
            IntegratedNearbySheetState.COLLAPSED -> collapseNearbyStationPanel(animated = true)
            IntegratedNearbySheetState.MINIMIZED -> minimizeNearbyStationPanel(animated = true)
            IntegratedNearbySheetState.HIDDEN -> Unit
        }
    }

    private fun enterDestinationSearchMode() {
        destinationModeActive = true
        nearbyRequestVersion++
        latestNearbyStations = emptyList()
        hideStationInfo(animated = false)
        hideNearbyStationPanel(animated = true)
        mapController?.clearStations()
        mapController?.highlightSelectedStation(null)
        mapController?.clearCurrentLocationRadius()
        mapController?.clearRoute()
    }

    private fun restoreNearbyModeAfterDestinationSearchCancelled() {
        if (!destinationModeActive) {
            return
        }

        destinationModeActive = false
        mapController?.clearRoute()
        val point = currentGpsPoint
        if (point == null) {
            Log.i(TAG, "Restoring integrated nearby mode after search close: GPS point is missing.")
            refreshCurrentLocationFromGps()
            return
        }

        Log.i(
            TAG,
            "Restoring integrated nearby mode after search close: lat=${point.latitude}, lon=${point.longitude}, radius=$selectedNearbyRadiusMeters"
        )
        mapController?.setCurrentLocationRadiusMeters(selectedNearbyRadiusMeters)
        loadNearbyStations(point)
    }

    private fun loadNearbyStations(point: LocationPoint) {
        if (destinationModeActive) {
            return
        }

        val settings = userPreferenceManager.loadSettings()
        mapController?.setCurrentLocationRadiusMeters(selectedNearbyRadiusMeters)
        val requestVersion = ++nearbyRequestVersion
        val request = NearbyStationSearchRequest(
            latitude = point.latitude,
            longitude = point.longitude,
            radiusMeters = selectedNearbyRadiusMeters,
            fuelAmountLiters = settings.refuelAmountLiter,
            fuelEfficiencyKmPerLiter = settings.fuelEfficiencyKmPerLiter,
            fuelTypes = UserFuelType.values().map { it.backendFuelType },
            referenceLabel = point.name
        )

        Log.i(
            TAG,
            "Integrated nearby request: lat=${point.latitude}, lon=${point.longitude}, radius=$selectedNearbyRadiusMeters"
        )
        stationRepository.searchNearbyStations(request, object : ApiCallback<StationSearchResponse> {
            override fun onSuccess(result: StationSearchResponse) {
                if (requestVersion != nearbyRequestVersion) {
                    return
                }

                rawNearbyStations = StationDisplayMapper.toGasStations(result)
                    .filterStationsInsideRadius(point, selectedNearbyRadiusMeters)
                val stations = prepareNearbyStationsForDisplay(rawNearbyStations, point)

                latestNearbyStations = stations
                mapController?.showStations(stations)
                showNearbyStationPanel(stations)
                Log.i(
                    TAG,
                    "Integrated nearby loaded: raw=${result.stations.size}, visible=${stations.size}"
                )
            }

            override fun onError(error: Throwable) {
                if (requestVersion != nearbyRequestVersion) {
                    return
                }

                latestNearbyStations = emptyList()
                rawNearbyStations = emptyList()
                mapController?.showStations(emptyList())
                showNearbyStationPanel(emptyList())
                Log.w(TAG, "Integrated nearby request failed.", error)
                Toast.makeText(
                    this@IntegratedMapActivity,
                    "주변 주유소를 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun drawRouteToNearbyStation(station: GasStation) {
        val origin = currentGpsPoint
        if (origin == null) {
            Toast.makeText(this, "현재 위치를 먼저 확인해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val requestVersion = ++nearbyRouteRequestVersion
        val settings = userPreferenceManager.loadSettings()
        val destination = station.locationPoint
        val request = RouteStationSearchRequest(
            originLatitude = origin.latitude,
            originLongitude = origin.longitude,
            destinationLatitude = destination.latitude,
            destinationLongitude = destination.longitude,
            routePolyline = null,
            radiusKm = selectedNearbyRadiusMeters / 1000.0,
            fuelAmountLiters = settings.refuelAmountLiter,
            fuelEfficiencyKmPerLiter = settings.fuelEfficiencyKmPerLiter,
            fuelTypes = UserFuelType.values().map { it.backendFuelType },
            sortOrder = StationSearchSortOrder.DISTANCE_ASC,
            routeResultMode = RouteResultMode.ROUTE_ONLY,
            originLabel = origin.name,
            destinationLabel = station.name
        )

        stationRepository.searchRouteStations(request, object : ApiCallback<StationSearchResponse> {
            override fun onSuccess(result: StationSearchResponse) {
                if (requestVersion != nearbyRouteRequestVersion) {
                    return
                }

                val route = result.route
                val routeInfo = RouteInfo(
                    origin = origin,
                    destination = destination,
                    distanceMeters = route?.distanceMeters ?: station.distanceMeters,
                    durationSeconds = route?.durationSeconds ?: 0,
                    tollFeeWon = route?.tollFeeWon ?: 0,
                    polyline = route?.routePolyline?.takeIf { it.isNotBlank() }
                        ?: buildFallbackRoutePolyline(origin, destination)
                )
                if (selectedNearbyStation?.id == station.id) {
                    val stationWithRouteDistance = station.copy(distanceMeters = routeInfo.distanceMeters)
                    val decoratedStation = attachCostSummary(stationWithRouteDistance, origin)
                        ?: stationWithRouteDistance
                    selectedNearbyStation = decoratedStation
                    renderStationInfo(decoratedStation)
                }
                mapController?.showRoute(routeInfo, RouteCameraPlacement.ABOVE_BOTTOM_SHEET)
                Toast.makeText(
                    this@IntegratedMapActivity,
                    "${station.name}까지 경로를 표시했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onError(error: Throwable) {
                if (requestVersion != nearbyRouteRequestVersion) {
                    return
                }

                Log.w(TAG, "Integrated nearby station route failed: ${station.id}", error)
                Toast.makeText(
                    this@IntegratedMapActivity,
                    "선택한 주유소까지의 경로를 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    override fun onBackPressed() {
        if (stationInfoSheetState != IntegratedStationInfoSheetState.HIDDEN) {
            resetExitBackConfirmation()
            when (stationInfoSheetState) {
                IntegratedStationInfoSheetState.EXPANDED -> collapseStationInfo(animated = true)
                IntegratedStationInfoSheetState.COLLAPSED -> minimizeStationInfo(animated = true)
                IntegratedStationInfoSheetState.MINIMIZED -> hideStationInfo(animated = true)
                IntegratedStationInfoSheetState.HIDDEN -> Unit
            }
            return
        }

        when (nearbyStationSheetState) {
            IntegratedNearbySheetState.EXPANDED -> {
                resetExitBackConfirmation()
                collapseNearbyStationPanel(animated = true)
                return
            }
            IntegratedNearbySheetState.COLLAPSED -> {
                resetExitBackConfirmation()
                minimizeNearbyStationPanel(animated = true)
                return
            }
            IntegratedNearbySheetState.MINIMIZED,
            IntegratedNearbySheetState.HIDDEN -> Unit
        }

        super.onBackPressed()
    }

    private fun formatRouteDistance(distanceMeters: Int): String {
        if (distanceMeters <= 0) {
            return getString(R.string.station_info_distance_unavailable)
        }

        return if (distanceMeters < 1000) {
            getString(R.string.distance_format_meter, distanceMeters)
        } else {
            getString(R.string.distance_format_kilometer, distanceMeters / 1000.0)
        }
    }

    private fun formatWon(amount: Int): String {
        return String.format(Locale.KOREA, "%,d원", amount)
    }

    private fun formatCostValue(cost: Int?): String {
        return cost?.takeIf { it >= 0 }?.let { "약 ${formatWon(it)}" }
            ?: getString(R.string.station_info_cost_unavailable)
    }

    private fun attachCostSummary(station: GasStation, origin: LocationPoint): GasStation? {
        val settings = userPreferenceManager.loadSettings()
        val selectedFuelType = resolveActiveNearbyFuelType()
        val selectedFuelPrice = resolveSelectedFuelPrice(station, selectedFuelType) ?: return null
        val distanceMeters = station.distanceMeters.takeIf { it > 0 }
            ?: distanceMeters(origin, station.locationPoint).roundToInt()
        val moveCost = CostCalculator.calculateMoveCost(
            distanceMeters,
            settings.fuelEfficiencyKmPerLiter,
            selectedFuelPrice
        )
        val refuelCost = CostCalculator.calculateRefuelCost(
            settings.refuelAmountLiter,
            selectedFuelPrice
        )
        val totalCost = CostCalculator.calculateTotalExpectedCost(moveCost, refuelCost)

        return station.copy(
            costSummary = StationCostSummary(
                selectedFuelType = selectedFuelType,
                selectedFuelPricePerLiter = selectedFuelPrice,
                distanceMeters = distanceMeters,
                distanceKm = distanceMeters / 1000.0,
                moveCostWon = moveCost,
                refuelCostWon = refuelCost,
                totalExpectedCostWon = totalCost,
                unavailableReason = null
            )
        )
    }

    private fun refreshNearbyStationsForCurrentFilters(reason: String) {
        if (rawNearbyStations.isEmpty()) {
            return
        }

        clearNearbyRouteForRefresh(reason)
        val origin = currentGpsPoint ?: return
        val stations = prepareNearbyStationsForDisplay(rawNearbyStations, origin)
        latestNearbyStations = stations
        nearbyStationAdapter.submitList(stations)
        nearbyStationCountView.text = getString(R.string.destination_search_results_count_format, stations.size)
        nearbyStationEmptyView.visibility = if (stations.isEmpty()) View.VISIBLE else View.GONE
        nearbyStationRecycler.visibility = if (stations.isEmpty()) View.GONE else View.VISIBLE
        mapController?.showStations(stations)
    }

    private fun prepareNearbyStationsForDisplay(
        stations: List<GasStation>,
        origin: LocationPoint
    ): List<GasStation> {
        return stations
            .mapNotNull { attachCostSummary(it, origin) }
            .sortedWith(
                compareBy<GasStation> {
                    it.costSummary?.totalExpectedCostWon ?: Int.MAX_VALUE
                }.thenBy { it.distanceMeters }
                    .thenBy { it.name }
            )
    }

    private fun List<GasStation>.filterStationsInsideRadius(
        center: LocationPoint,
        radiusMeters: Int
    ): List<GasStation> {
        return asSequence()
            .filter { isValidWgs84Coordinate(it.locationPoint) }
            .filter { distanceMeters(center, it.locationPoint) <= radiusMeters }
            .toList()
    }

    private fun clearNearbyRouteForRefresh(reason: String) {
        selectedNearbyStation = null
        clearSelectedNearbyRouteAndHighlight(reason)
    }

    private fun clearSelectedNearbyRouteAndHighlight(reason: String) {
        nearbyRouteRequestVersion++
        mapController?.clearRoute()
        mapController?.highlightSelectedStation(null)
        selectedNearbyStation = null
        Log.i(TAG, "Cleared integrated nearby route and highlight: $reason")
    }

    private fun resolveActiveNearbyFuelType(): UserFuelType {
        return temporaryNearbyFuelType ?: userPreferenceManager.loadSettings().fuelType
    }

    private fun resolveSelectedFuelPrice(station: GasStation, fuelType: UserFuelType): Int? {
        val prices = station.fuelPrices ?: return null
        return when (fuelType) {
            UserFuelType.GAS_HIGH -> prices.premiumGasolineWon
            UserFuelType.GAS_LOW -> prices.regularGasolineWon
            UserFuelType.DIESEL -> prices.dieselWon
            UserFuelType.LPG -> prices.lpgWon
        }?.takeIf { it > 0 }
    }

    private fun buildFallbackRoutePolyline(origin: LocationPoint, destination: LocationPoint): String {
        return listOf(
            origin.latitude to origin.longitude,
            destination.latitude to destination.longitude
        ).joinToString(";") { (latitude, longitude) ->
            "$latitude,$longitude"
        }
    }

    private fun isValidWgs84Coordinate(point: LocationPoint): Boolean {
        return point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
    }

    private fun distanceMeters(from: LocationPoint, to: LocationPoint): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val fromLat = Math.toRadians(from.latitude)
        val toLat = Math.toRadians(to.latitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(fromLat) * cos(toLat) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    companion object {
        private const val TAG = "IntegratedMapActivity"
        private const val DEFAULT_NEARBY_RADIUS_METERS = 5000
        private const val DEFAULT_NEARBY_RADIUS_INDEX = 1
        private val SEARCH_RADIUS_OPTIONS = listOf(
            IntegratedSearchRadiusOption(R.string.station_list_radius_3km, 3000),
            IntegratedSearchRadiusOption(R.string.station_list_radius_5km, 5000),
            IntegratedSearchRadiusOption(R.string.station_list_radius_10km, 10000)
        )
    }
}

private data class IntegratedSearchRadiusOption(
    val labelResId: Int,
    val radiusMeters: Int
)

private enum class IntegratedNearbySheetState {
    HIDDEN,
    MINIMIZED,
    COLLAPSED,
    EXPANDED
}

private enum class IntegratedStationInfoSheetState {
    HIDDEN,
    MINIMIZED,
    COLLAPSED,
    EXPANDED
}

private enum class IntegratedStationInfoSelectionMode {
    NONE,
    NEARBY,
    ROUTE_RECOMMENDATION
}
