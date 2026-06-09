package com.example.cheapestoilfinder.destination.api

import retrofit2.Call
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface DestinationAutocompleteApiService {
    @GET("api/places/autocomplete")
    fun autocomplete(@Query("query") query: String): Call<DestinationAutocompleteResponse>

    @POST("api/places/search")
    fun search(@Body request: DestinationSearchRequest): Call<DestinationSearchResponse>

    @POST("api/places/search")
    fun searchRaw(@Body request: DestinationSearchRequest): Call<ResponseBody>
}
