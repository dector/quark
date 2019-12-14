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

package io.github.dector.quark.exporters.svg

import io.github.dector.quark.qr.QrCodeInfo
import io.github.dector.quark.size

object SvgExporter {

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
    fun exportToSvgString(qr: QrCodeInfo, border: Int): String {
        require(border >= 0) { "Border must be non-negative" }
        val brd = border.toLong()
        val sb = StringBuilder()
            .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
            .append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1\$d %1\$d\" stroke=\"none\">\n",
                qr.size + brd * 2))
            .append("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
            .append("\t<path d=\"")
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) {
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
}
