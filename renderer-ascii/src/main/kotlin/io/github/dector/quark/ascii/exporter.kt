package io.github.dector.quark.ascii

import io.github.dector.quark.QrCode
import io.github.dector.quark.size
import io.github.dector.quark.sizeWithBorder

fun QrCode.toAscii(border: Int = 4, filledPixel: Char = '█', emptyPixel: Char = ' '): String {
    val widthOrHeight = sizeWithBorder(border)

    val sb = StringBuilder()

    repeat(border) {
        sb.appendln(" ".repeat(widthOrHeight))
    }

    (0 until size).forEach { y ->
        sb.append(" ".repeat(border))

        (0 until size).forEach { x ->
            val pixel = if (get(x, y)) filledPixel else emptyPixel
            sb.append(pixel)
        }

        sb.appendln(" ".repeat(border))
    }

    repeat(border - 1) {
        sb.appendln(" ".repeat(widthOrHeight))
    }
    sb.append(" ".repeat(widthOrHeight))

    return sb.toString()
}
