package com.panda.flashernova.toolbox.rkp

import android.media.DeniedByServerException
import android.media.MediaDrm
import android.media.UnsupportedSchemeException
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID

/**
 * Widevine certificate provisioning (corresponds to True RKP's Widevine.java).
 *
 * Relation to the original tool: the original tool's outer APK contains an author-modified WidevineProvisioner,
 * The behavior is "try the China URL first, fall back to the official URL only on timeout" -- confirmed by real-device testing:
 *   I RkpdWidevine: Provisioning with China URL
 *       https://www.googleapis.cn/certificateprovisioning/v1/devicecertificates/create?key=...
 * The so-called China URL is not a third-party server; it replaces ".com" in the platform-provided address with ".cn".
 *
 * This implementation depends only on the framework's MediaDrm and does not need AOSP rkpdapp classes.
 */
class RkpWidevine(private val log: RkpLogger?) {

    fun provision() {
        var drm: MediaDrm? = null
        try {
            val d = MediaDrm(WIDEVINE_UUID)
            drm = d
            val request = d.provisionRequest
            val data = request.data
            val base = request.defaultUrl
            if (base.isNullOrEmpty()) {
                say("Widevine provides no provisioning URL, skipping")
                return
            }
            val cn = base.replace(".com", ".cn")
            val resp: ByteArray = if (cn != base) {
                say("Requesting Widevine certificate via China URL")
                try {
                    post(cn, data)
                } catch (_: SocketTimeoutException) {
                    say("China URL timed out, falling back to the official address")
                    post(base, data)
                }
            } else {
                say("Requesting Widevine certificate via the official address")
                post(base, data)
            }
            d.provideProvisionResponse(resp)
            say(RkpLevel.OK, "Widevine certificate provisioned (${resp.size} bytes)")
        } catch (_: UnsupportedSchemeException) {
            say("Device does not support Widevine, skipping")
        } catch (_: DeniedByServerException) {
            say(RkpLevel.ERR, "The Widevine server rejected the request")
        } catch (e: IOException) {
            say(RkpLevel.ERR, "Widevine provisioning failed: ${e.message}")
        } catch (t: Throwable) {
            say(RkpLevel.ERR, "Widevine error: $t")
        } finally {
            runCatching { drm?.close() }
        }
    }

    @Throws(IOException::class)
    private fun post(urlStr: String, body: ByteArray): ByteArray {
        val payload = "$urlStr&signedRequest=${String(body)}"
        val conn = URL(payload).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.doInput = true
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setChunkedStreamingMode(0)
        conn.setRequestProperty("User-Agent", userAgent())
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Connection", "close")
        conn.outputStream.use { it.write(ByteArray(0)) }

        val code = conn.responseCode
        val input: InputStream? = if (code == 200) conn.inputStream else conn.errorStream
        val buffer = ByteArrayOutputStream()
        input?.use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                buffer.write(buf, 0, n)
            }
        }
        conn.disconnect()
        if (code != 200) throw IOException("HTTP $code")
        return buffer.toByteArray()
    }

    private fun userAgent(): String = listOf(
        Build.MODEL, Build.TYPE, Build.VERSION.INCREMENTAL, Build.ID
    ).joinToString("/")

    private fun say(s: String) = say(RkpLevel.NORMAL, s)

    private fun say(level: Int, s: String) {
        log?.log(level, s)
    }

    companion object {
        private const val TIMEOUT_MS = 20_000
        private val WIDEVINE_UUID: UUID =
            UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    }
}
