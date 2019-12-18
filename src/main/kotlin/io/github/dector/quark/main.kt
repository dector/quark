package io.github.dector.quark

import io.github.dector.quark.ascii.toAscii
import io.github.dector.quark.ascii.toSvg
import io.github.dector.quark.builders.encodeText
import io.github.dector.quark.exporters.image.ImageExporter
import java.io.File
import javax.imageio.ImageIO

fun main() {
    val qrCode = QrCode.encodeText("https://kotlinlang.org", ErrorCorrectionLevel.LOW)

    // Image
    val file = File("out/qr_code.png").also { it.parentFile.mkdirs() }
    ImageIO.write(
        ImageExporter.exportToImage(qrCode, 16, 4),
        "png",
        file
    )

    // Ascii
    println(qrCode.toAscii())

    // Svg
    File("out/qr_code.svg")
        .also { it.parentFile.mkdirs() }
        .writeText(qrCode.toSvg())

    // Self-Check
    val areFilesEquals = File("out/__qr_code.png").readBytes().contentEquals(file.readBytes())
    if (areFilesEquals) println("Files are equals")
    else System.err.println("!!! FILES ARE NOT EQUALS !!!")
}
