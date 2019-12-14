package io.github.dector.project

import io.nayuki.qrcodegen.QrCode
import javax.imageio.ImageIO

fun main() {
    val qrCode = QrCode.encodeText("https://kotlinlang.org", QrCode.Ecc.LOW)

    ImageIO.write(
        qrCode.toImage(16, 4),
        "png",
        File("out/qr_code.png")
    )
}
