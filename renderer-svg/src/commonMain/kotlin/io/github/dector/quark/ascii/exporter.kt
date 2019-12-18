package io.github.dector.quark.ascii

import io.github.dector.quark.QrCode
import io.github.dector.quark.size
import io.github.dector.quark.sizeWithBorder
import io.github.dector.quark.utils.withGrid

fun QrCode.toSvg(border: Int = 4): String {
    val borderCount = if (border >= 0) border else 0

    val widthOrHeight = sizeWithBorder(borderCount)

    val path = buildString {
        withGrid(0 until size, 0 until size) { x, y ->
            if (get(x, y)) {
                if (x != 0 || y != 0) append(" ")

                append("M")
                    .append(x + border)
                    .append(",")
                    .append(y + border)
                    .append("h1v1h-1z")
            }
        }
    }

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
            "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
        <svg xmlns="http://www.w3.org/2000/svg"
            version="1.1"
            viewBox="0 0 $widthOrHeight $widthOrHeight"
            stroke="none">
            
            <rect width="100%" height="100%" fill="#FFFFFF"/>
            <path d="$path" fill="#000000" />
        
        </svg>
        """.trimIndent()
}
