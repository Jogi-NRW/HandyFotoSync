package de.handyfotosync.app

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Plant die automatischen Übertragungen.
 * NetworkType.UNMETERED = nur WLAN, nie über mobile Daten.
 */
object Scheduler {
    private const val PERIODIC = "sync_stuendlich"
    private const val ON_NEW = "sync_bei_neuem_foto"
    private const val NOW = "sync_jetzt"
    const val TAG_TRIGGER = "ausloeser_neues_foto"

    private fun wifiOnly() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .build()

    /** Automatik ein- oder ausschalten. */
    fun apply(ctx: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(ON_NEW)
            return
        }
        // 1) Stündliche Kontrolle (sobald WLAN da ist)
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(wifiOnly())
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        // 2) Zusätzlich: kurz nach jedem neuen Foto
        enqueueOnNewMedia(ctx, ExistingWorkPolicy.KEEP)
    }

    /** Startet eine Übertragung, sobald neue Fotos/Videos in der Mediathek auftauchen (und WLAN da ist). */
    fun enqueueOnNewMedia(ctx: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE) {
        val c = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(1, TimeUnit.MINUTES)
            .setTriggerContentMaxDelay(10, TimeUnit.MINUTES)
            .build()
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(c)
            .addTag(TAG_TRIGGER)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(ON_NEW, policy, req)
    }

    /** Knopf „Jetzt übertragen“ – läuft sofort, sofern WLAN verbunden ist. */
    fun syncNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(wifiOnly())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }
}
