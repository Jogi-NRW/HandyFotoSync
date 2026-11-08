package de.handyfotosync.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat

object Net {
    /** true, wenn das Handy gerade über WLAN (oder LAN) verbunden ist – nicht über Mobilfunk. */
    fun isOnWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}

object Perms {
    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun hasMedia(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) granted(ctx, Manifest.permission.READ_MEDIA_IMAGES)
        else granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)

    /** Nur „ausgewählte Fotos“ freigegeben (Android 14+). */
    fun hasOnlyPartialMedia(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 34 && !hasMedia(ctx) &&
            granted(ctx, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")

    fun hasMediaLocation(ctx: Context): Boolean =
        granted(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION)

    /** Alle Berechtigungen, die die App beim Start anfragt. */
    fun needed(includeVideos: Boolean): Array<String> {
        val list = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (includeVideos) list.add(Manifest.permission.READ_MEDIA_VIDEO)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        list.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        return list.toTypedArray()
    }

    fun missing(ctx: Context, includeVideos: Boolean): List<String> =
        needed(includeVideos)
            .filter { it != "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" }
            .filter { !granted(ctx, it) }
}
