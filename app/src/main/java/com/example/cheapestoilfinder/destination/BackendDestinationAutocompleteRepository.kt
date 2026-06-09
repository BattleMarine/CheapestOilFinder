package com.example.cheapestoilfinder.destination

import android.util.Log
import com.example.cheapestoilfinder.destination.api.DestinationApiClient
import com.example.cheapestoilfinder.destination.api.DestinationAutocompleteResponse
import com.example.cheapestoilfinder.station.api.ApiCallback
import com.example.cheapestoilfinder.station.api.BackendApiConfig
import java.io.IOException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class BackendDestinationAutocompleteRepository private constructor(
    private val api: com.example.cheapestoilfinder.destination.api.DestinationAutocompleteApiService,
    private val fallbackRepository: DestinationAutocompleteRepository
) : DestinationAutocompleteRepository {

    override fun search(
        query: String,
        callback: ApiCallback<List<DestinationSearchSuggestion>>
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) {
            callback.onSuccess(emptyList())
            return
        }

        val call = api.autocomplete(trimmedQuery)
        Log.d(TAG, "autocomplete request url=${call.request().url}")
        call.enqueue(object : Callback<DestinationAutocompleteResponse> {
            override fun onResponse(
                call: Call<DestinationAutocompleteResponse>,
                response: Response<DestinationAutocompleteResponse>
            ) {
                if (!response.isSuccessful) {
                    val errorBody = readErrorBody(response)
                    Log.w(TAG, "Autocomplete API error ${response.code()} for ${call.request().url}: $errorBody")
                    fallbackWithLocal(trimmedQuery, callback)
                    return
                }

                val body = response.body()
                if (body == null) {
                    Log.w(TAG, "Autocomplete API returned empty body for ${call.request().url}")
                    fallbackWithLocal(trimmedQuery, callback)
                    return
                }

                val mapped = body.items
                    .map {
                        DestinationSearchSuggestion(
                            displayText = it.displayText.orEmpty().ifBlank { it.primaryText.orEmpty() },
                            description = it.secondaryText.orEmpty().ifBlank { it.sourceType.orEmpty() },
                            latitude = it.latitude,
                            longitude = it.longitude,
                            sourceRef = it.sourceRef
                        )
                    }
                    .filter { it.displayText.isNotBlank() }
                    .take(4)

                if (mapped.isEmpty()) {
                    fallbackWithLocal(trimmedQuery, callback)
                    return
                }

                callback.onSuccess(mapped)
            }

            override fun onFailure(call: Call<DestinationAutocompleteResponse>, t: Throwable) {
                Log.w(TAG, "Autocomplete API failed for ${call.request().url}", t)
                fallbackWithLocal(trimmedQuery, callback)
            }
        })
    }

    private fun fallbackWithLocal(
        query: String,
        callback: ApiCallback<List<DestinationSearchSuggestion>>
    ) {
        fallbackRepository.search(query, callback)
    }

    private fun readErrorBody(response: Response<*>): String? {
        return try {
            response.errorBody()?.string()
        } catch (exception: IOException) {
            "Failed to read error body: ${exception.message}"
        }
    }

    companion object {
        private const val TAG = "DestinationAutocomplete"

        fun create(baseUrl: String): BackendDestinationAutocompleteRepository {
            return BackendDestinationAutocompleteRepository(
                DestinationApiClient.create(baseUrl),
                LocalDestinationAutocompleteRepository()
            )
        }

        fun createDefault(): BackendDestinationAutocompleteRepository {
            return create(BackendApiConfig.DEFAULT_BASE_URL)
        }
    }
}
