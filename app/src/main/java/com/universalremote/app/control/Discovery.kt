package com.universalremote.app.control

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.universalremote.app.model.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

data class FoundTv(val host: String, val type: TvType, val port: Int?, val name: String)

object Discovery {
    /** Port → TV type. A TV is reported once per type, using the first port that answers. */
    private val probes = listOf(
        56789 to TvType.VESTEL,
        8060 to TvType.ROKU,
        8002 to TvType.SAMSUNG,
        8001 to TvType.SAMSUNG,
        3001 to TvType.LG,
        3000 to TvType.LG,
    )

    fun localIpv4(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    /** Scans the phone's /24 subnet for known TV ports, plus SSDP for Sony. Calls [onFound] on the main thread. */
    suspend fun scan(context: Context, onFound: (FoundTv) -> Unit) = coroutineScope {
        val self = localIpv4(context) ?: throw IOException("Connect your phone to the same Wi-Fi as the TV first.")
        val prefix = self.substringBeforeLast('.')
        val seen = ConcurrentHashMap.newKeySet<String>()
        val report: suspend (FoundTv) -> Unit = { tv ->
            if (seen.add("${tv.host}/${tv.type}")) withContext(Dispatchers.Main) { onFound(tv) }
        }

        launch(Dispatchers.IO) { runCatching { ssdpSony(context, report) } }

        val sem = Semaphore(64)
        (1..254).map { i ->
            launch(Dispatchers.IO) {
                val host = "$prefix.$i"
                if (host == self) return@launch
                sem.withPermit {
                    val open = probes.map { (port, type) ->
                        async { if (isOpen(host, port)) port to type else null }
                    }.awaitAll().filterNotNull()
                    for ((port, type) in open) {
                        if ("$host/$type" in seen) continue
                        report(FoundTv(host, type, null, nameFor(host, type) ?: defaultName(type, host)))
                    }
                }
            }
        }.forEach { it.join() }
    }

    private fun defaultName(type: TvType, host: String) = when (type) {
        TvType.VESTEL -> "Vestel-based TV ($host)"
        TvType.ROKU -> "Roku ($host)"
        TvType.SAMSUNG -> "Samsung TV ($host)"
        TvType.LG -> "LG TV ($host)"
        TvType.SONY -> "Sony Bravia ($host)"
        TvType.IR -> host
    }

    private fun isOpen(host: String, port: Int, timeoutMs: Int = 400): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
    } catch (e: Exception) {
        false
    }

    private fun get(url: String): String? = runCatching {
        Net.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    private fun nameFor(host: String, type: TvType): String? = when (type) {
        TvType.ROKU -> get("http://$host:8060/query/device-info")?.let { xml ->
            listOf("user-device-name", "friendly-device-name", "model-name").firstNotNullOfOrNull { tag ->
                Regex("<$tag>([^<]+)</$tag>").find(xml)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            }
        }
        TvType.SAMSUNG -> get("http://$host:8001/api/v2/")?.let { body ->
            runCatching { JSONObject(body).optJSONObject("device")?.optString("name") }.getOrNull()
                ?.replace("&quot;", "\"")?.takeIf { it.isNotBlank() }
        }
        else -> null
    }

    private suspend fun ssdpSony(context: Context, report: suspend (FoundTv) -> Unit) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("ssdp")?.apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\nST: urn:schemas-sony-com:service:IRCC:1\r\n\r\n").toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) { socket.send(DatagramPacket(msg, msg.size, group, 1900)) }
                val deadline = System.currentTimeMillis() + 3500
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length)
                    if (text.contains("IRCC", ignoreCase = true)) {
                        val host = packet.address.hostAddress ?: continue
                        report(FoundTv(host, TvType.SONY, null, "Sony Bravia ($host)"))
                    }
                }
            }
        } finally {
            lock?.release()
        }
    }
}

object WakeOnLan {
    suspend fun send(context: Context, mac: String) = withContext(Dispatchers.IO) {
        val hex = mac.replace(Regex("[^0-9A-Fa-f]"), "")
        if (hex.length != 12) throw IOException("MAC address should look like AA:BB:CC:DD:EE:FF")
        val macBytes = ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (i in 0 until 16) System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
        val targets = mutableListOf("255.255.255.255")
        Discovery.localIpv4(context)?.let { targets += it.substringBeforeLast('.') + ".255" }
        DatagramSocket().use { s ->
            s.broadcast = true
            for (t in targets) for (port in listOf(9, 7)) {
                runCatching { s.send(DatagramPacket(packet, packet.size, InetAddress.getByName(t), port)) }
            }
        }
    }
}
