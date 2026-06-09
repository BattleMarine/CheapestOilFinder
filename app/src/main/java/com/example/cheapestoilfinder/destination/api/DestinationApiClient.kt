package com.example.cheapestoilfinder.destination.api

import com.example.cheapestoilfinder.station.api.BackendApiConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object DestinationApiClient {
    fun create(baseUrl: String): DestinationAutocompleteApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(BackendApiConfig.normalizeBaseUrl(baseUrl))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(DestinationAutocompleteApiService::class.java)
    }
}
