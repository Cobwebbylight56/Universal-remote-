package com.universalremote.app.control

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/** A device that answered a UPnP/DLNA search. Many TVs announce themselves this way. */
data class UpnpDevice(
    val host: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val deviceType: String,
) {
    val looksLikeTv: Boolean
        get() {
            val text = "$name $manufacturer $model $deviceType".lowercase()
            return listOf("tv", "vestel", "polaroid", "toshiba", "hitachi", "jvc", "bush", "luxor", "mediarenderer", "dial")
                .any { it in text }
        }
}

data class PortResult(val port: Int, val meaning: String, val open: Boolean)

object Diagnostics {
    val portsToCheck = listOf(
        56789 to "Vestel SmartCenter remote (what this app uses for Polaroid/Toshiba/JVC)",
        4660 to "Vestel/Toshiba telnet remote",
        1986 to "Vestel LAN control",
        56790 to "Vestel DIAL / casting",
        8008 to "Google Cast / DIAL",
        8009 to "Google Cast",
        8060 to "Roku",
        8001 to "Samsung",
        8002 to "Samsung (secure)",
        3000 to "LG webOS",
        3001 to "LG webOS (secure)",
        1925 to "Philips",
        6466 to "Android TV remote",
        80 to "Web server",
        8080 to "Web server (alt)",
    )

    private fun isOpen(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 800); true }
    } catch (e: Exception) {
        false
    }

    suspend fun checkPorts(host: String): List<PortResult> = coroutineScope {
        portsToCheck.map { (port, meaning) ->
            async(Dispatchers.IO) { PortResult(port, meaning, isOpen(host, port)) }
        }.awaitAll()
    }

    suspend fun isReachable(host: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { InetAddress.getByName(host).isReachable(1500) }.getOrDefault(false)
    }

    /** Sends an SSDP "ssdp:all" search and reads each responder's description. Calls [onFound] on the main thread. */
    suspend fun upnpSearch(context: Context, onFound: (UpnpDevice) -> Unit) = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("upnp")?.apply { setReferenceCounted(false); acquire() }
        val locations = ConcurrentHashMap<String, String>() // location -> host
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val group = InetAddress.getByName("239.255.255.250")
                for (st in listOf("ssdp:all", "urn:dial-multiscreen-org:service:dial:1", "urn:schemas-upnp-org:device:MediaRenderer:1")) {
                    val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\nST: $st\r\n\r\n").toByteArray()
                    socket.send(DatagramPacket(msg, msg.size, group, 1900))
                }
                val deadline = System.currentTimeMillis() + 4000
                val buf = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length)
                    val location = text.lineSequence()
                        .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                        ?.substringAfter(':')?.trim() ?: continue
                    val host = packet.address.hostAddress ?: continue
                    locations.putIfAbsent(location, host)
                }
            }
        } finally {
            lock?.release()
        }

        val devices = coroutineScope {
            locations.map { (location, host) ->
                async {
                    val xml = runCatching {
                        Net.http.newCall(Request.Builder().url(location).build()).execute().use { it.body?.string() }
                    }.getOrNull().orEmpty()
                    fun tag(t: String) = Regex("<$t>([^<]*)</$t>").find(xml)?.groupValues?.get(1)?.trim().orEmpty()
                    UpnpDevice(
                        host = host,
                        name = tag("friendlyName").ifBlank { host },
                        manufacturer = tag("manufacturer"),
                        model = listOf(tag("modelName"), tag("modelNumber")).filter { it.isNotBlank() }.joinToString(" "),
                        deviceType = tag("deviceType").substringAfter("device:").substringBefore(':'),
                    )
                }
            }.awaitAll()
        }
        // One entry per address, preferring the description that looks most like a TV.
        val best = devices.groupBy { it.host }.values.map { list ->
            list.firstOrNull { it.looksLikeTv } ?: list.firstOrNull { it.name != it.host } ?: list.first()
        }.sortedWith(compareByDescending<UpnpDevice> { it.looksLikeTv }.thenBy { it.name })
        withContext(Dispatchers.Main) { best.forEach(onFound) }
    }
}
