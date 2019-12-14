package io.github.dector.quark.exporters.svg

import io.github.dector.quark.qr.QrCode

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
    fun exportToSvgString(qr: QrCode, border: Int): String {
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
