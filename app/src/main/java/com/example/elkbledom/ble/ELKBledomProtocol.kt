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
}

enum class LedPattern(val displayName: String) {
    SOLID        ("Solid"),
    JUMP_RGB     ("Jump RGB"),
    JUMP_ALL     ("Jump All"),
    FADE_RGB     ("Fade RGB"),
    FADE_ALL     ("Fade All"),
    CROSSFADE_R  ("Crossfade Red"),
    CROSSFADE_GB  ("Crossfade Green Blue"),
    CROSSFADE_BO  ("Crossfade Blue Orange"),
    CROSSFADE_B  ("Crossfade Blue"),
    CROSSFADE_W  ("Crossfade White"),
    FLASH_RGB    ("Flash RGB"),
    FLASH_ALL    ("Flash All"),
    STROBE_W     ("Strobe White"),
}