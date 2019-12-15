package io.github.dector.quark

object Constants {

    /**
     * The minimum version number  (1) supported in the QR Code Model 2 standard.
     */
    const val MIN_VERSION = 1

    /**
     * The maximum version number (40) supported in the QR Code Model 2 standard.
     */
    const val MAX_VERSION = 40
}

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
