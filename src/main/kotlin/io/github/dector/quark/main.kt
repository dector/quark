package io.github.dector.project

import io.github.dector.quark.qr.QrEncoder
import io.github.dector.quark.qr.encodeText
import io.nayuki.qrcodegen.QrCode
import java.io.File
import javax.imageio.ImageIO

fun main() {
    val qrCode = QrEncoder.encodeText("https://kotlinlang.org", QrCode.Ecc.LOW)

    ImageIO.write(
        qrCode.toImage(16, 4),
        "png",
        File("out/qr_code.png").also { it.parentFile.mkdirs() }
    )
}
