package de.handyfotosync.app

import android.content.Context
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbFile
import java.net.ConnectException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Properties

/**
 * Verbindung zur FRITZ!NAS-Freigabe per SMB 2/3 (SMB 1 bleibt aus).
 * Nicht direkt erzeugen, sondern über [open] – das probiert mehrere SMB-Varianten durch.
 */
class SmbClient(private val s: Settings, profile: Int) : AutoCloseable {
    private val base: BaseContext
    private val ctx: CIFSContext
    private val hostAddr: String = resolveHost(s.host)

    init {
        val props = Properties()
        props.setProperty("jcifs.smb.client.minVersion", "SMB202")
        props.setProperty("jcifs.smb.client.maxVersion", PROFILES[profile])
        props.setProperty("jcifs.resolveOrder", "DNS,BCAST")
        props.setProperty("jcifs.smb.client.connTimeout", "10000")
        props.setProperty("jcifs.smb.client.responseTimeout", "120000") // Festplatten an der FRITZ!Box sind bei großen Videos teils langsam
        props.setProperty("jcifs.smb.client.soTimeout", "180000")
        props.setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
        base = BaseContext(PropertyConfiguration(props))
        ctx = base.withCredentials(NtlmPasswordAuthenticator("", s.user, s.password))
    }

    private fun url(path: String): String {
        val parts = (listOf(s.share) + path.split('/'))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return "smb://$hostAddr/" + parts.joinToString("/")
    }

    /** Ordner (URL muss mit "/" enden). */
    fun dir(path: String): SmbFile = SmbFile(url(path).trimEnd('/') + "/", ctx)

    /** Datei. */
    fun file(path: String): SmbFile = SmbFile(url(path), ctx)

    override fun close() {
        try {
            base.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        /** Höchste erlaubte SMB-Version, in der Reihenfolge, in der sie probiert werden. */
        val PROFILES = arrayOf("SMB311", "SMB300", "SMB210")

        /** "fritz.box" → IPv4-Adresse (z. B. 192.168.178.1). IPv4 wird bevorzugt. */
        fun resolveHost(host: String): String {
            val h = host.trim()
            return try {
                val all = InetAddress.getAllByName(h)
                val pick = all.firstOrNull { it is Inet4Address } ?: all.first()
                val addr = pick.hostAddress ?: return h
                if (pick is Inet6Address) "[" + addr.substringBefore('%') + "]" else addr
            } catch (_: Exception) {
                h
            }
        }

        /** Öffnet die Verbindung; probiert bei Bedarf ältere SMB-Versionen. */
        fun open(s: Settings): SmbClient {
            val first = s.smbProfile
            val order = listOf(first) + PROFILES.indices.filter { it != first }
            var last: Exception? = null
            for (p in order) {
                val c = SmbClient(s, p)
                try {
                    c.dir("").exists() // Verbindungsaufbau + Anmeldung
                    if (p != s.smbProfile) s.smbProfile = p
                    return c
                } catch (e: Exception) {
                    c.close()
                    last = e
                    // Falsches Kennwort nicht mehrfach probieren (FRITZ!Box sperrt sonst kurz)
                    if (isAuthError(e)) throw e
                }
            }
            throw last ?: IllegalStateException("Keine Verbindung")
        }

        private fun isAuthError(e: Throwable): Boolean {
            var c: Throwable? = e
            while (c != null) {
                if (c is SmbAuthException) return true
                c = c.cause
            }
            return false
        }

        private fun chain(e: Throwable): String {
            val parts = ArrayList<String>()
            var c: Throwable? = e
            while (c != null && parts.size < 5) {
                parts.add(c.javaClass.simpleName + (c.message?.let { ": $it" } ?: ""))
                c = c.cause
            }
            return parts.joinToString("\n   ← ")
        }

        /** Ausführliche Diagnose zum Weitergeben. Nur im Hintergrund-Thread aufrufen. */
        fun diagnose(context: Context, s: Settings): String {
            val sb = StringBuilder()
            sb.append("App 1.8 · Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" · ").append(android.os.Build.MODEL).append('\n')
            sb.append("WLAN aktiv: ").append(if (Net.isOnWifi(context)) "ja" else "NEIN").append('\n')
            sb.append("Adresse: ").append(s.host).append('\n')
            try {
                val all = InetAddress.getAllByName(s.host.trim())
                sb.append("  aufgelöst: ").append(all.joinToString { it.hostAddress ?: "?" }).append('\n')
            } catch (e: Exception) {
                sb.append("  Auflösung fehlgeschlagen: ").append(chain(e)).append('\n')
            }
            val ip = resolveHost(s.host).trim('[', ']')
            sb.append("  benutzt: ").append(ip).append('\n')
            for (port in listOf(445, 139)) {
                sb.append("Port ").append(port).append(": ")
                try {
                    Socket().use { it.connect(InetSocketAddress(ip, port), 4000) }
                    sb.append("offen\n")
                } catch (e: Exception) {
                    sb.append("nicht erreichbar – ").append(chain(e)).append('\n')
                }
            }
            sb.append("Freigabe: ").append(s.share).append(" · Benutzer: ").append(s.user).append('\n')
            for ((i, d) in PROFILES.withIndex()) {
                sb.append("SMB bis ").append(d).append(": ")
                try {
                    SmbClient(s, i).use { it.dir("").exists() }
                    sb.append("OK\n")
                } catch (e: Exception) {
                    sb.append(chain(e)).append('\n')
                    if (isAuthError(e)) break
                }
            }
            return sb.toString()
        }

        /** Übersetzt technische Fehler in verständliche Hinweise. */
        fun friendlyError(e: Throwable): String {
            var c: Throwable? = e
            while (c != null) {
                when (c) {
                    is SmbAuthException ->
                        return "Anmeldung abgelehnt: Benutzername/Kennwort prüfen und beim FRITZ!Box-Benutzer „Zugang zu NAS-Inhalten“ aktivieren."
                    is UnknownHostException ->
                        return "FRITZ!Box nicht gefunden – bist du im Heim-WLAN? (Alternativ als Adresse 192.168.178.1 eintragen.)"
                    is ConnectException, is SocketTimeoutException, is NoRouteToHostException ->
                        return "Keine Verbindung zur FRITZ!Box – ist „Zugriff über ein Netzlaufwerk (SMB)“ aktiviert und bist du im Heim-WLAN (nicht Gast-WLAN)?"
                }
                val msg = c.message ?: ""
                if (msg.contains("denied", ignoreCase = true)) {
                    return "Zugriff verweigert: Der FRITZ!Box-Benutzer braucht Lese- UND Schreibrechte auf den Speicher."
                }
                if (msg.contains("cannot find the path", ignoreCase = true) ||
                    msg.contains("cannot find the file", ignoreCase = true) ||
                    msg.contains("bad network name", ignoreCase = true)
                ) {
                    return "Freigabe oder Pfad nicht gefunden – Freigabename (meist FRITZ.NAS) und Speicher-Ordner prüfen."
                }
                c = c.cause
            }
            return e.message ?: e.javaClass.simpleName
        }
    }
}
