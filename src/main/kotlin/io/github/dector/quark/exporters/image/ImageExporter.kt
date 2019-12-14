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

package io.github.dector.quark.exporters.image

import io.github.dector.quark.qr.QrCode
import java.awt.Color
import java.awt.image.BufferedImage

object ImageExporter {

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
    fun exportToImage(qr: QrCode, scale: Int = 8, border: Int): BufferedImage {
        require(scale > 0) { "Scale should be positive" }
        require(border >= 0) { "Border count should be non-negative" }
        require(!(border > Int.MAX_VALUE / 2 || qr.size + border * 2L > Int.MAX_VALUE / scale)) { "Scale or border too large" }

        val image = run {
            val widthOrHeight = (qr.size + border * 2) * scale
            BufferedImage(widthOrHeight, widthOrHeight, BufferedImage.TYPE_BYTE_BINARY)
        }

        run {
            val g = image.createGraphics().apply {
                color = Color.WHITE
                fillRect(0, 0, image.width, image.height)
            }

            (0..qr.size).forEach { i ->
                (0..qr.size).forEach { j ->
                    val x = (i + border) * scale
                    val y = (j + border) * scale
                    val color = if (qr.getModule(i, j)) Color.BLACK else Color.WHITE

                    g.color = color
                    g.fillRect(x, y, scale, scale)
                }
            }
        }

        return image
    }
}
