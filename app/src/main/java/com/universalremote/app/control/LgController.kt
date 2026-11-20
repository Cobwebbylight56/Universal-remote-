package com.universalremote.app.control

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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** LG webOS: SSAP over websocket (3001 TLS / 3000), buttons over the pointer-input socket. */
class LgController(override var device: Device, private val env: ControllerEnv) : TvController {
    @Volatile private var main: WebSocket? = null
    @Volatile private var pointer: WebSocket? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val counter = AtomicInteger()
    private val mutex = Mutex()

    override fun defaultCode(key: Key): String? = CodeTables.lg[key]

    private val permissions = listOf(
        "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CLOSE", "TEST_OPEN", "TEST_PROTECTED",
        "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_RECORDING",
        "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER", "CONTROL_MOUSE_AND_KEYBOARD",
        "READ_APP_STATUS", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE",
        "READ_RUNNING_APPS", "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE",
        "READ_COUNTRY_INFO", "READ_SETTINGS", "CONTROL_TV_SCREEN", "CONTROL_TV_STANBY",
        "CONTROL_FAVORITE_GROUP", "CONTROL_USER_INFO", "CHECK_BLUETOOTH_DEVICE", "CONTROL_BLUETOOTH",
        "CONTROL_TIMER_INFO", "STB_INTERNAL_CONNECTION", "CONTROL_RECORDING", "READ_RECORDING_STATE",
        "WRITE_RECORDING_LIST", "READ_RECORDING_LIST", "READ_RECORDING_SCHEDULE", "WRITE_RECORDING_SCHEDULE",
        "READ_STORAGE_DEVICE_LIST", "READ_TV_PROGRAM_INFO", "CONTROL_BOX_CHANNEL", "READ_TV_ACR_AUTH_TOKEN",
        "READ_TV_CONTENT_STATE", "READ_TV_CURRENT_TIME", "ADD_LAUNCHER_CHANNEL", "SET_CHANNEL_SKIP",
        "RELEASE_CHANNEL_SKIP", "CONTROL_CHANNEL_BLOCK", "DELETE_SELECT_CHANNEL", "CONTROL_CHANNEL_GROUP",
        "SCAN_TV_CHANNELS", "CONTROL_TV_POWER", "CONTROL_WOL"
    )

    private fun registerMessage(): String = JSONObject()
        .put("type", "register")
        .put("id", "reg0")
        .put("payload", JSONObject().apply {
            put("forcePairing", false)
            put("pairingType", "PROMPT")
            if (device.token.isNotBlank()) put("client-key", device.token)
            put(
                "manifest", JSONObject()
                    .put("manifestVersion", 1)
                    .put("appVersion", "1.0")
                    .put("permissions", JSONArray(permissions))
            )
        }).toString()

    private suspend fun connect(): WebSocket = mutex.withLock {
        main ?: run {
            if (device.host.isBlank()) throw IOException("No IP address set for this TV.")
            val ports = device.port?.let { listOf(it) } ?: listOf(3001, 3000)
            var last: Throwable? = null
            for (p in ports) {
                try {
                    val s = openMain(p)
                    main = s
                    return@withLock s
                } catch (e: Throwable) {
                    last = e
                }
            }
            throw IOException(last?.message ?: "Couldn't connect to the LG TV", last)
        }
    }

    private fun reset(ws: WebSocket) {
        if (main === ws) {
            main = null
            pointer?.cancel()
            pointer = null
        }
    }

    private suspend fun openMain(port: Int): WebSocket {
        val scheme = if (port == 3001) "wss" else "ws"
        val registered = CompletableDeferred<Unit>()
        env.status("Connecting… accept the prompt on the TV if one appears.")
        val ws = Net.ws.newWebSocket(
            Request.Builder().url("$scheme://${device.host}:$port").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(registerMessage())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val j = runCatching { JSONObject(text) }.getOrNull() ?: return
                    val id = j.optString("id")
                    val type = j.optString("type")
                    when {
                        type == "registered" -> {
                            val key = j.optJSONObject("payload")?.optString("client-key").orEmpty()
                            if (key.isNotBlank() && key != device.token) {
                                device = device.copy(token = key)
                                env.updateDevice(device)
                            }
                            env.status("")
                            registered.complete(Unit)
                        }
                        id == "reg0" && type == "response" ->
                            env.status("Accept the connection request on your TV.")
                        id == "reg0" && type == "error" ->
                            registered.completeExceptionally(IOException(j.optString("error", "TV refused pairing")))
                        else -> pending.remove(id)?.let { d ->
                            if (type == "error") d.completeExceptionally(IOException(j.optString("error")))
                            else d.complete(j.optJSONObject("payload") ?: JSONObject())
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    registered.completeExceptionally(t)
                    reset(webSocket)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    reset(webSocket)
                }
            })
        try {
            withTimeout(60_000) { registered.await() }
        } catch (e: TimeoutCancellationException) {
            ws.cancel()
            throw IOException("Timed out. Accept the prompt on the TV and try again.")
        } catch (e: Throwable) {
            ws.cancel()
            throw e
        }
        return ws
    }

    private suspend fun request(uri: String, payload: JSONObject? = null): JSONObject {
        val ws = connect()
        val id = "r" + counter.incrementAndGet()
        val result = CompletableDeferred<JSONObject>()
        pending[id] = result
        val msg = JSONObject().put("type", "request").put("id", id).put("uri", uri)
        payload?.let { msg.put("payload", it) }
        if (!ws.send(msg.toString())) {
            pending.remove(id)
            reset(ws)
            throw IOException("Lost connection to the TV.")
        }
        return try {
            withTimeout(8_000) { result.await() }
        } catch (e: TimeoutCancellationException) {
            pending.remove(id)
            throw IOException("The TV didn't answer.")
        }
    }

    private suspend fun pointerSocket(): WebSocket {
        pointer?.let { return it }
        val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket").optString("socketPath")
        if (path.isBlank()) throw IOException("TV didn't provide a button socket.")
        val opened = CompletableDeferred<Unit>()
        val ws = Net.ws.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.completeExceptionally(t)
                if (pointer === webSocket) pointer = null
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (pointer === webSocket) pointer = null
            }
        })
        try {
            withTimeout(5_000) { opened.await() }
        } catch (e: TimeoutCancellationException) {
            ws.cancel()
            throw IOException("Couldn't open the TV's button socket.")
        }
        pointer = ws
        return ws
    }

    override suspend fun sendCode(code: String) {
        val c = code.trim()
        when {
            c.startsWith("ssap://") -> request(c)
            c.startsWith("app:") -> request("ssap://system.launcher/launch", JSONObject().put("id", c.removePrefix("app:")))
            else -> {
                val msg = "type:button\nname:$c\n\n"
                if (!pointerSocket().send(msg)) {
                    pointer = null
                    if (!pointerSocket().send(msg)) throw IOException("Lost connection to the TV.")
                }
            }
        }
    }

    override fun close() {
        pointer?.close(1000, null)
        main?.close(1000, null)
        pointer = null
        main = null
    }
}
