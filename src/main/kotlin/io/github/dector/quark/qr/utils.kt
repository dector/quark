package io.github.dector.quark.qr

fun Int.parseBit(index: Int): Boolean =
    ((this ushr index) and 1) != 0
