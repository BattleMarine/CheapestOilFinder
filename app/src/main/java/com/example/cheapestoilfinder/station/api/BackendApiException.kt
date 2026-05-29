package com.example.cheapestoilfinder.station.api

import java.io.IOException

class BackendApiException(
    val statusCode: Int,
    val responseBody: String?
) : IOException("Backend API request failed with status $statusCode")
