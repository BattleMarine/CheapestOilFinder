package com.example.cheapestoilfinder.map

import com.example.cheapestoilfinder.BuildConfig

object KakaoMapConfig {
    val NATIVE_APP_KEY: String = BuildConfig.KAKAO_NATIVE_APP_KEY

    fun isConfigured(): Boolean {
        return NATIVE_APP_KEY.trim().isNotEmpty()
    }
}
