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

    /** speed: 1–255 */
    fun setEffect(effectCode: Byte, speed: Int = 100): ByteArray {
        val s = speed.coerceIn(1, 255).toByte()
        return byteArrayOf(0x7E, 0x05, 0x03, effectCode, s, 0x03, 0x00, 0x00, 0xEF.toByte())
    }
}

enum class LedPattern(val displayName: String, val effectCode: Byte) {
    SOLID("Solid", 0x00),
    JUMP_RGB("Jump RGB", 0x80.toByte()),
    JUMP_ALL("Jump All", 0x81.toByte()),
    FADE_RGB("Fade RGB", 0x82.toByte()),
    FADE_ALL("Fade All", 0x83.toByte()),
    CROSSFADE_R("Fade Red", 0x84.toByte()),
    CROSSFADE_G("Fade Green", 0x85.toByte()),
    CROSSFADE_B("Fade Blue", 0x86.toByte()),
    FLASH_RGB("Flash RGB", 0x8C.toByte()),
    FLASH_ALL("Flash All", 0x8D.toByte()),
    STROBE_W("Strobe White", 0x94.toByte()),
}
