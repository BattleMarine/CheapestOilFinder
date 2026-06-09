package com.example.cheapestoilfinder.station

import android.content.Context
import com.example.cheapestoilfinder.station.api.ApiCallback
import com.example.cheapestoilfinder.station.api.BackendApiClient
import com.example.cheapestoilfinder.station.api.BackendApiConfig
import com.example.cheapestoilfinder.station.api.BackendApiException
import com.example.cheapestoilfinder.station.api.StationApiService
import com.example.cheapestoilfinder.station.dto.NearbyStationSearchRequest
import com.example.cheapestoilfinder.station.dto.RouteStationSearchRequest
import com.example.cheapestoilfinder.station.dto.StationDetailResponse
import com.example.cheapestoilfinder.station.dto.StationSearchResponse
import java.io.IOException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class BackendStationRepository private constructor(
    private val apiService: StationApiService
) : StationRepository {

    override fun searchNearbyStations(
        request: NearbyStationSearchRequest,
        callback: ApiCallback<StationSearchResponse>
    ) {
        val call = apiService.searchNearbyStations(
            request.latitude,
            request.longitude,
            request.radiusMeters,
            request.fuelAmountLiters,
            request.fuelEfficiencyKmPerLiter,
            request.fuelTypes.toMutableList(),
            request.sortOrder,
            request.referenceLabel.orEmpty()
        )
        android.util.Log.d(
            TAG,
            "searchNearbyStations request url=${call.request().url}"
        )
        enqueue(call, callback)
    }

    override fun searchRouteStations(
        request: RouteStationSearchRequest,
        callback: ApiCallback<StationSearchResponse>
    ) {
        enqueue(apiService.searchRouteStations(request), callback)
    }

    override fun getStationDetail(stationId: String, callback: ApiCallback<StationDetailResponse>) {
        enqueue(apiService.getStationDetail(stationId), callback)
    }

    private fun <T> enqueue(call: Call<T>, callback: ApiCallback<T>) {
        call.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null) {
                        callback.onError(BackendApiException(response.code(), "Response body was empty"))
                        return
                    }
                    callback.onSuccess(body)
                    return
                }

                val errorBody = readErrorBody(response)
                android.util.Log.w(
                    TAG,
                    "Backend API error ${response.code()} for ${call.request().url}: $errorBody"
                )
                callback.onError(BackendApiException(response.code(), errorBody))
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                callback.onError(t)
            }
        })
    }

    private fun readErrorBody(response: Response<*>): String? {
        return try {
            response.errorBody()?.string()
        } catch (exception: IOException) {
            "Failed to read error body: ${exception.message}"
        }
    }

    companion object {
        private const val TAG = "BackendStationRepository"
        fun create(baseUrl: String): BackendStationRepository {
            return BackendStationRepository(BackendApiClient.create(baseUrl))
        }

        fun createDefault(): BackendStationRepository {
            return create(BackendApiConfig.DEFAULT_BASE_URL)
        }

        fun createDefault(context: Context): BackendStationRepository {
            return create(BackendApiConfig.defaultBaseUrl(context))
        }
    }
}
