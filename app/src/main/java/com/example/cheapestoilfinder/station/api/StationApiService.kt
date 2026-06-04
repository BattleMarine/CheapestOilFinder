package com.example.cheapestoilfinder.station.api

import com.example.cheapestoilfinder.station.dto.RouteStationSearchRequest
import com.example.cheapestoilfinder.station.dto.StationDetailResponse
import com.example.cheapestoilfinder.station.dto.StationSearchResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface StationApiService {
    @GET("api/stations/nearby")
    fun searchNearbyStations(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusMeters") radiusMeters: Int,
        @Query("fuelAmountLiters") fuelAmountLiters: Double,
        @Query("fuelEfficiencyKmPerLiter") fuelEfficiencyKmPerLiter: Double,
        @Query("fuelTypes") fuelTypes: MutableList<FuelType>,
        @Query("sortOrder") sortOrder: StationSearchSortOrder,
        @Query("referenceLabel") referenceLabel: String
    ): Call<StationSearchResponse>

    @POST("api/stations/route")
    fun searchRouteStations(@Body request: RouteStationSearchRequest): Call<StationSearchResponse>

    @GET("api/stations/{stationId}")
    fun getStationDetail(@Path("stationId") stationId: String): Call<StationDetailResponse>
}
