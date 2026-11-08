package de.handyfotosync.app

import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Gemeinsame Hilfen für beide Bildschirme. */
object Ui {

    /**
     * Android 15+ zeichnet bis unter Status- und Navigationsleiste.
     * Blaue Leiste oben beginnt hinter der Statusleiste, der Knopf unten bleibt über
     * Navigationsleiste bzw. Tastatur.
     */
    fun applyInsets(act: AppCompatActivity, screen: View, appbar: View, scroll: View, bottomBar: View) {
        WindowCompat.getInsetsController(act.window, act.window.decorView).isAppearanceLightStatusBars = false
        val baseBottom = bottomBar.paddingBottom
        val baseLeft = bottomBar.paddingLeft
        val baseRight = bottomBar.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(screen) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            appbar.setPadding(bars.left, bars.top, bars.right, 0)
            scroll.setPadding(bars.left, 0, bars.right, 0)
            bottomBar.setPadding(
                baseLeft + bars.left, bottomBar.paddingTop, baseRight + bars.right,
                baseBottom + maxOf(bars.bottom, ime.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    fun toast(act: AppCompatActivity, t: String) = Toast.makeText(act, t, Toast.LENGTH_LONG).show()

    /** Fehlerfenster mit Knopf „Technische Details“. */
    fun showError(act: AppCompatActivity, s: Settings, e: Throwable, onBusy: (String?) -> Unit) {
        val msg = SmbClient.friendlyError(e)
        MaterialAlertDialogBuilder(act)
            .setTitle("Verbindung fehlgeschlagen")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .setNeutralButton("Technische Details") { _, _ -> showDiagnosis(act, s, onBusy) }
            .show()
    }

    /** Prüft Adresse, Ports und SMB-Varianten und zeigt das Ergebnis zum Kopieren an. */
    fun showDiagnosis(act: AppCompatActivity, s: Settings, onBusy: (String?) -> Unit) {
        onBusy("Diagnose läuft … (bis zu 30 Sekunden)")
        act.lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                try {
                    SmbClient.diagnose(act.applicationContext, s)
                } catch (e: Exception) {
                    "Diagnose fehlgeschlagen: $e"
                }
            }
            onBusy(null)
            MaterialAlertDialogBuilder(act)
                .setTitle("Technische Details")
                .setMessage(report)
                .setPositiveButton("Kopieren") { _, _ ->
                    val cm = act.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("Diagnose", report))
                    toast(act, "Kopiert – du kannst es jetzt einfügen und weitergeben.")
                }
                .setNegativeButton("Schließen", null)
                .show()
        }
    }

    /** 1234567890 → "1,1 GB" */
    fun formatBytes(b: Long): String {
        if (b < 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024; i++
        }
        return if (i >= 3) String.format(Locale.GERMANY, "%.1f %s", v, units[i])
        else String.format(Locale.GERMANY, "%.0f %s", v, units[i])
    }
}
