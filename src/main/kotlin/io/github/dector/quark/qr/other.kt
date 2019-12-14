/*
 * Kotlin QR code generation library based on Java library from Project Nayuki
 * (https://www.nayuki.io/page/qr-code-generator-library)
 *
 * Kotlin translation and refactoring done by dector (https://github.com/dector)
 * for quark project (https://github.com/dector/quark)
 *
 * Original library copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

package io.github.dector.quark.qr

import kotlin.experimental.xor

// Returns true iff the i'th bit of x is set to 1.
fun getBit(x: Int, i: Int): Boolean {
    return x ushr i and 1 != 0
}

// Pushes the given value to the front and drops the last value. A helper function for getPenaltyScore().
fun finderPenaltyAddHistory(currentRunLength: Int, runHistory: IntArray) {
    System.arraycopy(runHistory, 0, runHistory, 1, runHistory.size - 1)
    runHistory[0] = currentRunLength
}

// Returns a Reed-Solomon ECC generator polynomial for the given degree. This could be
// implemented as a lookup table over all possible parameter values, instead of as an algorithm.
fun reedSolomonComputeDivisor(degree: Int): ByteArray {
    require(!(degree < 1 || degree > 255)) { "Degree out of range" }
    // Polynomial coefficients are stored from highest to lowest power, excluding the leading term which is always 1.
    // For example the polynomial x^3 + 255x^2 + 8x + 93 is stored as the uint8 array {255, 8, 93}.
    val result = ByteArray(degree)
    result[degree - 1] = 1 // Start off with the monomial x^0
    // Compute the product polynomial (x - r^0) * (x - r^1) * (x - r^2) * ... * (x - r^{degree-1}),
    // and drop the highest monomial term which is always 1x^degree.
    // Note that r = 0x02, which is a generator element of this field GF(2^8/0x11D).
    var root = 1
    for (i in 0 until degree) { // Multiply the current product by (x - r^i)
        for (j in result.indices) {
            result[j] = reedSolomonMultiply(result[j].toInt() and 0xFF, root).toByte()
            if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
        }
        root = reedSolomonMultiply(root, 0x02)
    }
    return result
}

// Returns the product of the two given field elements modulo GF(2^8/0x11D). The arguments and result
// are unsigned 8-bit integers. This could be implemented as a lookup table of 256*256 entries of uint8.
fun reedSolomonMultiply(x: Int, y: Int): Int {
    assert(x shr 8 == 0 && y shr 8 == 0)
    // Russian peasant multiplication
    var z = 0
    for (i in 7 downTo 0) {
        z = z shl 1 xor (z ushr 7) * 0x11D
        z = z xor (y ushr i and 1) * x
    }
    assert(z ushr 8 == 0)
    return z
}

// Returns the Reed-Solomon error correction codeword for the given data and divisor polynomials.
fun reedSolomonComputeRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
    val result = ByteArray(divisor.size)
    for (b in data) { // Polynomial division
        val factor: Int = b.toInt() xor result[0].toInt() and 0xFF
        System.arraycopy(result, 1, result, 0, result.size - 1)
        result[result.size - 1] = 0
        for (i in result.indices) result[i] = result[i] xor reedSolomonMultiply(divisor[i].toInt() and 0xFF, factor).toByte()
    }
    return result
}
