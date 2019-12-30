package io.github.dector.quark.cli

import io.github.dector.quark.ErrorCorrectionLevel
import io.github.dector.quark.QrCode
import io.github.dector.quark.ascii.toSvg
import io.github.dector.quark.builders.encodeText
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main() {
    val app = { req: Request ->
        val text = req.query("text") ?: ""

        val responseBody = if (text.isNotEmpty()) {
            val qr = QrCode.encodeText(text, ErrorCorrectionLevel.LOW)
            qr.toSvg(includeHeader = false, width = "100%", height="100%")
        } else ""

        Response(Status.OK)
            .header("Content-Type", "text/html; charset=UTF-8")
            .body("""
                <div>
                $responseBody
                </div>""".trimIndent())
    }

    val server = app.asServer(Jetty()).start()
    println("Server started at http://localhost:${server.port()}")
}
