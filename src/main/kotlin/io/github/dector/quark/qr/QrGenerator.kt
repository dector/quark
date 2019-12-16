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
import io.github.dector.quark.qr.QrTables.ECC_CODEWORDS_PER_BLOCK
import io.github.dector.quark.qr.QrTables.NUM_ERROR_CORRECTION_BLOCKS
import io.github.dector.quark.utils.calculatePenaltyScore

class QrGenerator(
    private val config: Config
) {

    init {
        require(config.version in MIN_VERSION..MAX_VERSION) { "Version value out of range" }
        require(config.requestedMask == -1 || config.requestedMask in 0..7) { "Mask value out of range" }
    }

    private var selectedMask = config.requestedMask    // Will be updated later

    private val layer = run {
        val size = config.version * 4 + 17

        Layer(SquareField(size), SquareField(size))
    }

    operator fun invoke(dataCodewords: ByteArray): QrCode {
        drawServicePatterns(layer, config)

        drawData(layer, config, dataCodewords)
        selectedMask = detectAndDrawMask(layer, config.requestedMask, config.correctionLevel)

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
        val correctionLevel: ErrorCorrectionLevel,
        val requestedMask: Int
    )
}

private fun drawServicePatterns(layer: Layer, config: QrGenerator.Config) {
    layer.drawTimingPattern()
    layer.drawFinderPattern()
    layer.drawAlignmentPattern(config.version)
    layer.drawFormatData(config.correctionLevel)
    layer.drawVersionData(config.version)
}

private fun drawData(layer: Layer, config: QrGenerator.Config, data: ByteArray) {
    val codewords = data.supplementWithErrorCorrection(config.version, config.correctionLevel)

    layer.drawCodewords(codewords, config.version)
}

// Returns a new byte string representing the given data with the appropriate error correction
// codewords appended to it, based on this object's version and error correction level.
private fun ByteArray.supplementWithErrorCorrection(version: Int, correctionLevel: ErrorCorrectionLevel): ByteArray {
    require(size == getNumDataCodewords(version, correctionLevel))

    // Calculate parameter numbers
    val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[correctionLevel.ordinal][version].toInt()
    val blockEccLen = ECC_CODEWORDS_PER_BLOCK[correctionLevel.ordinal][version].toInt()
    val rawCodewords = getNumRawDataModules(version) / 8
    val numShortBlocks = numBlocks - rawCodewords % numBlocks
    val shortBlockLen = rawCodewords / numBlocks

    // Split data into blocks and append ECC to each block
    val blocks = arrayOfNulls<ByteArray>(numBlocks)
    val rsDiv = reedSolomonComputeDivisor(blockEccLen)

    run {
        var k = 0
        (0 until numBlocks).forEach { i ->
            val dat = copyOfRange(k, k + shortBlockLen - blockEccLen + if (i < numShortBlocks) 0 else 1)
            k += dat.size

            val block = dat.copyOf(shortBlockLen + 1)
            val ecc = reedSolomonComputeRemainder(dat, rsDiv)

            System.arraycopy(ecc, 0, block, block.size - blockEccLen, ecc.size)
            blocks[i] = block
        }
    }

    // Interleave (not concatenate) the bytes from every block into a single sequence
    val result = ByteArray(rawCodewords)
    var k = 0
    (blocks[0]!!.indices).forEach { i ->
        for (j in blocks.indices) {
            // Skip the padding byte in short blocks
            if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                result[k] = blocks[j]!![i]
                k++
            }
        }
    }

    return result
}

// A messy helper function for the constructor. This QR Code must be in an unmasked state when this
// method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
// This method applies and returns the actual mask chosen, from 0 to 7.
private fun detectAndDrawMask(layer: Layer, requestedMask: Int, correctionLevel: ErrorCorrectionLevel): Int {
    val mask = if (requestedMask in 0..7) {
        requestedMask
    } else (0..7).map { maskToTry ->
        // Automatically choose best mask
        val newLayer = layer.copy()

        newLayer.applyMask(maskToTry)
        newLayer.drawFormatData(correctionLevel, maskToTry)

        val penalty = calculatePenaltyScore(layer.canvas)

        maskToTry to penalty
    }.minBy { it.second }!!.first

    check(mask in 0..7)

    layer.applyMask(mask)
    layer.drawFormatData(correctionLevel, mask) // Overwrite old format bits

    return mask
}
