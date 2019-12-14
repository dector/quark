package io.github.dector.project

import io.github.dector.quark.qr.QrEncoder
import io.github.dector.quark.qr.encodeText
import io.nayuki.qrcodegen.QrCode
import java.io.File
import javax.imageio.ImageIO

fun main() {
    val qrCode = QrEncoder.encodeText("https://kotlinlang.org", QrCode.Ecc.LOW)

    val file = File("out/qr_code.png").also { it.parentFile.mkdirs() }
    ImageIO.write(
        qrCode.toImage(16, 4),
        "png",
        file
    )

    val areFilesEquals = File("out/__qr_code.png").readBytes().contentEquals(file.readBytes())
    if (areFilesEquals) println("Files are equals")
    else System.err.println("!!! FILES ARE NOT EQUALS !!!")
}
