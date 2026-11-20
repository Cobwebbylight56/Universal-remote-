package com.universalremote.app.control

import com.universalremote.app.model.Key
import com.universalremote.app.model.Key.*

enum class IrProtocol { NEC, SAMSUNG32, RC5, SIRC12 }

class IrCodeSet(
    val id: String,
    val title: String,
    val protocol: IrProtocol,
    val address: Int,
    val commands: Map<Key, Int>,
)

object IrCodeSets {
    private val vestelRc5: Map<Key, Int> = CodeTables.vestel.mapValues { it.value - 1000 }

    val all: List<IrCodeSet> = listOf(
        IrCodeSet(
            "vestel", "Vestel / Polaroid / Toshiba / JVC / Hitachi / Bush (RC5, guess)",
            IrProtocol.RC5, 0, vestelRc5
        ),
        IrCodeSet("samsung", "Samsung", IrProtocol.SAMSUNG32, 0x07, buildMap {
            put(NUM_1, 0x04); put(NUM_2, 0x05); put(NUM_3, 0x06); put(NUM_4, 0x08); put(NUM_5, 0x09)
            put(NUM_6, 0x0A); put(NUM_7, 0x0C); put(NUM_8, 0x0D); put(NUM_9, 0x0E); put(NUM_0, 0x11)
            put(POWER, 0x02); put(SOURCE, 0x01); put(MUTE, 0x0F); put(HOME, 0x79); put(MENU, 0x1A)
            put(BACK, 0x58); put(EXIT, 0x2D); put(INFO, 0x1F); put(GUIDE, 0x4F)
            put(UP, 0x60); put(DOWN, 0x61); put(LEFT, 0x65); put(RIGHT, 0x62); put(OK, 0x68)
            put(VOL_UP, 0x07); put(VOL_DOWN, 0x0B); put(CH_UP, 0x12); put(CH_DOWN, 0x10)
            put(PLAY, 0x47); put(PAUSE, 0x4A); put(STOP, 0x46); put(REWIND, 0x45); put(FAST_FORWARD, 0x48)
            put(RED, 0x6C); put(GREEN, 0x14); put(YELLOW, 0x15); put(BLUE, 0x16)
        }),
        IrCodeSet("lg", "LG", IrProtocol.NEC, 0x04, buildMap {
            for (n in 0..9) put(Key.digit(n), 0x10 + n)
            put(POWER, 0x08); put(SOURCE, 0x0B); put(MUTE, 0x09); put(HOME, 0x7C); put(MENU, 0x43)
            put(BACK, 0x28); put(EXIT, 0x5B); put(INFO, 0xAA); put(GUIDE, 0xAB)
            put(UP, 0x40); put(DOWN, 0x41); put(LEFT, 0x07); put(RIGHT, 0x06); put(OK, 0x44)
            put(VOL_UP, 0x02); put(VOL_DOWN, 0x03); put(CH_UP, 0x00); put(CH_DOWN, 0x01)
            put(PLAY, 0xB0); put(PAUSE, 0xBA); put(STOP, 0xB1); put(REWIND, 0x8F); put(FAST_FORWARD, 0x8E)
            put(RED, 0x72); put(GREEN, 0x71); put(YELLOW, 0x63); put(BLUE, 0x61)
        }),
        IrCodeSet("sony", "Sony", IrProtocol.SIRC12, 1, buildMap {
            for (n in 1..9) put(Key.digit(n), n - 1)
            put(NUM_0, 9)
            put(POWER, 21); put(SOURCE, 37); put(MUTE, 20); put(HOME, 96); put(EXIT, 99); put(INFO, 58)
            put(UP, 116); put(DOWN, 117); put(LEFT, 52); put(RIGHT, 51); put(OK, 101)
            put(VOL_UP, 18); put(VOL_DOWN, 19); put(CH_UP, 16); put(CH_DOWN, 17)
        }),
        IrCodeSet("philips", "Philips (RC5)", IrProtocol.RC5, 0, buildMap {
            for (n in 0..9) put(Key.digit(n), n)
            put(POWER, 12); put(MUTE, 13); put(VOL_UP, 16); put(VOL_DOWN, 17); put(CH_UP, 32); put(CH_DOWN, 33)
            put(UP, 80); put(DOWN, 81); put(LEFT, 85); put(RIGHT, 86); put(OK, 87); put(MENU, 82)
            put(SOURCE, 56); put(INFO, 15)
            put(RED, 55); put(GREEN, 54); put(YELLOW, 50); put(BLUE, 52)
        }),
    )

    fun byId(id: String): IrCodeSet? = all.firstOrNull { it.id == id }
}

/** Turns protocol + address + command into an on/off microsecond pattern for ConsumerIrManager. */
object IrEncoder {
    class Signal(val frequency: Int, val pattern: IntArray)

    fun encode(protocol: IrProtocol, address: Int, command: Int, toggle: Boolean): Signal = when (protocol) {
        IrProtocol.NEC -> Signal(38000, nec(address, command))
        IrProtocol.SAMSUNG32 -> Signal(38000, samsung32(address, command))
        IrProtocol.RC5 -> Signal(36000, rc5(address, command, toggle))
        IrProtocol.SIRC12 -> Signal(40000, sirc12(address, command))
    }

    private fun bytesLsbFirst(out: MutableList<Int>, bytes: IntArray, mark: Int, zero: Int, one: Int) {
        for (b in bytes) for (i in 0 until 8) {
            out += mark
            out += if ((b shr i) and 1 == 1) one else zero
        }
    }

    fun nec(address: Int, command: Int): IntArray {
        val out = mutableListOf(9000, 4500)
        val bytes = if (address > 0xFF) {
            intArrayOf(address and 0xFF, (address shr 8) and 0xFF, command and 0xFF, command.inv() and 0xFF)
        } else {
            intArrayOf(address and 0xFF, address.inv() and 0xFF, command and 0xFF, command.inv() and 0xFF)
        }
        bytesLsbFirst(out, bytes, 562, 562, 1687)
        out += 562; out += 40000
        return out.toIntArray()
    }

    fun samsung32(address: Int, command: Int): IntArray {
        val out = mutableListOf(4500, 4500)
        val bytes = intArrayOf(address and 0xFF, address and 0xFF, command and 0xFF, command.inv() and 0xFF)
        bytesLsbFirst(out, bytes, 560, 560, 1690)
        out += 560; out += 40000
        return out.toIntArray()
    }

    /** Philips RC5 (with RC5X field bit for commands 64–127). Manchester coded, 889µs half-bits. */
    fun rc5(address: Int, command: Int, toggle: Boolean): IntArray {
        val bits = mutableListOf<Int>()
        bits += 1
        bits += if (command < 64) 1 else 0
        bits += if (toggle) 1 else 0
        for (i in 4 downTo 0) bits += (address shr i) and 1
        for (i in 5 downTo 0) bits += (command shr i) and 1
        // 1 = space then mark, 0 = mark then space
        val levels = mutableListOf<Boolean>()
        for (b in bits) if (b == 1) { levels += false; levels += true } else { levels += true; levels += false }
        val out = mutableListOf<Int>()
        var current = levels.first()
        var run = 0
        for (l in levels) {
            if (l == current) run += 889 else { out += run; current = l; run = 889 }
        }
        out += run
        // Pattern must start with a mark: drop the leading space of the first '1' bit.
        if (!levels.first()) out.removeAt(0)
        // Make sure it ends with a space (gap).
        if (out.size % 2 == 1) out += 90000 else out[out.lastIndex] = out.last() + 90000
        return out.toIntArray()
    }

    /** Sony SIRC 12-bit, sent three times with a 45ms frame period. */
    fun sirc12(device: Int, command: Int): IntArray {
        val frame = mutableListOf(2400, 600)
        for (i in 0 until 7) { frame += if ((command shr i) and 1 == 1) 1200 else 600; frame += 600 }
        for (i in 0 until 5) { frame += if ((device shr i) and 1 == 1) 1200 else 600; frame += 600 }
        val used = frame.sum()
        frame[frame.lastIndex] = frame.last() + maxOf(0, 45000 - used)
        return (frame + frame + frame).toIntArray()
    }
}
