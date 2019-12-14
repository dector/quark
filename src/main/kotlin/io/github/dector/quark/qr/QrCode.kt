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

import io.github.dector.quark.qr.QrConstants.MAX_VERSION
import io.github.dector.quark.qr.QrConstants.MIN_VERSION
import io.github.dector.quark.qr.QrConstants.PENALTY_N1
import io.github.dector.quark.qr.QrConstants.PENALTY_N2
import io.github.dector.quark.qr.QrConstants.PENALTY_N3
import io.github.dector.quark.qr.QrConstants.PENALTY_N4
import java.awt.image.BufferedImage
import java.util.Arrays
import java.util.Objects

/**
 * A QR Code symbol, which is a type of two-dimension barcode.
 * Invented by Denso Wave and described in the ISO/IEC 18004 standard.
 *
 * Instances of this class represent an immutable square grid of black and white cells.
 * The class provides static factory functions to create a QR Code from text or binary data.
 * The class covers the QR Code Model 2 specification, supporting all versions (sizes)
 * from 1 to 40, all 4 error correction levels, and 4 character encoding modes.
 *
 * Ways to create a QR Code object:
 *
 *  - High level: Take the payload data and call [QrCode.encodeText]
 * or [QrCode.encodeBinary].
 *  - Mid level: Custom-make the list of [segments][QrSegment]
 * and call [QrCode.encodeSegments] or
 * [QrCode.encodeSegments]
 *  - Low level: Custom-make the array of data codeword bytes (including segment headers and
 * final padding, excluding error correction codewords), supply the appropriate version number,
 * and call the [constructor][QrCode.QrCode].
 *
 * (Note that all ways require supplying the desired error correction level.)
 *
 * @see QrSegment
 */
class QrCode(ver: Int, ecl: ErrorCorrectionLevel, dataCodewords: ByteArray, msk: Int) {
    // Public immutable scalar parameters:

    /**
     * The version number of this QR Code, which is between 1 and 40 (inclusive).
     * This determines the size of this barcode.
     */
    val version: Int

    /**
     * The width and height of this QR Code, measured in modules, between
     * 21 and 177 (inclusive). This is equal to version &#xD7; 4 + 17.
     */
    val size: Int

    /**
     * The error correction level used in this QR Code, which is not `null`.
     */
    val errorCorrectionLevel: ErrorCorrectionLevel

    /**
     * The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
     *
     * Even if a QR Code is created with automatic masking requested (mask =
     * &#x2212;1), the resulting object still has a mask value between 0 and 7.
     */
    val mask: Int

    // Private grids of modules/pixels, with dimensions of size*size:
    // The modules of this QR Code (false = white, true = black).
    // Immutable after constructor finishes. Accessed through getModule().
    private val modules: Array<BooleanArray>

    // Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
    private var isFunction: Array<BooleanArray>?

    /**
     * Constructs a QR Code with the specified version number,
     * error correction level, data codeword bytes, and mask number.
     *
     * This is a low-level API that most users should not use directly. A mid-level
     * API is the [.encodeSegments] function.
     *
     * @param ver           the version number to use, which must be in the range 1 to 40 (inclusive)
     * @param ecl           the error correction level to use
     * @param dataCodewords the bytes representing segments to encode (without ECC)
     * @param msk           the mask pattern to use, which is either &#x2212;1 for automatic choice or from 0 to 7 for fixed choice
     * @throws NullPointerException     if the byte array or error correction level is `null`
     * @throws IllegalArgumentException if the version or mask value is out of range,
     * or if the data is the wrong length for the specified version and error correction level
     */
    init { // Check arguments and initialize fields
        require(!(ver < MIN_VERSION || ver > MAX_VERSION)) { "Version value out of range" }
        require(!(msk < -1 || msk > 7)) { "Mask value out of range" }
        version = ver
        size = ver * 4 + 17
        errorCorrectionLevel = ecl
        Objects.requireNonNull(dataCodewords)
        modules = Array(size) { BooleanArray(size) } // Initially all white
        isFunction = Array(size) { BooleanArray(size) }
        // Compute ECC, draw modules, do masking
        drawFunctionPatterns()
        val allCodewords = addEccAndInterleave(dataCodewords)
        drawCodewords(allCodewords)
        mask = handleConstructorMasking(msk)
        isFunction = null
    }

    /**
     * Returns the color of the module (pixel) at the specified coordinates, which is `false`
     * for white or `true` for black. The top left corner has the coordinates (x=0, y=0).
     * If the specified coordinates are out of bounds, then `false` (white) is returned.
     *
     * @param x the x coordinate, where 0 is the left edge and size&#x2212;1 is the right edge
     * @param y the y coordinate, where 0 is the top edge and size&#x2212;1 is the bottom edge
     * @return `true` if the coordinates are in bounds and the module at that location is black, or `false` (white) otherwise
     */
    fun getModule(x: Int, y: Int): Boolean {
        return x in 0 until size && 0 <= y && y < size && modules[y][x]
    }

    /**
     * Returns a raster image depicting this QR Code, with the specified module scale and border modules.
     *
     * For example, toImage(scale=10, border=4) means to pad the QR Code with 4 white
     * border modules on all four sides, and use 10&#xD7;10 pixels to represent each module.
     * The resulting image only contains the hex colors 000000 and FFFFFF.
     *
     * @param scale  the side length (measured in pixels, must be positive) of each module
     * @param border the number of border modules to add, which must be non-negative
     *
     * @return a new image representing this QR Code, with padding and scaling
     *
     * @throws IllegalArgumentException if the scale or border is out of range, or if
     * {scale, border, size} cause the image dimensions to exceed Integer.MAX_VALUE
     */
    fun toImage(scale: Int, border: Int): BufferedImage {
        require(!(scale <= 0 || border < 0)) { "Value out of range" }
        require(!(border > Int.MAX_VALUE / 2 || size + border * 2L > Int.MAX_VALUE / scale)) { "Scale or border too large" }
        val result = BufferedImage((size + border * 2) * scale, (size + border * 2) * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val color = getModule(x / scale - border, y / scale - border)
                result.setRGB(x, y, if (color) 0x000000 else 0xFFFFFF)
            }
        }
        return result
    }

    /**
     * Returns a string of SVG code for an image depicting this QR Code, with the specified number
     * of border modules. The string always uses Unix newlines (\n), regardless of the platform.
     *
     * @param border the number of border modules to add, which must be non-negative
     *
     * @return a string representing this QR Code as an SVG XML document
     *
     * @throws IllegalArgumentException if the border is negative
     */
    fun toSvgString(border: Int): String {
        require(border >= 0) { "Border must be non-negative" }
        val brd = border.toLong()
        val sb = StringBuilder()
            .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
            .append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1\$d %1\$d\" stroke=\"none\">\n",
                size + brd * 2))
            .append("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
            .append("\t<path d=\"")
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (getModule(x, y)) {
                    if (x != 0 || y != 0) sb.append(" ")
                    sb.append(String.format("M%d,%dh1v1h-1z", x + brd, y + brd))
                }
            }
        }
        return sb
            .append("\" fill=\"#000000\"/>\n")
            .append("</svg>\n")
            .toString()
    }

    /*---- Private helper methods for constructor: Drawing function modules ----*/

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
        val alignPatPos = alignmentPatternPositions
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
        for (i in 0..5) setFunctionModule(8, i, getBit(bits, i))
        setFunctionModule(8, 7, getBit(bits, 6))
        setFunctionModule(8, 8, getBit(bits, 7))
        setFunctionModule(7, 8, getBit(bits, 8))
        for (i in 9..14) setFunctionModule(14 - i, 8, getBit(bits, i))

        // Draw second copy
        for (i in 0..7) setFunctionModule(size - 1 - i, 8, getBit(bits, i))
        for (i in 8..14) setFunctionModule(8, size - 15 + i, getBit(bits, i))
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
            val bit = getBit(bits, i)
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
        isFunction!![y][x] = true
    }

    /*---- Private helper methods for constructor: Codewords and masking ----*/

    // Returns a new byte string representing the given data with the appropriate error correction
    // codewords appended to it, based on this object's version and error correction level.
    private fun addEccAndInterleave(data: ByteArray): ByteArray {
        Objects.requireNonNull(data)
        require(data.size == getNumDataCodewords(version, errorCorrectionLevel))

        // Calculate parameter numbers
        val numBlocks = Constants.NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal][version].toInt()
        val blockEccLen = Constants.ECC_CODEWORDS_PER_BLOCK[errorCorrectionLevel.ordinal][version].toInt()
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
                    if (!isFunction!![y][x] && i < data.size * 8) {
                        modules[y][x] = getBit(data[i ushr 3].toInt(), 7 - (i and 7))
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
                modules[y][x] = modules[y][x] xor invert and !isFunction!![y][x]
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
                val penalty = penaltyScore
                if (penalty < minPenalty) {
                    msk = i
                    minPenalty = penalty
                }
                applyMask(i) // Undoes the mask due to XOR
            }
        }
        assert(0 <= msk && msk <= 7)
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
    private val penaltyScore: Int
        get() {
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
    private val alignmentPatternPositions: IntArray
        get() = if (version == 1) intArrayOf() else {
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
