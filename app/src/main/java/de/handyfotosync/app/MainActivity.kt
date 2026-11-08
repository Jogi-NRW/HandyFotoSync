package de.handyfotosync.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Übersicht: Status, Fortschritt, Schalter, Verlauf und „Jetzt übertragen“. */
class MainActivity : AppCompatActivity() {

    private lateinit var s: Settings
    private lateinit var chipWifi: TextView
    private lateinit var tvHost: TextView
    private lateinit var tvState: TextView
    private lateinit var tvBig: TextView
    private lateinit var tvDetail: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var statNew: TextView
    private lateinit var statAlready: TextView
    private lateinit var statFailed: TextView
    private lateinit var tvFolder: TextView
    private lateinit var tvLog: TextView
    private lateinit var permBox: LinearLayout
    private lateinit var tvPerm: TextView
    private lateinit var btnPerms: MaterialButton
    private lateinit var swVideos: MaterialSwitch
    private lateinit var swCamera: MaterialSwitch
    private lateinit var swAuto: MaterialSwitch

    private var busyText: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        s = Settings(this)

        Ui.applyInsets(
            this, findViewById(R.id.screen), findViewById(R.id.appbar),
            findViewById(R.id.scroll), findViewById(R.id.bottomBar)
        )

        chipWifi = findViewById(R.id.chipWifi)
        tvHost = findViewById(R.id.tvHost)
        tvState = findViewById(R.id.tvState)
        tvBig = findViewById(R.id.tvBig)
        tvDetail = findViewById(R.id.tvDetail)
        progress = findViewById(R.id.progress)
        statNew = findViewById(R.id.statNew)
        statAlready = findViewById(R.id.statAlready)
        statFailed = findViewById(R.id.statFailed)
        tvFolder = findViewById(R.id.tvFolder)
        tvLog = findViewById(R.id.tvLog)
        permBox = findViewById(R.id.permBox)
        tvPerm = findViewById(R.id.tvPerm)
        btnPerms = findViewById(R.id.btnPerms)
        swVideos = findViewById(R.id.swVideos)
        swCamera = findViewById(R.id.swCamera)
        swAuto = findViewById(R.id.swAuto)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_setup -> {
                    startActivity(Intent(this, SetupActivity::class.java)); true
                }
                R.id.action_diag -> {
                    Ui.showDiagnosis(this, s) { busyText = it; refresh() }; true
                }
                R.id.action_reset -> {
                    confirmReset(); true
                }
                else -> false
            }
        }

        swVideos.isChecked = s.includeVideos
        swCamera.isChecked = s.onlyCamera
        swAuto.isChecked = s.autoSync
        swVideos.setOnCheckedChangeListener { _, on ->
            s.includeVideos = on
            if (on) requestPerms()
        }
        swCamera.setOnCheckedChangeListener { _, on -> s.onlyCamera = on }
        swAuto.setOnCheckedChangeListener { _, on ->
            s.autoSync = on
            Scheduler.apply(this, on && s.isConfigured)
        }

        findViewById<MaterialButton>(R.id.btnPerms).setOnClickListener {
            if (Perms.hasOnlyPartialMedia(this)) openAppSettings() else requestPerms()
        }
        findViewById<MaterialButton>(R.id.btnSync).setOnClickListener { syncNow() }

        if (Perms.missing(this, s.includeVideos).isNotEmpty()) requestPerms()
        if (s.autoSync && s.isConfigured) Scheduler.apply(this, true)
        if (!s.isConfigured && savedInstanceState == null) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun requestPerms() = permLauncher.launch(Perms.needed(s.includeVideos))

    private fun openAppSettings() {
        startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", packageName, null))
        )
    }

    private fun refresh() {
        // WLAN-Anzeige
        val wifi = Net.isOnWifi(this)
        chipWifi.text = if (wifi) "WLAN verbunden" else "Kein WLAN"
        val chipColor = ContextCompat.getColor(this, if (wifi) R.color.green else R.color.orange)
        chipWifi.setTextColor(chipColor)
        chipWifi.setBackgroundResource(if (wifi) R.drawable.bg_chip_ok else R.drawable.bg_chip_warn)
        chipWifi.compoundDrawablesRelative[0]?.setTint(chipColor)
        tvHost.text = s.host

        // Status
        if (busyText != null) {
            tvState.text = "Bitte warten"
            tvBig.text = busyText
            tvDetail.visibility = View.GONE
            progress.visibility = View.GONE
        } else if (s.running && s.total > 0) {
            tvState.text = "Übertragung läuft"
            val big = SpannableStringBuilder("${s.done}")
            val start = big.length
            big.append(" von ${s.total}")
            big.setSpan(RelativeSizeSpan(0.62f), start, big.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            big.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.grey)),
                start, big.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            tvBig.text = big
            tvDetail.visibility = View.GONE
            progress.visibility = View.VISIBLE
            progress.setProgressCompat(s.done * 100 / s.total, true)
        } else {
            tvState.text = if (s.lastRun.isNotEmpty()) "Letzter Lauf · ${s.lastRun}" else "Noch keine Übertragung"
            tvBig.text = s.headline.ifEmpty { if (s.isConfigured) "Bereit" else "Noch nicht eingerichtet" }
            val detail = if (s.headline.isEmpty()) "" else s.status
            tvDetail.text = detail
            tvDetail.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
            progress.visibility = View.GONE
        }
        statNew.text = s.cntNew.toString()
        statAlready.text = s.cntAlready.toString()
        statFailed.text = s.cntFailed.toString()
        tvFolder.text = s.folder.ifEmpty { "Kein Zielordner – oben rechts auf Einrichtung tippen" }

        // Verlauf: die letzten Einträge
        val lines = s.log.lines().filter { it.isNotBlank() }.take(6)
        tvLog.text = if (lines.isEmpty()) "Noch keine Einträge." else lines.joinToString("\n")

        permBox.visibility = if (Perms.hasMedia(this)) View.GONE else View.VISIBLE
        if (Perms.hasOnlyPartialMedia(this)) {
            tvPerm.text = "Die App darf nur auf ausgewählte Fotos zugreifen. Damit alle Fotos gesichert werden: " +
                "Berechtigungen → Fotos und Videos → „Immer alle erlauben“."
            btnPerms.text = "Einstellungen öffnen"
        } else {
            tvPerm.text = "Die App darf noch nicht auf deine Fotos zugreifen. Bitte „Alle erlauben“ wählen."
            btnPerms.text = "Berechtigungen erteilen"
        }
    }

    private fun syncNow() {
        when {
            !s.isConfigured -> startActivity(Intent(this, SetupActivity::class.java))
            Perms.hasOnlyPartialMedia(this) -> openAppSettings()
            !Perms.hasMedia(this) -> requestPerms()
            !Net.isOnWifi(this) -> {
                Scheduler.syncNow(this) // wartet automatisch auf WLAN
                Ui.toast(this, "Kein WLAN – die Übertragung startet automatisch, sobald du im WLAN bist.")
            }
            else -> {
                Scheduler.syncNow(this)
                Ui.toast(this, "Übertragung gestartet")
            }
        }
        refresh()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Merkliste zurücksetzen?")
            .setMessage(
                "Die App prüft dann beim nächsten Durchlauf wieder alle Fotos gegen die Festplatte. " +
                    "Bereits vorhandene Dateien werden NICHT doppelt kopiert."
            )
            .setPositiveButton("Zurücksetzen") { _, _ ->
                Ledger(this).clear()
                Ui.toast(this, "Merkliste gelöscht")
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}
