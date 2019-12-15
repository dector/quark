package io.github.dector.quark.qr

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
    (-4..4).forEach { dx ->
        (-4..4).forEach { dy ->
            val x = centerX + dx
            val y = centerY + dy
            val distance = max(abs(dx), abs(dy))

            val isFilled = distance != 2 && distance != 4
            setAndProtectSafe(x, y, isFilled)
        }
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
    (-2..2).forEach { dx ->
        (-2..2).forEach { dy ->
            val x = centerX + dx
            val y = centerY + dy

            val isFilled = max(abs(dx), abs(dy)) != 1
            setAndProtect(x, y, isFilled)
        }
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
