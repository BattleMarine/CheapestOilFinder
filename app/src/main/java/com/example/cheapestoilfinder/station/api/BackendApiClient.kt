package com.example.cheapestoilfinder.station.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object BackendApiClient {
    fun create(baseUrl: String): StationApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(BackendApiConfig.normalizeBaseUrl(baseUrl))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(StationApiService::class.java)
    }
}
