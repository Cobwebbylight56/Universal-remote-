package com.universalremote.app.control

import android.util.Base64
import com.universalremote.app.model.Device
import com.universalremote.app.model.Key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException

/** Samsung Tizen TVs: websocket on 8002 (TLS, token) or 8001 (older models). */
class SamsungController(override var device: Device, private val env: ControllerEnv) : TvController {
    @Volatile private var socket: WebSocket? = null
    private val mutex = Mutex()

    override fun defaultCode(key: Key): String? = CodeTables.samsung[key]

    private suspend fun connect(): WebSocket = mutex.withLock {
        socket ?: run {
            if (device.host.isBlank()) throw IOException("No IP address set for this TV.")
            val ports = device.port?.let { listOf(it) } ?: listOf(8002, 8001)
            var last: Throwable? = null
            for (p in ports) {
                try {
                    val s = open(p)
                    socket = s
                    return@withLock s
                } catch (e: Throwable) {
                    last = e
                }
            }
            throw IOException(last?.message ?: "Couldn't connect to the Samsung TV", last)
        }
    }

    private suspend fun open(port: Int): WebSocket {
        val name = Base64.encodeToString("Universal Remote".toByteArray(), Base64.NO_WRAP)
        val scheme = if (port == 8002) "wss" else "ws"
        var url = "$scheme://${device.host}:$port/api/v2/channels/samsung.remote.control?name=$name"
        if (device.token.isNotBlank()) url += "&token=${device.token}"
        val ready = CompletableDeferred<Unit>()
        env.status("Connecting… if the TV asks, choose Allow.")
        val ws = Net.ws.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val j = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (j.optString("event")) {
                    "ms.channel.connect" -> {
                        val token = j.optJSONObject("data")?.optString("token").orEmpty()
                        if (token.isNotBlank() && token != device.token) {
                            device = device.copy(token = token)
                            env.updateDevice(device)
                        }
                        env.status("")
                        ready.complete(Unit)
                    }
                    "ms.channel.unauthorized" -> ready.completeExceptionally(
                        IOException(
                            "The TV refused the connection. Check Settings › General › External Device Manager " +
                                "› Device Connection Manager and allow this phone."
                        )
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready.completeExceptionally(t)
                if (socket === webSocket) socket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
            }
        })
        try {
            withTimeout(30_000) { ready.await() }
        } catch (e: TimeoutCancellationException) {
            ws.cancel()
            throw IOException("Timed out waiting for the TV to accept the connection.")
        } catch (e: Throwable) {
            ws.cancel()
            throw e
        }
        return ws
    }

    override suspend fun sendCode(code: String) {
        val c = code.trim()
        val msg = if (c.startsWith("app:")) {
            JSONObject().put("method", "ms.channel.emit").put(
                "params", JSONObject()
                    .put("event", "ed.apps.launch")
                    .put("to", "host")
                    .put("data", JSONObject().put("appId", c.removePrefix("app:")).put("action_type", "DEEP_LINK"))
            )
        } else {
            JSONObject().put("method", "ms.remote.control").put(
                "params", JSONObject()
                    .put("Cmd", "Click")
                    .put("DataOfCmd", c)
                    .put("Option", "false")
                    .put("TypeOfRemote", "SendRemoteKey")
            )
        }
        val ws = connect()
        if (!ws.send(msg.toString())) {
            socket = null
            // One retry on a fresh connection.
            if (!connect().send(msg.toString())) throw IOException("Lost connection to the TV.")
        }
    }

    override fun close() {
        socket?.close(1000, null)
        socket = null
    }
}
