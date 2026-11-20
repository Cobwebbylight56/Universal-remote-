package com.universalremote.app.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TvType(val title: String, val description: String) {
    VESTEL(
        "Vestel-based TV (Wi-Fi)",
        "Polaroid, Toshiba, JVC, Hitachi, Bush, Luxor, Finlux, Telefunken, Techwood and other Vestel-made smart TVs"
    ),
    ROKU("Roku TV (Wi-Fi)", "TCL, Hisense, Sharp and other Roku TVs, plus Roku sticks and boxes"),
    SAMSUNG("Samsung Smart TV (Wi-Fi)", "Tizen models, roughly 2016 onwards"),
    LG("LG webOS TV (Wi-Fi)", "LG smart TVs, roughly 2014 onwards"),
    SONY("Sony Bravia (Wi-Fi)", "Turn on IP control and set a Pre-Shared Key on the TV first"),
    IR("Infrared (IR blaster)", "Any TV with an infrared remote, if your phone has an IR emitter");

    val usesNetwork get() = this != IR
}

data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TvType,
    val host: String = "",
    /** null means use the backend's default port(s). */
    val port: Int? = null,
    /** Optional MAC address, used for Wake-on-LAN. */
    val mac: String = "",
    /** Sony Pre-Shared Key. */
    val psk: String = "",
    /** Pairing token saved by Samsung / LG after you accept the prompt on the TV. */
    val token: String = "",
    /** IR code set id (see IrCodeSets). */
    val irBrand: String = "",
    /** Per-button code overrides, keyed by Key.name. */
    val codes: Map<String, String> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type.name); put("host", host)
        port?.let { put("port", it) }
        put("mac", mac); put("psk", psk); put("token", token); put("irBrand", irBrand)
        put("codes", JSONObject(codes as Map<*, *>))
    }

    companion object {
        fun fromJson(j: JSONObject): Device {
            val codes = mutableMapOf<String, String>()
            j.optJSONObject("codes")?.let { c -> c.keys().forEach { k -> codes[k] = c.optString(k) } }
            return Device(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = j.optString("name"),
                type = runCatching { TvType.valueOf(j.optString("type")) }.getOrDefault(TvType.VESTEL),
                host = j.optString("host"),
                port = if (j.has("port")) j.optInt("port") else null,
                mac = j.optString("mac"),
                psk = j.optString("psk"),
                token = j.optString("token"),
                irBrand = j.optString("irBrand"),
                codes = codes,
            )
        }
    }
}

class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)

    fun load(): List<Device> = runCatching {
        val arr = JSONArray(prefs.getString("list", "[]"))
        (0 until arr.length()).map { Device.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun save(devices: List<Device>) {
        val arr = JSONArray()
        devices.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    var lastDeviceId: String?
        get() = prefs.getString("last", null)
        set(v) { prefs.edit().putString("last", v).apply() }
}
