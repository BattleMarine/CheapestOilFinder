package com.example.cheapestoilfinder.station.api

interface ApiCallback<T> {
    fun onSuccess(result: T)
    fun onError(error: Throwable)
}
