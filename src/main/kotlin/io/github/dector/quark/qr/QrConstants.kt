package io.github.dector.quark.qr

object QrConstants {

    /**
     * The minimum version number  (1) supported in the QR Code Model 2 standard.
     */
    const val MIN_VERSION = 1

    /**
     * The maximum version number (40) supported in the QR Code Model 2 standard.
     */
    const val MAX_VERSION = 40

    // For use in getPenaltyScore(), when evaluating which mask is best.
    const val PENALTY_N1 = 3
    const val PENALTY_N2 = 3
    const val PENALTY_N3 = 40
    const val PENALTY_N4 = 10

}
