package com.universalremote.app.control

import com.universalremote.app.model.Device
import com.universalremote.app.model.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private fun requireHost(device: Device) {
    if (device.host.isBlank()) throw IOException("No IP address set for this TV. Edit it and add one.")
}

/**
 * Vestel-made smart TVs (Polaroid, Toshiba, JVC, Hitachi, Bush, Luxor…) run a
 * "SmartCenter" service on port 56789 that accepts remote keys as small XML posts.
 */
class VestelController(override var device: Device) : TvController {
    override fun defaultCode(key: Key): String? = CodeTables.vestel[key]?.toString()

    override suspend fun sendCode(code: String) {
        requireHost(device)
        withContext(Dispatchers.IO) {
            val port = device.port ?: 56789
            val body = "<?xml version='1.0' ?><remote><key code='${code.trim()}'/></remote>"
            val req = Request.Builder()
                .url("http://${device.host}:$port/apps/SmartCenter")
                .header("Connection", "keep-alive")
                .post(body.toRequestBody("text/plain; charset=ISO-8859-1".toMediaType()))
                .build()
            try {
                Net.http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("TV replied HTTP ${resp.code}")
                }
            } catch (e: java.net.ConnectException) {
                throw IOException(
                    "Couldn't reach the TV on port $port. Check it's on and on the same Wi-Fi, " +
                        "and that Virtual Remote / network standby is turned on in the TV's settings."
                )
            }
        }
    }
}

/** Roku External Control Protocol (port 8060). */
class RokuController(override var device: Device) : TvController {
    override fun defaultCode(key: Key): String? = CodeTables.roku[key]

    override suspend fun sendCode(code: String) {
        requireHost(device)
        withContext(Dispatchers.IO) {
            val c = code.trim()
            val path = if (c.startsWith("launch/") || c.startsWith("keypress/")) c else "keypress/$c"
            val req = Request.Builder()
                .url("http://${device.host}:${device.port ?: 8060}/$path")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            Net.http.newCall(req).execute().use { resp ->
                if (resp.code == 403) throw IOException(
                    "Roku refused the command. On the Roku go to Settings › System › Advanced system settings › " +
                        "Control by mobile apps and set it to Permissive or Enabled."
                )
                if (!resp.isSuccessful) throw IOException("Roku replied HTTP ${resp.code}")
            }
        }
    }
}

/** Sony Bravia IRCC over HTTP with a Pre-Shared Key. */
class SonyController(override var device: Device) : TvController {
    override fun defaultCode(key: Key): String? = CodeTables.sony[key]

    override suspend fun sendCode(code: String) {
        requireHost(device)
        withContext(Dispatchers.IO) {
            val body = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>${code.trim()}</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"""
            val req = Request.Builder()
                .url("http://${device.host}:${device.port ?: 80}/sony/IRCC")
                .header("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
                .header("X-Auth-PSK", device.psk)
                .post(body.toRequestBody("text/xml; charset=UTF-8".toMediaType()))
                .build()
            Net.http.newCall(req).execute().use { resp ->
                if (resp.code == 403) throw IOException(
                    "TV rejected the Pre-Shared Key. Set it under Settings › Network › Home network › IP control."
                )
                if (!resp.isSuccessful) throw IOException("TV replied HTTP ${resp.code}")
            }
        }
    }
}
