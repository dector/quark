package io.github.dector.quark

interface QrCode {

    val version: Int

    operator fun get(x: Int, y: Int): Boolean
}

// [21, 177]
val QrCode.size: Int get() = version * 4 + 17

fun QrCode.sizeWithBorder(border: Int): Int = size + 2 * border
