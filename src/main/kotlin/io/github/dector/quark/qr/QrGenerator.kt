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

import io.github.dector.quark.Constants
import io.github.dector.quark.ErrorCorrectionLevel
import io.github.dector.quark.QrCode
import io.github.dector.quark.utils.parseBit
import java.util.Arrays
import java.util.Objects

class QrGenerator(
    val version: Int,
    val errorCorrectionLevel: ErrorCorrectionLevel,
    val requestedMask: Int,
    val dataCodewords: ByteArray
) {

    init { // Check arguments and initialize fields
        require(version in Constants.MIN_VERSION..Constants.MAX_VERSION) { "Version value out of range" }
        require(requestedMask == -1 || requestedMask in 0..7) { "Mask value out of range" }
    }

    private val size = version * 4 + 17

    private lateinit var selectedMask: Pair<Int, Unit>

    // Private grids of modules/pixels, with dimensions of size*size:
    // The modules of this QR Code (false = white, true = black).
    // Immutable after constructor finishes. Accessed through getModule().
    private val modules: Array<BooleanArray> = Array(size) { BooleanArray(size) } // Initially all white

    // Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
    private val functionModules: Array<BooleanArray>? = Array(size) { BooleanArray(size) }

    operator fun invoke(): QrCode {
        // Compute ECC, draw modules, do masking
        drawFunctionPatterns()
        val allCodewords = addEccAndInterleave(dataCodewords)
        drawCodewords(allCodewords)
        selectedMask = handleConstructorMasking(requestedMask) to Unit

        return getGenerated()
    }

    private fun getGenerated(): QrCode {
        val version = this.version
        val modules = this.modules

        return object : QrCode {
            override val version = version
            override fun get(x: Int, y: Int) = modules[y][x]
        }
    }

    // Reads this object's version field, and draws and marks all function modules.
    private fun drawFunctionPatterns() { // Draw horizontal and vertical timing patterns
        for (i in 0 until size) {
            setFunctionModule(6, i, i % 2 == 0)
            setFunctionModule(i, 6, i % 2 == 0)
        }
        // Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
        drawFinderPattern(3, 3)
        drawFinderPattern(size - 4, 3)
        drawFinderPattern(3, size - 4)
        // Draw numerous alignment patterns
        val alignPatPos = getAlignmentPatternPositions()
        val numAlign = alignPatPos.size
        for (i in 0 until numAlign) {
            for (j in 0 until numAlign) { // Don't draw on the three finder corners
                if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0)) drawAlignmentPattern(alignPatPos[i], alignPatPos[j])
            }
        }
        // Draw configuration data
        drawFormatBits(0) // Dummy mask value; overwritten later in the constructor
        drawVersion()
    }

    // Draws two copies of the format bits (with its own error correction code)
    // based on the given mask and this object's error correction level field.
    private fun drawFormatBits(msk: Int) { // Calculate error correction code and pack bits
        val data = errorCorrectionLevel.formatBits shl 3 or msk // errCorrLvl is uint2, mask is uint3
        var rem = data
        for (i in 0..9) rem = rem shl 1 xor (rem ushr 9) * 0x537
        val bits = data shl 10 or rem xor 0x5412 // uint15
        assert(bits ushr 15 == 0)

        // Draw first copy
        for (i in 0..5) setFunctionModule(8, i, bits.parseBit(i))
        setFunctionModule(8, 7, bits.parseBit(6))
        setFunctionModule(8, 8, bits.parseBit(7))
        setFunctionModule(7, 8, bits.parseBit(8))
        for (i in 9..14) setFunctionModule(14 - i, 8, bits.parseBit(i))

        // Draw second copy
        for (i in 0..7) setFunctionModule(size - 1 - i, 8, bits.parseBit(i))
        for (i in 8..14) setFunctionModule(8, size - 15 + i, bits.parseBit(i))
        setFunctionModule(8, size - 8, true) // Always black
    }

    // Draws two copies of the version bits (with its own error correction code),
    // based on this object's version field, iff 7 <= version <= 40.
    private fun drawVersion() {
        if (version < 7) return

        // Calculate error correction code and pack bits
        var rem = version // version is uint6, in the range [7, 40]
        for (i in 0..11) rem = rem shl 1 xor (rem ushr 11) * 0x1F25
        val bits = version shl 12 or rem // uint18
        assert(bits ushr 18 == 0)

        // Draw two copies
        for (i in 0..17) {
            val bit = bits.parseBit(i)
            val a = size - 11 + i % 3
            val b = i / 3
            setFunctionModule(a, b, bit)
            setFunctionModule(b, a, bit)
        }
    }

    // Draws a 9*9 finder pattern including the border separator,
    // with the center module at (x, y). Modules can be out of bounds.
    private fun drawFinderPattern(x: Int, y: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val dist = Math.max(Math.abs(dx), Math.abs(dy)) // Chebyshev/infinity norm
                val xx = x + dx
                val yy = y + dy
                if (0 <= xx && xx < size && 0 <= yy && yy < size) setFunctionModule(xx, yy, dist != 2 && dist != 4)
            }
        }
    }

    // Draws a 5*5 alignment pattern, with the center module
    // at (x, y). All modules must be in bounds.
    private fun drawAlignmentPattern(x: Int, y: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) setFunctionModule(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1)
        }
    }

    // Sets the color of a module and marks it as a function module.
    // Only used by the constructor. Coordinates must be in bounds.
    private fun setFunctionModule(x: Int, y: Int, isBlack: Boolean) {
        modules[y][x] = isBlack
        functionModules!![y][x] = true
    }

    // Returns a new byte string representing the given data with the appropriate error correction
    // codewords appended to it, based on this object's version and error correction level.
    private fun addEccAndInterleave(data: ByteArray): ByteArray {
        Objects.requireNonNull(data)
        require(data.size == getNumDataCodewords(version, errorCorrectionLevel))

        // Calculate parameter numbers
        val numBlocks = QrTables.NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal][version].toInt()
        val blockEccLen = QrTables.ECC_CODEWORDS_PER_BLOCK[errorCorrectionLevel.ordinal][version].toInt()
        val rawCodewords = getNumRawDataModules(version) / 8
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

    // Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
    // data area of this QR Code. Function modules need to be marked off before this is called.
    private fun drawCodewords(data: ByteArray) {
        Objects.requireNonNull(data)
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
                    if (!functionModules!![y][x] && i < data.size * 8) {
                        modules[y][x] = data[i ushr 3].toInt().parseBit(7 - (i and 7))
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

    // XORs the codeword modules in this QR Code with the given mask pattern.
    // The function modules must be marked and the codeword bits must be drawn
    // before masking. Due to the arithmetic of XOR, calling applyMask() with
    // the same mask value a second time will undo the mask. A final well-formed
    // QR Code needs exactly one (not zero, two, etc.) mask applied.
    private fun applyMask(msk: Int) {
        require(!(msk < 0 || msk > 7)) { "Mask value out of range" }
        for (y in 0 until size) {
            for (x in 0 until size) {
                val invert: Boolean = when (msk) {
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
                modules[y][x] = modules[y][x] xor (invert and !functionModules!![y][x])
            }
        }
    }

    // A messy helper function for the constructor. This QR Code must be in an unmasked state when this
    // method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
    // This method applies and returns the actual mask chosen, from 0 to 7.
    private fun handleConstructorMasking(msk: Int): Int {
        var msk = msk

        if (msk == -1) { // Automatically choose best mask
            var minPenalty = Int.MAX_VALUE
            for (i in 0..7) {
                applyMask(i)
                drawFormatBits(i)
                val penalty: Int = getPenaltyScore()
                if (penalty < minPenalty) {
                    msk = i
                    minPenalty = penalty
                }
                applyMask(i) // Undoes the mask due to XOR
            }
        }
        assert(msk in 0..7)
        applyMask(msk) // Apply the final choice of mask

        drawFormatBits(msk) // Overwrite old format bits

        return msk // The caller shall assign this value to the final-declared field

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
    private fun getPenaltyScore(): Int {
        var result = 0
        // Adjacent modules in row having same color, and finder-like patterns
        val runHistory = IntArray(7)
        for (y in 0 until size) {
            var runColor = false
            var runX = 0
            Arrays.fill(runHistory, 0)
            var padRun = size // Add white border to initial run
            for (x in 0 until size) {
                if (modules[y][x] == runColor) {
                    runX++
                    if (runX == 5) result += PENALTY_N1 else if (runX > 5) result++
                } else {
                    finderPenaltyAddHistory(runX + padRun, runHistory)
                    padRun = 0
                    if (!runColor) result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                    runColor = modules[y][x]
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
                if (modules[y][x] == runColor) {
                    runY++
                    if (runY == 5) result += PENALTY_N1 else if (runY > 5) result++
                } else {
                    finderPenaltyAddHistory(runY + padRun, runHistory)
                    padRun = 0
                    if (!runColor) result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                    runColor = modules[y][x]
                    runY = 1
                }
            }
            result += finderPenaltyTerminateAndCount(runColor, runY + padRun, runHistory) * PENALTY_N3
        }

        // 2*2 blocks of modules having same color
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val color = modules[y][x]
                if (color == modules[y][x + 1] && color == modules[y + 1][x] && color == modules[y + 1][x + 1]) result += PENALTY_N2
            }
        }

        // Balance of black and white modules
        var black = 0
        for (row in modules) {
            for (color in row) {
                if (color) black++
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

    /*---- Private helper functions ----*/

    // Returns an ascending list of positions of alignment patterns for this version number.
    // Each position is in the range [0,177), and are used on both the x and y axes.
    // This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
    private fun getAlignmentPatternPositions(): IntArray = if (version == 1) intArrayOf() else {
        val numAlign = version / 7 + 2
        val step: Int
        step = if (version == 32) // Special snowflake
            26 else  // step = ceil[(size - 13) / (numAlign*2 - 2)] * 2
            (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2
        val result = IntArray(numAlign)
        result[0] = 6
        var i = result.size - 1
        var pos = size - 7
        while (i >= 1) {
            result[i] = pos
            i--
            pos -= step
        }
        result
    }

    // Can only be called immediately after a white run is added, and
    // returns either 0, 1, or 2. A helper function for getPenaltyScore().
    private fun finderPenaltyCountPatterns(runHistory: IntArray): Int {
        val n = runHistory[1]
        assert(n <= size * 3)
        val core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n
        return ((if (core && runHistory[0] >= n * 4 && runHistory[6] >= n) 1 else 0)
            + if (core && runHistory[6] >= n * 4 && runHistory[0] >= n) 1 else 0)
    }

    // Must be called at the end of a line (row or column) of modules. A helper function for getPenaltyScore().
    private fun finderPenaltyTerminateAndCount(currentRunColor: Boolean, currentRunLength: Int, runHistory: IntArray): Int {
        var currentRunLength = currentRunLength
        if (currentRunColor) { // Terminate black run
            finderPenaltyAddHistory(currentRunLength, runHistory)
            currentRunLength = 0
        }
        currentRunLength += size // Add white border to final run
        finderPenaltyAddHistory(currentRunLength, runHistory)
        return finderPenaltyCountPatterns(runHistory)
    }
}

// For use in getPenaltyScore(), when evaluating which mask is best.
private const val PENALTY_N1 = 3
private const val PENALTY_N2 = 3
private const val PENALTY_N3 = 40
private const val PENALTY_N4 = 10
