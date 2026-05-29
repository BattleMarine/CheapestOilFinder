package com.example.cheapestoilfinder.map

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

object DebugKeyHashLogger {
    private const val TAG = "DebugKeyHashLogger"

    fun log(context: Context) {
        try {
            val packageInfo: PackageInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )

                logSignatures(packageInfo.signingInfo?.apkContentsSigners)
            } else {
                packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                logSignatures(packageInfo.signatures)
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to compute key hash", exception)
        }
    }

    private fun logSignatures(signatures: Array<out Signature>?) {
        if (signatures == null) {
            return
        }

        for (signature in signatures) {
            val digest = MessageDigest.getInstance("SHA")
            digest.update(signature.toByteArray())
            val keyHash = Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
            Log.i(TAG, "Kakao key hash: $keyHash")
        }
    }
}
