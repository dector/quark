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

package io.github.dector.quark.builders

import io.github.dector.quark.util.BitBuffer
import io.github.dector.quark.qr.QrSegment
import io.github.dector.quark.util.create
import java.util.regex.Pattern
import kotlin.math.min

/**
 * Returns a list of zero or more segments to represent the specified Unicode text string.
 * The result may use various segment modes and switch modes to optimize the length of the bit stream.
 *
 * @param text the text to be encoded, which can be any Unicode string
 *
 * @return a new list of segments containing the text
 */
fun QrSegment.Companion.createWithBestEncoding(text: String): List<QrSegment> = when {
    // Select the most efficient segment encoding automatically
    text.isEmpty() ->
        emptyList()
    text.isNumeric() ->
        QrSegment.makeNumeric(text).inList()
    text.isAlphanumeric() ->
        QrSegment.makeAlphanumeric(text).inList()
    else ->
        QrSegment.makeBytes(text.toByteArray()).inList()
}

/**
 * Returns a segment representing the specified string of decimal digits encoded in numeric mode.
 *
 * @param digits the text, with only digits from 0 to 9 allowed
 * @return a segment containing the text
 *
 * @throws IllegalArgumentException if the string contains non-digit characters
 */
fun QrSegment.Companion.makeNumeric(digits: String): QrSegment {
    require(NUMERIC_REGEX.matcher(digits).matches()) { "String contains non-numeric characters" }

    val buffer = BitBuffer.create()

    var i = 0
    while (i <= digits.lastIndex) {
        // Consume up to 3 digits per iteration
        val n = min(digits.length - i, 3)
        val bits = digits.substring(i, i + n).toInt()
        buffer.appendBits(bits, n * 3 + 1)

        i += n
    }

    return QrSegment(QrSegment.Mode.NUMERIC, digits.length, buffer)
}


/**
 * Returns a segment representing the specified text string encoded in alphanumeric mode.
 * The characters allowed are: 0 to 9, A to Z (uppercase only), space,
 * dollar, percent, asterisk, plus, hyphen, period, slash, colon.
 *
 * @param text the text, with only certain characters allowed
 * @return a segment  containing the text
 *
 * @throws IllegalArgumentException if the string contains non-encodable characters
 */
fun QrSegment.Companion.makeAlphanumeric(text: String): QrSegment {
    require(ALPHANUMERIC_REGEX.matcher(text).matches()) { "String contains unencodable characters in alphanumeric mode" }

    val buffer = BitBuffer.create()

    var i = 0
    while (i <= text.length - 2) {
        // Process groups of 2
        var bits = ALPHANUMERIC_CHARSET.indexOf(text[i]) * 45
        bits += ALPHANUMERIC_CHARSET.indexOf(text[i + 1])
        buffer.appendBits(bits, 11)

        i += 2
    }

    if (i < text.length) {
        // 1 character remaining
        val bits = ALPHANUMERIC_CHARSET.indexOf(text[i])
        buffer.appendBits(bits, 6)
    }

    return QrSegment(QrSegment.Mode.ALPHANUMERIC, text.length, buffer)
}

/**
 * Returns a segment representing the specified binary data
 * encoded in byte mode. All input byte arrays are acceptable.
 *
 * Any text string can be converted to UTF-8 bytes (`s.getBytes(StandardCharsets.UTF_8)`) and encoded as a byte mode segment.
 *
 * @param data the binary data
 * @return a segment containing the data
 */
fun QrSegment.Companion.makeBytes(data: ByteArray): QrSegment {
    val buffer = BitBuffer.create()
    for (byte in data) {
        val bits = byte.toInt() and 0xFF
        buffer.appendBits(bits, 8)
    }
    return QrSegment(QrSegment.Mode.BYTE, data.size, buffer)
}

private fun String.isNumeric() = NUMERIC_REGEX.matcher(this).matches()
private fun String.isAlphanumeric() = ALPHANUMERIC_REGEX.matcher(this).matches()

private fun QrSegment.inList() = listOf(this)

/** Describes precisely all strings that are encodable in numeric mode. To test whether a
 * string `s` is encodable: `boolean ok = NUMERIC_REGEX.matcher(s).matches();`.
 * A string is encodable iff each character is in the range 0 to 9.
 *
 * @see .makeNumeric
 */
private val NUMERIC_REGEX = Pattern.compile("[0-9]*")

/** Describes precisely all strings that are encodable in alphanumeric mode. To test whether a
 * string `s` is encodable: `boolean ok = ALPHANUMERIC_REGEX.matcher(s).matches();`.
 * A string is encodable iff each character is in the following set: 0 to 9, A to Z
 * (uppercase only), space, dollar, percent, asterisk, plus, hyphen, period, slash, colon.
 *
 * @see .makeAlphanumeric
 */
private val ALPHANUMERIC_REGEX = Pattern.compile("[A-Z0-9 $%*+./:-]*")

// The set of all legal characters in alphanumeric mode, where
// each character value maps to the index in the string.
private const val ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
