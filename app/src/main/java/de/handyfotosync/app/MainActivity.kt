package de.handyfotosync.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import jcifs.smb.SmbFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var s: Settings
    private lateinit var etHost: EditText
    private lateinit var etShare: EditText
    private lateinit var etUser: EditText
    private lateinit var etPassword: EditText
    private lateinit var etFolder: EditText
    private lateinit var swVideos: SwitchCompat
    private lateinit var swCamera: SwitchCompat
    private lateinit var swAuto: SwitchCompat
    private lateinit var tvPerms: TextView
    private lateinit var btnPerms: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView

    private var busyText: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1500)
        }
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshPerms() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        s = Settings(this)

        etHost = findViewById(R.id.etHost)
        etShare = findViewById(R.id.etShare)
        etUser = findViewById(R.id.etUser)
        etPassword = findViewById(R.id.etPassword)
        etFolder = findViewById(R.id.etFolder)
        swVideos = findViewById(R.id.swVideos)
        swCamera = findViewById(R.id.swCamera)
        swAuto = findViewById(R.id.swAuto)
        tvPerms = findViewById(R.id.tvPerms)
        btnPerms = findViewById(R.id.btnPerms)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)

        etHost.setText(s.host)
        etShare.setText(s.share)
        etUser.setText(s.user)
        etPassword.setText(s.password)
        etFolder.setText(s.folder)
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

        findViewById<Button>(R.id.btnDrives).setOnClickListener { saveFields(); chooseDrive() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { saveFields(); testConnection() }
        btnPerms.setOnClickListener { requestPerms() }
        findViewById<Button>(R.id.btnSync).setOnClickListener { syncNow() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { confirmReset() }

        if (Perms.missing(this, s.includeVideos).isNotEmpty()) requestPerms()
        if (s.autoSync && s.isConfigured) Scheduler.apply(this, true)
    }

    override fun onResume() {
        super.onResume()
        refreshPerms()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        saveFields()
        handler.removeCallbacks(ticker)
    }

    private fun saveFields() {
        s.host = etHost.text.toString().ifBlank { "fritz.box" }
        s.share = etShare.text.toString().ifBlank { "FRITZ.NAS" }
        s.user = etUser.text.toString()
        s.password = etPassword.text.toString()
        s.folder = etFolder.text.toString()
    }

    private fun requestPerms() = permLauncher.launch(Perms.needed(s.includeVideos))

    private fun refreshPerms() {
        val missingMedia = !Perms.hasMedia(this)
        tvPerms.visibility = if (missingMedia) View.VISIBLE else View.GONE
        btnPerms.visibility = if (missingMedia) View.VISIBLE else View.GONE
        if (missingMedia) {
            tvPerms.text = "Die App darf noch nicht auf deine Fotos zugreifen. " +
                "Bitte „Alle erlauben“ wählen (nicht nur ausgewählte Fotos)."
        }
    }

    private fun refreshStatus() {
        tvStatus.text = busyText ?: s.status
        tvLog.text = s.log
    }

    // ---------- Festplatte auf der FRITZ!Box auswählen ----------

    private fun chooseDrive() {
        busyText = "Verbinde mit ${s.host} …"
        refreshStatus()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SmbClient.open(s).use { smb ->
                        smb.dir("").listFiles()
                            .filter { it.isDirectory }
                            .map { it.name.trimEnd('/') }
                            .filter { !it.startsWith(".") }
                    }
                }
            }
            busyText = null
            result.onSuccess { names ->
                if (names.isEmpty()) {
                    toast("Keine Festplatte gefunden – ist sie an der FRITZ!Box angeschlossen?")
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Auf welcher Festplatte speichern?")
                        .setItems(names.toTypedArray()) { _, i ->
                            etFolder.setText("${names[i]}/Handy-Fotos-Videos")
                            saveFields()
                            testConnection()
                        }
                        .show()
                }
            }.onFailure { showError(it) }
            refreshStatus()
        }
    }

    // ---------- Verbindung testen (inkl. Schreibrecht) ----------

    private fun testConnection() {
        if (!s.isConfigured) {
            toast("Bitte Benutzer, Kennwort und Zielordner ausfüllen.")
            return
        }
        busyText = "Teste Verbindung …"
        refreshStatus()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SmbClient.open(s).use { smb ->
                        val d = smb.dir(s.folder)
                        if (!d.exists()) d.mkdirs()
                        val t = smb.file(s.folder + "/.verbindungstest.tmp")
                        SmbFileOutputStream(t).use { it.write("ok".toByteArray()) }
                        t.delete()
                    }
                }
            }
            busyText = null
            result.onSuccess {
                s.status = "✔ Verbindung OK – Ordner „${s.folder}“ ist bereit (${Settings.now()})."
                if (s.autoSync) Scheduler.apply(this@MainActivity, true)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Alles bereit")
                    .setMessage(
                        "Der Ordner „${s.folder}“ auf der FRITZ!Box ist beschreibbar.\n\n" +
                            "Jetzt alle vorhandenen Fotos übertragen? (Beim ersten Mal kann das je nach Menge länger dauern.)"
                    )
                    .setPositiveButton("Jetzt übertragen") { _, _ -> syncNow() }
                    .setNegativeButton("Später", null)
                    .show()
            }.onFailure { showError(it) }
            refreshStatus()
        }
    }

    private fun syncNow() {
        saveFields()
        when {
            !s.isConfigured -> toast("Bitte zuerst die Verbindung einrichten.")
            !Perms.hasMedia(this) -> requestPerms()
            !Net.isOnWifi(this) -> {
                Scheduler.syncNow(this) // wartet automatisch auf WLAN
                toast("Kein WLAN – die Übertragung startet automatisch, sobald du im WLAN bist.")
            }
            else -> {
                Scheduler.syncNow(this)
                s.status = "Übertragung wird gestartet …"
                toast("Übertragung gestartet")
            }
        }
        refreshStatus()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Merkliste zurücksetzen?")
            .setMessage(
                "Die App prüft dann beim nächsten Durchlauf wieder alle Fotos gegen die Festplatte. " +
                    "Bereits vorhandene Dateien werden NICHT doppelt kopiert."
            )
            .setPositiveButton("Zurücksetzen") { _, _ ->
                Ledger(this).clear()
                toast("Merkliste gelöscht")
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showError(e: Throwable) {
        val msg = SmbClient.friendlyError(e)
        s.status = "✖ $msg"
        AlertDialog.Builder(this)
            .setTitle("Verbindung fehlgeschlagen")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .setNeutralButton("Technische Details") { _, _ -> showDiagnosis() }
            .show()
    }

    /** Prüft Adresse, Ports und SMB-Varianten und zeigt das Ergebnis zum Kopieren an. */
    private fun showDiagnosis() {
        saveFields()
        busyText = "Diagnose läuft … (bis zu 30 Sekunden)"
        refreshStatus()
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                try {
                    SmbClient.diagnose(applicationContext, s)
                } catch (e: Exception) {
                    "Diagnose fehlgeschlagen: $e"
                }
            }
            busyText = null
            refreshStatus()
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Technische Details")
                .setMessage(report)
                .setPositiveButton("Kopieren") { _, _ ->
                    val cm = getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("Diagnose", report))
                    toast("Kopiert – du kannst es jetzt einfügen und weitergeben.")
                }
                .setNegativeButton("Schließen", null)
                .show()
        }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
