package io.github.dector.quark.qr

import io.nayuki.qrcodegen.QrCode

enum class ErrorCorrectionLevel(
    val errorsTolerancePercent: Int,
    // In the range 0 to 3 (unsigned 2-bit integer).
    val formatBits: Int
) {
    LOW(errorsTolerancePercent = 7, formatBits = 1),
    MEDIUM(errorsTolerancePercent = 15, formatBits = 0),
    QUARTILE(errorsTolerancePercent = 25, formatBits = 3),
    HIGH(errorsTolerancePercent = 30, formatBits = 2)
}

// For migration purposes
private val migrationMapping = mapOf(
    QrCode.Ecc.LOW to ErrorCorrectionLevel.LOW,
    QrCode.Ecc.MEDIUM to ErrorCorrectionLevel.MEDIUM,
    QrCode.Ecc.HIGH to ErrorCorrectionLevel.HIGH,
    QrCode.Ecc.QUARTILE to ErrorCorrectionLevel.QUARTILE
)

fun QrCode.Ecc.neww() = migrationMapping.getValue(this)
fun ErrorCorrectionLevel.old() = migrationMapping.entries.find { it.value == this }!!.key
