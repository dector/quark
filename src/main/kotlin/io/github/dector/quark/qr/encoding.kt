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

import io.nayuki.qrcodegen.DataTooLongException
import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrCode.Ecc
import io.nayuki.qrcodegen.QrSegment

// TODO Used as an intermediate entity for extension-based API design. All extensions should be implemented on top of `QrCode.Companion`.
object QrEncoder

/**
 * Returns a QR Code representing the specified Unicode text string at the specified error correction level.
 * As a conservative upper bound, this function is guaranteed to succeed for strings that have 738 or fewer
 * Unicode code points (not UTF-16 code units) if the low error correction level is used. The smallest possible
 * QR Code version is automatically chosen for the output. The ECC level of the result may be higher than the
 * ecl argument if it can be done without increasing the version.
 *
 * @param text the text to be encoded, which can be any Unicode string
 * @param ecl the error correction level to use (boostable)
 *
 * @return a QR Code representing the text
 *
 * @throws DataTooLongException if the text fails to fit in the largest version QR Code at the ECL, which means it is too long
 */
fun QrEncoder.encodeText(text: String, ecl: QrCode.Ecc): QrCode {
    val segments = QrSegment.makeSegments(text)
    return QrCode.encodeSegments(segments, ecl)
}

/**
 * Returns a QR Code representing the specified binary data at the specified error correction level.
 * This function always encodes using the binary segment mode, not any text mode. The maximum number of
 * bytes allowed is 2953. The smallest possible QR Code version is automatically chosen for the output.
 * The ECC level of the result may be higher than the ecl argument if it can be done without increasing the version.
 *
 * @param data the binary data to encode
 * @param ecl  the error correction level to use (boostable)
 *
 * @return a QR Code representing the data
 *
 * @throws DataTooLongException if the data fails to fit in the largest version QR Code at the ECL, which means it is too long
 */
fun QrEncoder.encodeBinary(data: ByteArray, ecl: Ecc): QrCode {
    val seg = QrSegment.makeBytes(data)
    return QrCode.encodeSegments(listOf(seg), ecl)
}
