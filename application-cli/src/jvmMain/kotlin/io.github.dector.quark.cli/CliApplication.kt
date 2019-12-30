package io.github.dector.quark.cli

import io.github.dector.quark.ErrorCorrectionLevel
import io.github.dector.quark.QrCode
import io.github.dector.quark.ascii.toAscii
import io.github.dector.quark.builders.encodeText

fun main(args: Array<String>) {
    if (args.isEmpty()) return

    val text = args.joinToString(" ")
    println("Encoding: '$text'\n")

    val qr = QrCode.encodeText(text, ErrorCorrectionLevel.LOW)
    println(qr.toAscii())
}
