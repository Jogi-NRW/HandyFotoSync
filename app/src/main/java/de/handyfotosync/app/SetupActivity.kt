package de.handyfotosync.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Einrichtung: Verbindung zur FRITZ!Box und Zielordner. */
class SetupActivity : AppCompatActivity() {

    private data class Drive(val name: String, val free: Long)

    companion object {
        private const val TARGET = "Handy-Fotos-Videos"
    }

    private lateinit var s: Settings
    private lateinit var etHost: EditText
    private lateinit var etShare: EditText
    private lateinit var etUser: EditText
    private lateinit var etPassword: EditText
    private lateinit var etFolder: EditText
    private lateinit var btnSave: MaterialButton
    private lateinit var tvSetupStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        s = Settings(this)

        Ui.applyInsets(
            this, findViewById(R.id.screen), findViewById(R.id.appbar),
            findViewById(R.id.scroll), findViewById(R.id.bottomBar)
        )

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.navigationContentDescription = "Zurück"
        toolbar.setNavigationOnClickListener { saveFields(); finish() }

        etHost = findViewById(R.id.etHost)
        etShare = findViewById(R.id.etShare)
        etUser = findViewById(R.id.etUser)
        etPassword = findViewById(R.id.etPassword)
        etFolder = findViewById(R.id.etFolder)
        btnSave = findViewById(R.id.btnSave)
        tvSetupStatus = findViewById(R.id.tvSetupStatus)

        etHost.setText(s.host)
        etShare.setText(s.share)
        etUser.setText(s.user)
        etPassword.setText(s.password)
        etFolder.setText(s.folder)

        findViewById<ImageButton>(R.id.btnDrives).setOnClickListener { saveFields(); chooseDrive() }
        btnSave.setOnClickListener { saveFields(); testConnection() }
    }

    override fun onPause() {
        super.onPause()
        saveFields()
    }

    private fun saveFields() {
        s.host = etHost.text.toString().ifBlank { "192.168.178.1" }
        s.share = etShare.text.toString().ifBlank { "FRITZ.NAS" }
        s.user = etUser.text.toString()
        s.password = etPassword.text.toString()
        s.folder = etFolder.text.toString()
    }

    private fun setBusy(text: String?) {
        btnSave.isEnabled = text == null
        btnSave.text = text ?: "Speichern & Verbindung testen"
    }

    // ---------- Festplatte auswählen (Fenster von unten) ----------

    private fun chooseDrive() {
        if (s.user.isBlank()) {
            Ui.toast(this, "Bitte zuerst Benutzer und Kennwort eintragen.")
            return
        }
        setBusy("Suche Festplatten …")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SmbClient.open(s).use { smb ->
                        smb.dir("").listFiles()
                            .filter { it.isDirectory && !it.name.startsWith(".") }
                            .map { f ->
                                val free = try {
                                    f.diskFreeSpace
                                } catch (_: Exception) {
                                    -1L
                                }
                                Drive(f.name.trimEnd('/'), free)
                            }
                    }
                }
            }
            setBusy(null)
            result.onSuccess { drives ->
                showDriveSheet(drives)
            }.onFailure { e ->
                Ui.showError(this@SetupActivity, s, e) { setBusy(it) }
            }
        }
    }

    private fun showDriveSheet(found: List<Drive>) {
        // Systemordner der FRITZ!Box und den Zielordner selbst nicht anbieten
        val folders = found.filter {
            !it.name.equals("FRITZ", ignoreCase = true) &&
                !it.name.equals(TARGET, ignoreCase = true)
        }
        val sameFree = found.map { it.free }.distinct().size <= 1
        val free = found.firstOrNull()?.free ?: -1L

        // Eintrag 0 = direkt in der Freigabe (empfohlen), danach die gefundenen Ordner
        val paths = listOf("") + folders.map { it.name }
        val titles = listOf("Direkt in ${s.share}") + folders.map { it.name }
        val infos = listOf("Empfohlen · Ordner $TARGET hier anlegen") + folders.map {
            if (!sameFree && it.free >= 0) "Speicher · ${Ui.formatBytes(it.free)} frei" else "Ordner"
        }

        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_drives, null)
        view.findViewById<TextView>(R.id.tvSheetSub).text =
            "Gefunden an deiner FRITZ!Box" +
                (if (sameFree && free >= 0) " · ${Ui.formatBytes(free)} frei" else "") +
                ". Der Ordner $TARGET wird dort angelegt."
        view.findViewById<TextView>(R.id.tvLogin).text = "Anmeldung als „${s.user}“ erfolgreich"

        val list = view.findViewById<LinearLayout>(R.id.driveList)
        val previousFolder = etFolder.text.toString()
        val currentParent = s.folder.trim('/').let {
            if (it.contains('/')) it.substringBeforeLast('/') else ""
        }
        var selected = paths.indexOfFirst { it.equals(currentParent, ignoreCase = true) }.let { if (it < 0) 0 else it }
        val rows = ArrayList<LinearLayout>()

        fun apply(i: Int) {
            // Ordner sofort übernehmen – nicht erst beim Knopf „Auswählen“
            etFolder.setText(if (paths[i].isEmpty()) TARGET else "${paths[i]}/$TARGET")
            saveFields()
        }

        fun render() {
            rows.forEachIndexed { i, row ->
                row.setBackgroundResource(
                    if (i == selected) R.drawable.bg_drive_item_selected else R.drawable.bg_drive_item
                )
                row.findViewById<RadioButton>(R.id.rbDrive).isChecked = i == selected
            }
        }

        paths.indices.forEach { i ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_drive, list, false) as LinearLayout
            row.findViewById<TextView>(R.id.tvDriveName).text = titles[i]
            row.findViewById<TextView>(R.id.tvDriveInfo).text = infos[i]
            row.findViewById<ImageView>(R.id.ivDriveIcon)
                .setImageResource(if (i == 0) R.drawable.ic_drive else R.drawable.ic_folder)
            row.setOnClickListener { selected = i; render(); apply(i) }
            list.addView(row)
            rows.add(row)
        }
        render()

        // Lange Listen scrollen in einem begrenzten Bereich – die Knöpfe bleiben sichtbar
        val maxListHeight = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        val listScroll = view.findViewById<NestedScrollView>(R.id.listScroll)
        list.measure(
            View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        if (list.measuredHeight > maxListHeight) {
            listScroll.layoutParams = listScroll.layoutParams.apply { height = maxListHeight }
        }

        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            etFolder.setText(previousFolder)
            saveFields()
            sheet.dismiss()
        }
        view.findViewById<MaterialButton>(R.id.btnChoose).setOnClickListener {
            apply(selected)
            sheet.dismiss()
            testConnection()
        }
        sheet.setContentView(view)

        // Immer ganz aufklappen und Abstand zur Navigationsleiste halten
        val basePad = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePad + nav.bottom)
            insets
        }
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.setOnShowListener { sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        sheet.show()
    }

    // ---------- Verbindung testen (inkl. Schreibrecht) ----------

    private fun testConnection() {
        if (!s.isConfigured) {
            Ui.toast(this, "Bitte Benutzer, Kennwort und Zielordner ausfüllen.")
            return
        }
        setBusy("Teste Verbindung …")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SmbClient.open(s).use { smb ->
                        val d = smb.dir(s.folder)
                        if (!d.exists()) d.mkdirs()
                        val t = smb.file(s.folder + "/.verbindungstest.tmp")
                        jcifs.smb.SmbFileOutputStream(t).use { it.write("ok".toByteArray()) }
                        t.delete()
                    }
                }
            }
            setBusy(null)
            result.onSuccess {
                tvSetupStatus.setTextColor(ContextCompat.getColor(this@SetupActivity, R.color.green))
                tvSetupStatus.text = "✔ Verbindung OK – Ordner „${s.folder}“ ist bereit."
                if (s.autoSync) Scheduler.apply(this@SetupActivity, true)
                MaterialAlertDialogBuilder(this@SetupActivity)
                    .setTitle("Alles bereit")
                    .setMessage(
                        "Der Ordner „${s.folder}“ auf der FRITZ!Box ist beschreibbar.\n\n" +
                            "Jetzt alle vorhandenen Fotos übertragen?"
                    )
                    .setPositiveButton("Jetzt übertragen") { _, _ ->
                        Scheduler.syncNow(this@SetupActivity)
                        finish()
                    }
                    .setNegativeButton("Später") { _, _ -> finish() }
                    .show()
            }.onFailure { e ->
                tvSetupStatus.setTextColor(ContextCompat.getColor(this@SetupActivity, R.color.orange))
                tvSetupStatus.text = SmbClient.friendlyError(e)
                Ui.showError(this@SetupActivity, s, e) { setBusy(it) }
            }
        }
    }
}
