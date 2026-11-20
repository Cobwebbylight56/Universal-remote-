package com.universalremote.app.control

import android.content.Context
import android.hardware.ConsumerIrManager
import com.universalremote.app.model.Device
import com.universalremote.app.model.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class IrController(override var device: Device, context: Context) : TvController {
    private val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private var toggle = false

    private val codeSet get() = IrCodeSets.byId(device.irBrand)

    override fun defaultCode(key: Key): String? = codeSet?.commands?.get(key)?.toString()

    private fun parseNumber(s: String): Int {
        val t = s.trim().lowercase()
        return if (t.startsWith("0x")) t.substring(2).toInt(16) else t.toInt()
    }

    override suspend fun sendCode(code: String) {
        val mgr = ir?.takeIf { it.hasIrEmitter() }
            ?: throw IOException("This phone has no IR emitter. Use a Wi-Fi TV type instead.")
        val set = codeSet ?: throw IOException("Choose an IR brand for this TV first.")
        val parts = code.split(',')
        val (address, command) = try {
            if (parts.size == 2) parseNumber(parts[0]) to parseNumber(parts[1])
            else set.address to parseNumber(parts[0])
        } catch (e: NumberFormatException) {
            throw IOException("\"$code\" isn't a valid IR code.")
        }
        toggle = !toggle
        val signal = IrEncoder.encode(set.protocol, address, command, toggle)
        withContext(Dispatchers.IO) { mgr.transmit(signal.frequency, signal.pattern) }
    }

    companion object {
        fun hasEmitter(context: Context): Boolean =
            (context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager)?.hasIrEmitter() == true
    }
}
