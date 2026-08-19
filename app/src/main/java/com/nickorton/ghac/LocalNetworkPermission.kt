package com.nickorton.ghac

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Access to the user's own LAN, which is the only thing ghac ever connects to.
 *
 * Android 17 (API 37) stopped granting local network access implicitly through
 * INTERNET for apps targeting 37 or higher. When it is missing, connections to
 * a local address do not fail fast — they hang until the socket times out, so
 * the app looks like it simply cannot find the server. Requesting it up front
 * avoids that entirely.
 */
object LocalNetworkPermission {

    const val NAME = "android.permission.ACCESS_LOCAL_NETWORK"

    /**
     * First API level that enforces the permission. Written as a literal
     * because there is no `Build.VERSION_CODES` constant for it on the SDK
     * levels this app also supports.
     */
    private const val ENFORCED_FROM_SDK = 37

    /** True when the permission is irrelevant, or has been granted. */
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < ENFORCED_FROM_SDK) return true
        return ContextCompat.checkSelfPermission(context, NAME) == PackageManager.PERMISSION_GRANTED
    }

    /** True when this device enforces the permission and it is still missing. */
    fun isRequired(context: Context): Boolean = !isGranted(context)
}
