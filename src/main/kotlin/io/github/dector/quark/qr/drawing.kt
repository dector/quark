package io.github.dector.quark.qr

import io.github.dector.quark.ErrorCorrectionLevel
import io.github.dector.quark.utils.parseBit
import io.github.dector.quark.utils.withGrid
import kotlin.math.abs
import kotlin.math.max

// Timing pattern

fun Layer.drawTimingPattern() {
    (0 until size).forEach { i ->
        val isFilled = i.isEven()

        setAndProtect(6, i, isFilled)
        setAndProtect(i, 6, isFilled)
    }
}

private fun Int.isEven() = this % 2 == 0

// Finder pattern

fun Layer.drawFinderPattern() {
    drawFinderElement(3, 3)
    drawFinderElement(size - 4, 3)
    drawFinderElement(3, size - 4)
}

private fun Layer.drawFinderElement(centerX: Int, centerY: Int) {
    withGrid(-4..4, -4..4) { dx, dy ->
        val x = centerX + dx
        val y = centerY + dy
        val distance = max(abs(dx), abs(dy))

        val isFilled = distance != 2 && distance != 4
        setAndProtectSafe(x, y, isFilled)
    }
}

// Alignment pattern

fun Layer.drawAlignmentPattern(version: Int) {
    val positions = countAlignmentPatternPositions(version, size)

    (0..positions.lastIndex).forEach { i ->
        // Don't draw on the three finder corners
        (0..positions.lastIndex)
            .filterNot {
                (i == 0 && it == 0) || (i == 0 && it == positions.lastIndex) || (i == positions.lastIndex && it == 0)
            }
            .forEach { drawAlignmentElement(positions[i], positions[it]) }
    }
}

private fun Layer.drawAlignmentElement(centerX: Int, centerY: Int) {
    withGrid(-2..2, -2..2) { dx, dy ->
        val x = centerX + dx
        val y = centerY + dy

        val isFilled = max(abs(dx), abs(dy)) != 1
        setAndProtect(x, y, isFilled)
    }
}

// Returns an ascending list of positions of alignment patterns for this version number.
// Each position is in the range [0,177), and are used on both the x and y axes.
// This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
private fun countAlignmentPatternPositions(version: Int, size: Int): IntArray {
    if (version == 1) return intArrayOf()

    val elementsCount = version / 7 + 2

    val result = IntArray(elementsCount).also {
        it[0] = 6
    }

    val coordinateStep =
        if (version == 32) 26
        else (version * 4 + elementsCount * 2 + 1) / (elementsCount * 2 - 2) * 2

    var coordinate = size - 7
    (result.lastIndex downTo 1).forEach { i ->
        result[i] = coordinate
        coordinate -= coordinateStep
    }

    return result
}

// Version

fun Layer.drawVersionData(version: Int) {
    if (version < 7) return

    // uint18
    val bits = run {
        // Calculate error correction code and pack bits
        // version is uint6, in the range [7, 40]
        var rem = version

        repeat(12) {
            rem = rem shl 1 xor (rem ushr 11) * 0x1F25
        }

        version shl 12 or rem
    }

    check(bits ushr 18 == 0)

    // Draws two copies of the version bits (with its own error correction code),
    // based on this object's version field, iff 7 <= version <= 40.

    (0..17).forEach { i ->
        val bit = bits.parseBit(i)
        val a = size - 11 + i % 3
        val b = i / 3

        setAndProtect(a, b, bit)
        setAndProtect(b, a, bit)
    }
}

// Format bits

fun Layer.drawFormatData(correctionLevel: ErrorCorrectionLevel, mask: Int = 0) {
    // Draws two copies of the format bits (with its own error correction code)
    // based on the given mask and this object's error correction level field.

    // Calculate error correction code and pack bits
    val bits = run {
        val data = correctionLevel.formatBits shl 3 or mask // errCorrLvl is uint2, mask is uint3
        var rem = data

        repeat(10) {
            rem = rem shl 1 xor (rem ushr 9) * 0x537
        }

        data shl 10 or rem xor 0x5412 // uint15
    }

    check(bits ushr 15 == 0)

    // Draw first copy
    (0..14).forEach { i ->
        val (x, y) = when (i) {
            in 0..5 -> 8 to i
            6 -> 8 to 7
            7 -> 8 to 8
            8 -> 7 to 8
            in 9..14 -> (14 - i) to 8
            else -> error("")
        }

        setAndProtect(x, y, isFilled = bits.parseBit(i))
    }

    (0..14).forEach { i ->
        val (x, y) = when (i) {
            in 0..7 -> (size - 1 - i) to 8
            in 8..14 -> 8 to (size - 15 + i)
            else -> error("")
        }

        setAndProtect(x, y, isFilled = bits.parseBit(i))
    }

    setAndProtect(8, size - 8, isFilled = true) // Always filled
}

// Codewords

// Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
// data area of this QR Code. Function modules need to be marked off before this is called.
fun Layer.drawCodewords(data: ByteArray, version: Int) {
    require(data.size == getNumRawDataModules(version) / 8)

    var i = 0 // Bit index into the data

    // Do the funny zigzag scan
    var right = size - 1
    while (right >= 1) {

        // Index of right column in each column pair
        if (right == 6) right = 5
        for (vert in 0 until size) { // Vertical counter
            for (j in 0..1) {
                val x = right - j // Actual x coordinate
                val upward = right + 1 and 2 == 0
                val y = if (upward) size - 1 - vert else vert // Actual y coordinate
                if (!protectionMask[x, y] && i < data.size * 8) {
                    canvas[x, y] = data[i ushr 3].toInt().parseBit(7 - (i and 7))
                    i++
                }
                // If this QR Code has any remainder bits (0 to 7), they were assigned as
                // 0/false/white by the constructor and are left unchanged by this method
            }
        }
        right -= 2
    }
    assert(i == data.size * 8)
}
