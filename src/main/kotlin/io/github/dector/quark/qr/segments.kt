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

import io.nayuki.qrcodegen.QrSegment
import java.nio.charset.StandardCharsets

/**
 * Returns a list of zero or more segments to represent the specified Unicode text string.
 * The result may use various segment modes and switch modes to optimize the length of the bit stream.
 *
 * @param text the text to be encoded, which can be any Unicode string
 *
 * @return a new list of segments containing the text
 */
fun makeSegments(text: String): List<QrSegment> = when {
    // Select the most efficient segment encoding automatically
    text.isEmpty() -> emptyList()
    text.isNumeric() -> QrSegment.makeNumeric(text).inList()
    text.isAlphanumeric() -> QrSegment.makeAlphanumeric(text).inList()
    else -> QrSegment.makeBytes(text.toByteArray(StandardCharsets.UTF_8)).inList()
}

private fun String.isNumeric() = QrSegment.NUMERIC_REGEX.matcher(this).matches()
private fun String.isAlphanumeric() = QrSegment.ALPHANUMERIC_REGEX.matcher(this).matches()

private fun QrSegment.inList() = listOf(this)
