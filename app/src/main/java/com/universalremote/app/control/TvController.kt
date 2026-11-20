package com.universalremote.app.control

import android.content.Context
import com.universalremote.app.model.Device
import com.universalremote.app.model.Key
import com.universalremote.app.model.TvType

/** Callbacks a backend uses to talk back to the UI. Both may be called from any thread. */
class ControllerEnv(
    val status: (String) -> Unit,
    val updateDevice: (Device) -> Unit,
)

interface TvController {
    var device: Device

    /** The built-in code for a key, or null if this backend has none. */
    fun defaultCode(key: Key): String?

    fun codeFor(key: Key): String? =
        device.codes[key.name]?.takeIf { it.isNotBlank() } ?: defaultCode(key)

    /** Sends a raw backend-specific code (what the Codes screen edits). */
    suspend fun sendCode(code: String)

    suspend fun send(key: Key) {
        val code = codeFor(key)
            ?: throw UnsupportedOperationException("${key.label} has no code for this TV yet. Add one under Codes.")
        sendCode(code)
    }

    fun close() {}
}

object Controllers {
    fun create(device: Device, context: Context, env: ControllerEnv): TvController = when (device.type) {
        TvType.VESTEL -> VestelController(device)
        TvType.ROKU -> RokuController(device)
        TvType.SAMSUNG -> SamsungController(device, env)
        TvType.LG -> LgController(device, env)
        TvType.SONY -> SonyController(device)
        TvType.IR -> IrController(device, context.applicationContext)
    }

    /** Hint shown under the code fields on the Codes screen. */
    fun codeHint(type: TvType): String = when (type) {
        TvType.VESTEL -> "Numbers like 1012. Use the explorer to step through 1000–1100 and see what each one does."
        TvType.ROKU -> "Roku key names (Home, Select, VolumeUp…) or launch/<channel id>."
        TvType.SAMSUNG -> "Samsung key names (KEY_HOME, KEY_ENTER…) or app:<app id>."
        TvType.LG -> "Button names (HOME, ENTER…), ssap://… API calls, or app:<app id>."
        TvType.SONY -> "IRCC codes such as AAAAAQAAAAEAAAAVAw==."
        TvType.IR -> "Command number (26 or 0x1A), or address,command (7,2)."
    }
}
