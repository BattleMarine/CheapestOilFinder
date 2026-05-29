package com.example.cheapestoilfinder.station

import com.example.cheapestoilfinder.station.api.ApiCallback
import com.example.cheapestoilfinder.station.dto.NearbyStationSearchRequest
import com.example.cheapestoilfinder.station.dto.RouteStationSearchRequest
import com.example.cheapestoilfinder.station.dto.StationDetailResponse
import com.example.cheapestoilfinder.station.dto.StationSearchResponse

interface StationRepository {
    fun searchNearbyStations(request: NearbyStationSearchRequest, callback: ApiCallback<StationSearchResponse>)
    fun searchRouteStations(request: RouteStationSearchRequest, callback: ApiCallback<StationSearchResponse>)
    fun getStationDetail(stationId: String, callback: ApiCallback<StationDetailResponse>)
}
