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

/**
 * A segment of character/binary/control data in a QR Code symbol.
 * Instances of this class are immutable.
 *
 * The mid-level way to create a segment is to take the payload data and call a
 * static factory function such as [makeNumeric]. The low-level
 * way to create a segment is to custom-make the bit buffer and call the [QrSegment] with appropriate values.
 *
 * This segment class imposes no length restrictions, but QR Codes have restrictions.
 * Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
 * Any segment longer than this is meaningless for the purpose of generating QR Codes.
 * This class can represent kanji mode segments, but provides no help in encoding them
 * - see [io.nayuki.qrcodegen.advanced.QrSegmentAdvanced] for full kanji support.
 */
class QrSegment(
    /**
     * The mode indicator of this segment. Not `null`.
     */
    val mode: Mode,
    /**
     * The length of this segment's unencoded data. Measured in characters for
     * numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
     * Always zero or positive. Not the same as the data's bit length.
     */
    val numChars: Int,
    // The data bits of this segment. Not null. Accessed through copyData().
    val data: BitBuffer
) {

    /**
     * Constructs a QR Code segment with the specified attributes and data.
     * The character count (numCh) must agree with the mode and the bit buffer length,
     * but the constraint isn't checked. The specified bit buffer is cloned and stored.
     *
     * @param md    the mode
     * @param numCh the data length in characters or bytes, which is non-negative
     * @param data  the data bits
     *
     * @throws IllegalArgumentException if the character count is negative
     */
    init {
        require(numChars >= 0)
    }

    /**
     * Returns the data bits of this segment.
     *
     * @return a new copy of the data bits
     */
    fun copyData(): BitBuffer {
        return data.clone() // Make defensive copy
    }

    /**
     * Describes how a segment's data bits are interpreted.
     */
    enum class Mode(val modeBits: Int, private vararg val numBitsCharCount: Int) {
        NUMERIC(0x1, 10, 12, 14),
        ALPHANUMERIC(0x2, 9, 11, 13),
        BYTE(0x4, 8, 16, 16),
        KANJI(0x8, 8, 10, 12),
        ECI(0x7, 0, 0, 0);

        // Returns the bit width of the character count field for a segment in this mode
        // in a QR Code at the given version number. The result is in the range [0, 16].
        fun numCharCountBits(version: Int): Int {
            require(version in QrConstants.MIN_VERSION..QrConstants.MAX_VERSION)

            return numBitsCharCount[(version + 7) / 17]
        }
    }
}
