package com.example.elkbledom.ble

import java.util.UUID

object ELKBledomProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")

    // Fallback UUIDs used by some variants
    val SERVICE_UUID_ALT: UUID = UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID_ALT: UUID = UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb")

    fun powerOn(): ByteArray =
        byteArrayOf(0x7E, 0x04, 0x04, 0xF0.toByte(), 0x00, 0x01, 0xFF.toByte(), 0x00, 0xEF.toByte())

    fun powerOff(): ByteArray =
        byteArrayOf(0x7E, 0x04, 0x04, 0x00, 0x00, 0x00, 0xFF.toByte(), 0x00, 0xEF.toByte())

    fun setColor(r: Int, g: Int, b: Int): ByteArray = byteArrayOf(
        0x7E, 0x07, 0x05, 0x03,
        r.toByte(), g.toByte(), b.toByte(),
        0x10, 0xEF.toByte()
    )

    /** brightness: 0–100 */
    fun setBrightness(brightness: Int): ByteArray {
        val b = brightness.coerceIn(0, 100).toByte()
        return byteArrayOf(0x7E, 0x04, 0x01, b, 0x00, 0x00, 0x00, 0x00, 0xEF.toByte())
    }

    /**
     * Effect command: 7E 05 03 [speed] [mode] 03 00 00 EF
     * Byte 3 = speed (1–255), byte 4 = mode — order is fixed by the firmware.
     */
    fun setEffect(effectCode: Byte, speed: Int = 100): ByteArray {
        val s = speed.coerceIn(1, 255).toByte()
        return byteArrayOf(0x7E, 0x05, 0x03, s, effectCode, 0x03, 0x00, 0x00, 0xEF.toByte())
    }
}

enum class LedPattern(val displayName: String, val effectCode: Byte) {
    SOLID        ("Solid",           0x00),
    JUMP_RGB     ("Jump RGB",        0x85.toByte()),
    JUMP_ALL     ("Jump All",        0x86.toByte()),
    FADE_RGB     ("Fade RGB",        0x87.toByte()),
    FADE_ALL     ("Fade All",        0x88.toByte()),
    CROSSFADE_R  ("Crossfade Red",   0x89.toByte()),
    CROSSFADE_G  ("Crossfade Green", 0x8A.toByte()),
    CROSSFADE_B  ("Crossfade Blue",  0x8B.toByte()),
    CROSSFADE_W  ("Crossfade White", 0x8F.toByte()),
    FLASH_RGB    ("Flash RGB",       0x91.toByte()),
    FLASH_ALL    ("Flash All",       0x92.toByte()),
    STROBE_W     ("Strobe White",    0x97.toByte()),
}
