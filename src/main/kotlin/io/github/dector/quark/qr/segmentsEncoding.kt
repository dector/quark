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

import io.github.dector.quark.qr.Constants.ECC_CODEWORDS_PER_BLOCK
import io.github.dector.quark.qr.Constants.NUM_ERROR_CORRECTION_BLOCKS
import kotlin.math.min

/**
 * Returns a QR Code representing the specified segments at the specified error correction
 * level. The smallest possible QR Code version is automatically chosen for the output. The ECC level
 * of the result may be higher than the ecl argument if it can be done without increasing the version.
 *
 * This function allows the user to create a custom sequence of segments that switches
 * between modes (such as alphanumeric and byte) to encode text in less space.
 *
 * This is a mid-level API; the high-level API is [QrEncoder.encodeText] and [QrEncoder.encodeBinary].
 *
 * @param segments the segments to encode
 * @param correctionLevel the error correction level to use (boostable)
 *
 * @return a QR Code representing the segments
 *
 * @throws DataTooLongException if the segments fail to fit in the largest version QR Code at the ECL, which means they are too long
 */
fun encodeSegments(segments: List<QrSegment>, correctionLevel: ErrorCorrectionLevel): QrCode {
    return encodeSegments(segments, correctionLevel, QrConstants.MIN_VERSION, QrConstants.MAX_VERSION, -1, true)
}

/**
 * Returns a QR Code representing the specified segments with the specified encoding parameters.
 * The smallest possible QR Code version within the specified range is automatically
 * chosen for the output. Iff boostEcl is `true`, then the ECC level of the
 * result may be higher than the ecl argument if it can be done without increasing
 * the version. The mask number is either between 0 to 7 (inclusive) to force that
 * mask, or -1 to automatically choose an appropriate mask (which may be slow).
 *
 * This function allows the user to create a custom sequence of segments that switches
 * between modes (such as alphanumeric and byte) to encode text in less space.
 *
 * This is a mid-level API; the high-level API is [QrEncoder.encodeText] and [QrEncoder.encodeBinary].
 *
 * @param segments the segments to encode
 * @param correctionLevel the error correction level to use  (boostable)
 * @param minVersion the minimum allowed version of the QR Code (at least 1)
 * @param maxVersion the maximum allowed version of the QR Code (at most 40)
 * @param mask the mask number to use (between 0 and 7 (inclusive)), or -1 for automatic mask
 * @param boostEcl increases the ECC level as long as it doesn't increase the version number
 *
 * @return a QR Code representing the segments
 *
 * @throws IllegalArgumentException if 1 &#x2264; minVersion &#x2264; maxVersion &#x2264; 40 or &#x2212;1 &#x2264; mask &#x2264; 7 is violated
 * @throws DataTooLongException if the segments fail to fit in the maxVersion QR Code at the ECL, which means they are too long
 */
fun encodeSegments(segments: List<QrSegment>, correctionLevel: ErrorCorrectionLevel, minVersion: Int, maxVersion: Int, mask: Int, boostEcl: Boolean): QrCode {
    require(minVersion in QrConstants.MIN_VERSION..maxVersion)
    require(maxVersion in minVersion..QrConstants.MAX_VERSION)
    require(mask in -1..7)

    var ecl = correctionLevel

    // Find the minimal version number to use
    val version = calculateVersion(segments, minVersion, maxVersion, ecl)
    val dataUsedBits = getTotalBits(segments, version)

    // Increase the error correction level while the data still fits in the current version number
    if (boostEcl) {
        val newEcl = ErrorCorrectionLevel.values()
            .sortedBy { it.errorsTolerancePercent }
            .dropWhile { it != ecl }
            .lastOrNull { dataUsedBits <= getNumDataCodewords(version, it) * 8 }

        if (newEcl != null) {
            ecl = newEcl
        }
    }

    // Concatenate all segments to create the data bit string
    val bb = BitBuffer().apply {
        segments.forEach { segment ->
            appendBits(segment.mode.modeBits, 4)
            appendBits(segment.numChars, segment.mode.numCharCountBits(version))
            appendData(segment.copyData())
        }
    }
    assert(bb.bitLength() == dataUsedBits)

    // Add terminator and pad up to a byte if applicable
    val dataCapacityBits = getNumDataCodewords(version, ecl) * 8
    assert(bb.bitLength() <= dataCapacityBits)

    bb.appendBits(0, min(4, dataCapacityBits - bb.bitLength()))
    bb.appendBits(0, (8 - bb.bitLength() % 8) % 8)
    assert(bb.bitLength() % 8 == 0)

    // Pad with alternating bytes until data capacity is reached
    var padByte = 0xEC
    while (bb.bitLength() < dataCapacityBits) {
        bb.appendBits(padByte, 8)
        padByte = padByte xor 0xEC xor 0x11
    }

    // Pack bits into bytes in big endian
    val dataCodewords = ByteArray(bb.bitLength() / 8)
    for (i in 0 until bb.bitLength()) {
        dataCodewords[i ushr 3] = (dataCodewords[i ushr 3].toInt() or bb.getBit(i) shl 7 - (i and 7)).toByte()
    }

    // Create the QR Code object
    return QrCode(version, ecl, dataCodewords, mask)
}

private fun calculateVersion(segments: List<QrSegment>, minVersion: Int, maxVersion: Int, correctionLevel: ErrorCorrectionLevel): Int {
    var version = minVersion
    var dataUsedBits: Int

    while (true) {
        val dataCapacityBits = getNumDataCodewords(version, correctionLevel) * 8 // Number of data bits available
        dataUsedBits = getTotalBits(segments, version)

        if (dataUsedBits != -1 && dataUsedBits <= dataCapacityBits) {
            // This version number is found to be suitable
            break
        }

        version++

        // All versions in the range could not fit the given data
        if (version > maxVersion) {
            val msg = if (dataUsedBits != -1) {
                String.format("Data length = %d bits, Max capacity = %d bits", dataUsedBits, dataCapacityBits)
            } else "Segment too long"
            throw DataTooLongException(msg)
        }
    }
    assert(dataUsedBits != -1)

    return version
}

fun getNumDataCodewords(ver: Int, correctionLevel: ErrorCorrectionLevel): Int =
    (getNumRawDataModules(ver) / 8) -
        (ECC_CODEWORDS_PER_BLOCK[correctionLevel.ordinal][ver] * NUM_ERROR_CORRECTION_BLOCKS[correctionLevel.ordinal][ver])

// Returns the number of data bits that can be stored in a QR Code of the given version number, after
// all function modules are excluded. This includes remainder bits, so it might not be a multiple of 8.
// The result is in the range [208, 29648]. This could be implemented as a 40-entry lookup table.
fun getNumRawDataModules(ver: Int): Int {
    require(!(ver < QrConstants.MIN_VERSION || ver > QrConstants.MAX_VERSION)) { "Version number out of range" }

    val size = ver * 4 + 17
    var result = size * size // Number of modules in the whole QR Code square

    result -= 8 * 8 * 3 // Subtract the three finders with separators
    result -= 15 * 2 + 1 // Subtract the format information and black module
    result -= (size - 16) * 2 // Subtract the timing patterns (excluding finders)

    // The five lines above are equivalent to: int result = (16 * ver + 128) * ver + 64;
    if (ver >= 2) {
        val numAlign = ver / 7 + 2
        result -= (numAlign - 1) * (numAlign - 1) * 25 // Subtract alignment patterns not overlapping with timing patterns
        result -= (numAlign - 2) * 2 * 20 // Subtract alignment patterns that overlap with timing patterns

        // The two lines above are equivalent to: result -= (25 * numAlign - 10) * numAlign - 55;
        if (ver >= 7) result -= 6 * 3 * 2 // Subtract version information
    }

    assert(result in 208..29648)

    return result
}

// Calculates the number of bits needed to encode the given segments at the given version.
// Returns a non-negative number if successful. Otherwise returns -1 if a segment has too
// many characters to fit its length field, or the total bits exceeds Integer.MAX_VALUE.
fun getTotalBits(segments: List<QrSegment>, version: Int): Int {
    var result: Long = 0

    for (seg in segments) {
        val ccbits = seg.mode.numCharCountBits(version)

        if (seg.numChars >= 1 shl ccbits) return -1 // The segment's length doesn't fit the field's bit width

        result += 4L + ccbits + seg.copyData().bitLength()

        if (result > Int.MAX_VALUE) return -1 // The sum will overflow an int type
    }
    return result.toInt()
}

object Constants {

    @JvmField val ECC_CODEWORDS_PER_BLOCK = arrayOf(
        // Version: (note that index 0 is for padding, and is set to an illegal value)
        //           0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
        byteArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),   // Low
        byteArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),  // Medium
        byteArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),  // Quartile
        byteArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30)   // High
    )

    @JvmField val NUM_ERROR_CORRECTION_BLOCKS = arrayOf(
        // Version: (note that index 0 is for padding, and is set to an illegal value)
        //           0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40    Error correction level
        byteArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),               // Low
        byteArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),      // Medium
        byteArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),   // Quartile
        byteArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81)   // High
    )
}
