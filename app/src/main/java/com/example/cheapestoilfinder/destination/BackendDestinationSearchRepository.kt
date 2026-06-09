package com.example.cheapestoilfinder.destination

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.example.cheapestoilfinder.destination.api.DestinationApiClient
import com.example.cheapestoilfinder.destination.api.DestinationSearchRequest
import com.example.cheapestoilfinder.station.api.ApiCallback
import com.example.cheapestoilfinder.station.api.BackendApiConfig
import okhttp3.ResponseBody
import java.io.IOException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class BackendDestinationSearchRepository private constructor(
    private val api: com.example.cheapestoilfinder.destination.api.DestinationAutocompleteApiService,
    private val fallbackRepository: DestinationAutocompleteRepository
) : DestinationSearchRepository {

    override fun search(
        query: String,
        callback: ApiCallback<List<DestinationSearchSuggestion>>
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) {
            callback.onSuccess(emptyList())
            return
        }

        val call = api.searchRaw(DestinationSearchRequest(trimmedQuery))
        Log.d(TAG, "destination search request url=${call.request().url}")
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                if (!response.isSuccessful) {
                    val errorBody = readErrorBody(response)
                    Log.w(TAG, "Destination search API error ${response.code()} for ${call.request().url}: $errorBody")
                    fallbackWithLocal(trimmedQuery, callback)
                    return
                }

                val rawBody = response.body()?.string()
                if (rawBody.isNullOrBlank()) {
                    Log.w(TAG, "Destination search API returned empty body for ${call.request().url}")
                    fallbackWithLocal(trimmedQuery, callback)
                    return
                }

                Log.d(TAG, "Destination search raw body: ${rawBody.take(MAX_LOG_BODY_LENGTH)}")
                val mapped = parseSearchResponse(rawBody)

                if (mapped.isEmpty()) {
                    Log.w(TAG, "Destination search API mapped empty result. query='$trimmedQuery'")
                    fallbackWithLocal(trimmedQuery, callback)
                    return
                }

                Log.d(
                    TAG,
                    "Destination search API mapped. query='$trimmedQuery', size=${mapped.size}, coordinateCount=${mapped.count { it.latitude != null && it.longitude != null }}"
                )
                callback.onSuccess(mapped)
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.w(TAG, "Destination search API failed for ${call.request().url}", t)
                fallbackWithLocal(trimmedQuery, callback)
            }
        })
    }

    private fun parseSearchResponse(rawBody: String): List<DestinationSearchSuggestion> {
        return runCatching {
            val root = JsonParser.parseString(rawBody)
            val items = findResultArray(root)
            items.mapNotNull { item ->
                val itemObject = item.asJsonObjectOrNull() ?: return@mapNotNull null
                val displayText = firstString(
                    itemObject,
                    "displayText",
                    "primaryText",
                    "placeName",
                    "place_name",
                    "name",
                    "title",
                    "addressName",
                    "address_name",
                    "roadAddressName",
                    "road_address_name"
                )
                if (displayText.isBlank()) {
                    return@mapNotNull null
                }

                val description = firstString(
                    itemObject,
                    "secondaryText",
                    "addressName",
                    "address_name",
                    "roadAddressName",
                    "road_address_name",
                    "address",
                    "sourceType",
                    "categoryName",
                    "category_name"
                )
                val latitude = firstDouble(itemObject, "latitude", "lat", "y")
                val longitude = firstDouble(itemObject, "longitude", "lng", "lon", "x")
                DestinationSearchSuggestion(
                    displayText = displayText,
                    description = description,
                    latitude = latitude,
                    longitude = longitude,
                    sourceRef = firstString(itemObject, "sourceRef", "id", "placeId", "place_id")
                        .ifBlank { null }
                )
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to parse destination search raw body.", error)
            emptyList()
        }
    }

    private fun findResultArray(root: JsonElement): List<JsonElement> {
        if (root.isJsonArray) {
            return root.asJsonArray.toList()
        }

        val rootObject = root.asJsonObjectOrNull() ?: return emptyList()
        val directArray = firstArray(rootObject, "items", "results", "places", "documents", "data", "content")
        if (directArray.isNotEmpty()) {
            return directArray
        }

        val nestedObjects = listOf("result", "response", "body", "payload")
            .mapNotNull { rootObject.get(it)?.asJsonObjectOrNull() }
        return nestedObjects.firstNotNullOfOrNull { nested ->
            firstArray(nested, "items", "results", "places", "documents", "data", "content")
                .takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    private fun firstArray(jsonObject: JsonObject, vararg names: String): List<JsonElement> {
        return names.firstNotNullOfOrNull { name ->
            jsonObject.get(name)
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.toList()
        }.orEmpty()
    }

    private fun firstString(jsonObject: JsonObject, vararg names: String): String {
        return names.firstNotNullOfOrNull { name ->
            jsonObject.get(name)
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun firstDouble(jsonObject: JsonObject, vararg names: String): Double? {
        return names.firstNotNullOfOrNull { name ->
            val value = jsonObject.get(name)?.takeIf { !it.isJsonNull } ?: return@firstNotNullOfOrNull null
            runCatching { value.asDouble }.getOrNull()
        }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return takeIf { it.isJsonObject }?.asJsonObject
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
        private const val TAG = "DestinationSearch"
        private const val MAX_LOG_BODY_LENGTH = 2000

        fun create(baseUrl: String): BackendDestinationSearchRepository {
            return BackendDestinationSearchRepository(
                DestinationApiClient.create(baseUrl),
                LocalDestinationAutocompleteRepository()
            )
        }

        fun createDefault(): BackendDestinationSearchRepository {
            return create(BackendApiConfig.DEFAULT_BASE_URL)
        }
    }
}
