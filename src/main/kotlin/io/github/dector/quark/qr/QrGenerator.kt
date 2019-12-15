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

import io.github.dector.quark.Constants.MAX_VERSION
import io.github.dector.quark.Constants.MIN_VERSION
import io.github.dector.quark.ErrorCorrectionLevel
import io.github.dector.quark.QrCode
import io.github.dector.quark.utils.parseBit
import java.util.Arrays
import java.util.Objects

class QrGenerator(
    private val config: Config,
    private val requestedMask: Int // TODO make it sealed and move into Config
) {

    init {
        require(config.version in MIN_VERSION..MAX_VERSION) { "Version value out of range" }
        require(requestedMask == -1 || requestedMask in 0..7) { "Mask value out of range" }
    }

    private var selectedMask = requestedMask    // Will be updated later

    private val layer = run {
        val size = config.version * 4 + 17

        Layer(SquareField(size), SquareField(size))
    }

    operator fun invoke(dataCodewords: ByteArray): QrCode {
        fillFunctionPattern(layer, config)

        fillCodewords(layer, config, dataCodewords)
        selectedMask = detectAndFillMask(layer, requestedMask, config.correctionLevel)

        return getGenerated()
    }

    private fun getGenerated(): QrCode =
        object : QrCode {
            private val canvas = layer.canvas.copy()

            override val version = config.version
            override fun get(x: Int, y: Int) = canvas[x, y]
        }

    data class Config(
        val version: Int,
        val correctionLevel: ErrorCorrectionLevel
    )
}

private fun fillFunctionPattern(layer: Layer, config: QrGenerator.Config) {
    layer.drawTimingPattern()
    layer.drawFinderPattern()
    layer.drawAlignmentPattern(config.version)
    layer.drawFormatData(config.correctionLevel)
    layer.drawVersionData(config.version)
}

private fun fillCodewords(layer: Layer, config: QrGenerator.Config, dataCodewords: ByteArray) {
    val allCodewords = addEccAndInterleave(config, dataCodewords)

    val size = layer.size

    // Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
    // data area of this QR Code. Function modules need to be marked off before this is called.
    fun drawCodewords(data: ByteArray) {
        Objects.requireNonNull(data)
        require(data.size == getNumRawDataModules(config.version) / 8)
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
                    if (!layer.protectionMask[x, y] && i < data.size * 8) {
                        layer.canvas[x, y] = data[i ushr 3].toInt().parseBit(7 - (i and 7))
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

    drawCodewords(allCodewords)
}

// Returns a new byte string representing the given data with the appropriate error correction
// codewords appended to it, based on this object's version and error correction level.
private fun addEccAndInterleave(config: QrGenerator.Config, data: ByteArray): ByteArray {
    require(data.size == getNumDataCodewords(config.version, config.correctionLevel))

    // Calculate parameter numbers
    val numBlocks = QrTables.NUM_ERROR_CORRECTION_BLOCKS[config.correctionLevel.ordinal][config.version].toInt()
    val blockEccLen = QrTables.ECC_CODEWORDS_PER_BLOCK[config.correctionLevel.ordinal][config.version].toInt()
    val rawCodewords = getNumRawDataModules(config.version) / 8
    val numShortBlocks = numBlocks - rawCodewords % numBlocks
    val shortBlockLen = rawCodewords / numBlocks

    // Split data into blocks and append ECC to each block
    val blocks = arrayOfNulls<ByteArray>(numBlocks)
    val rsDiv = reedSolomonComputeDivisor(blockEccLen)

    run {
        var i = 0
        var k = 0
        while (i < numBlocks) {
            val dat = Arrays.copyOfRange(data, k, k + shortBlockLen - blockEccLen + if (i < numShortBlocks) 0 else 1)
            k += dat.size
            val block = Arrays.copyOf(dat, shortBlockLen + 1)
            val ecc = reedSolomonComputeRemainder(dat, rsDiv)
            System.arraycopy(ecc, 0, block, block.size - blockEccLen, ecc.size)
            blocks[i] = block
            i++
        }
    }

    // Interleave (not concatenate) the bytes from every block into a single sequence
    val result = ByteArray(rawCodewords)
    var i = 0
    var k = 0
    while (i < blocks[0]!!.size) {
        for (j in blocks.indices) { // Skip the padding byte in short blocks
            if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                result[k] = blocks[j]!![i]
                k++
            }
        }
        i++
    }
    return result
}

// A messy helper function for the constructor. This QR Code must be in an unmasked state when this
// method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
// This method applies and returns the actual mask chosen, from 0 to 7.
private fun detectAndFillMask(layer: Layer, requestedMask: Int, correctionLevel: ErrorCorrectionLevel): Int {
    var mask = requestedMask
    val size = layer.size

    // XORs the codeword modules in this QR Code with the given mask pattern.
    // The function modules must be marked and the codeword bits must be drawn
    // before masking. Due to the arithmetic of XOR, calling applyMask() with
    // the same mask value a second time will undo the mask. A final well-formed
    // QR Code needs exactly one (not zero, two, etc.) mask applied.
    fun applyMask(mask: Int) {
        require(!(mask < 0 || mask > 7)) { "Mask value out of range" }
        for (y in 0 until size) {
            for (x in 0 until size) {
                val invert: Boolean = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    7 -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    else -> throw AssertionError()
                }
                layer.canvas[x, y] = layer.canvas[x, y] xor (invert and !layer.protectionMask[x, y])
            }
        }
    }

    if (mask == -1) { // Automatically choose best mask
        var minPenalty = Int.MAX_VALUE
        for (i in 0..7) {
            applyMask(i)
            drawFormatBits(i, correctionLevel, layer)
            val penalty: Int = getPenaltyScore(layer.canvas)
            if (penalty < minPenalty) {
                mask = i
                minPenalty = penalty
            }
            applyMask(i) // Undoes the mask due to XOR
        }
    }
    assert(mask in 0..7)
    applyMask(mask) // Apply the final choice of mask

    drawFormatBits(mask, correctionLevel, layer) // Overwrite old format bits

    return mask // The caller shall assign this value to the final-declared field
}

// Draws two copies of the format bits (with its own error correction code)
// based on the given mask and this object's error correction level field.
private fun drawFormatBits(mask: Int, correctionLevel: ErrorCorrectionLevel, layer: Layer) {
    val size = layer.size

    // Calculate error correction code and pack bits
    val data = correctionLevel.formatBits shl 3 or mask // errCorrLvl is uint2, mask is uint3
    var rem = data
    for (i in 0..9) rem = rem shl 1 xor (rem ushr 9) * 0x537
    val bits = data shl 10 or rem xor 0x5412 // uint15
    assert(bits ushr 15 == 0)

    // Draw first copy
    for (i in 0..5) layer.setAndProtect(8, i, bits.parseBit(i))
    layer.setAndProtect(8, 7, bits.parseBit(6))
    layer.setAndProtect(8, 8, bits.parseBit(7))
    layer.setAndProtect(7, 8, bits.parseBit(8))
    for (i in 9..14) layer.setAndProtect(14 - i, 8, bits.parseBit(i))

    // Draw second copy
    for (i in 0..7) layer.setAndProtect(size - 1 - i, 8, bits.parseBit(i))
    for (i in 8..14) layer.setAndProtect(8, size - 15 + i, bits.parseBit(i))
    layer.setAndProtect(8, size - 8, true) // Always black
}

// Note that size is odd, so black/total != 1/2
// Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
// Add white border to initial run
// 2*2 blocks of modules having same color
// Balance of black and white modules
// Add white border to initial run
// Adjacent modules in column having same color, and finder-like patterns
// Adjacent modules in row having same color, and finder-like patterns

// Calculates and returns the penalty score based on state of this QR Code's current modules.
// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
private fun getPenaltyScore(canvas: SquareField): Int {
    val size = canvas.size

    // Can only be called immediately after a white run is added, and
    // returns either 0, 1, or 2. A helper function for getPenaltyScore().
    fun finderPenaltyCountPatterns(runHistory: IntArray): Int {
        val n = runHistory[1]
        assert(n <= size * 3)
        val core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n
        return ((if (core && runHistory[0] >= n * 4 && runHistory[6] >= n) 1 else 0)
            + if (core && runHistory[6] >= n * 4 && runHistory[0] >= n) 1 else 0)
    }

    // Must be called at the end of a line (row or column) of modules. A helper function for getPenaltyScore().
    fun finderPenaltyTerminateAndCount(currentRunColor: Boolean, startRunLength: Int, runHistory: IntArray): Int {
        var currentRunLength = startRunLength
        if (currentRunColor) { // Terminate black run
            finderPenaltyAddHistory(currentRunLength, runHistory)
            currentRunLength = 0
        }
        currentRunLength += size // Add white border to final run
        finderPenaltyAddHistory(currentRunLength, runHistory)
        return finderPenaltyCountPatterns(runHistory)
    }

    var result = 0
    // Adjacent modules in row having same color, and finder-like patterns
    val runHistory = IntArray(7)
    for (y in 0 until size) {
        var runColor = false
        var runX = 0
        Arrays.fill(runHistory, 0)
        var padRun = size // Add white border to initial run
        for (x in 0 until size) {
            if (canvas[x, y] == runColor) {
                runX++
                if (runX == 5) result += PENALTY_N1 else if (runX > 5) result++
            } else {
                finderPenaltyAddHistory(runX + padRun, runHistory)
                padRun = 0
                if (!runColor) result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                runColor = canvas[x, y]
                runX = 1
            }
        }
        result += finderPenaltyTerminateAndCount(runColor, runX + padRun, runHistory) * PENALTY_N3
    }

    // Adjacent modules in column having same color, and finder-like patterns
    for (x in 0 until size) {
        var runColor = false
        var runY = 0
        Arrays.fill(runHistory, 0)
        var padRun = size // Add white border to initial run
        for (y in 0 until size) {
            if (canvas[x, y] == runColor) {
                runY++
                if (runY == 5) result += PENALTY_N1 else if (runY > 5) result++
            } else {
                finderPenaltyAddHistory(runY + padRun, runHistory)
                padRun = 0
                if (!runColor) result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                runColor = canvas[x, y]
                runY = 1
            }
        }
        result += finderPenaltyTerminateAndCount(runColor, runY + padRun, runHistory) * PENALTY_N3
    }

    // 2*2 blocks of modules having same color
    for (y in 0 until size - 1) {
        for (x in 0 until size - 1) {
            val color = canvas[x, y]
            if (color == canvas[x + 1, y] && color == canvas[x, y + 1] && color == canvas[x + 1, y + 1]) result += PENALTY_N2
        }
    }

    // Balance of black and white modules
    var black = 0
    (0 until canvas.size).forEach { x ->
        (0 until canvas.size).forEach { y ->
            if (canvas[x, y]) black++
        }
    }
    val total = size * size // Note that size is odd, so black/total != 1/2

    // Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
    val k = (Math.abs(black * 20 - total * 10) + total - 1) / total - 1
    result += k * PENALTY_N4
    return result
}
// Special snowflake
// step = ceil[(size - 13) / (numAlign*2 - 2)] * 2

// For use in getPenaltyScore(), when evaluating which mask is best.
private const val PENALTY_N1 = 3
private const val PENALTY_N2 = 3
private const val PENALTY_N3 = 40
private const val PENALTY_N4 = 10
