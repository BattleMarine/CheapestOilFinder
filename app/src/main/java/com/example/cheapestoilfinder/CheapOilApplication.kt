package com.example.cheapestoilfinder

import android.app.Application
import android.util.Log
import com.example.cheapestoilfinder.map.DebugKeyHashLogger
import com.example.cheapestoilfinder.map.KakaoMapConfig
import com.kakao.vectormap.KakaoMapSdk

class CheapOilApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugKeyHashLogger.log(this)

        if (KakaoMapConfig.isConfigured()) {
            KakaoMapSdk.init(this, KakaoMapConfig.NATIVE_APP_KEY)
        } else {
            Log.w(TAG, "Kakao native app key is empty. KakaoMapSdk.init skipped.")
        }
    }

    companion object {
        private const val TAG = "CheapOilApplication"
    }
}
