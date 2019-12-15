package io.github.dector.quark.qr

fun Layer.drawTimingPattern() {
    (0 until size).forEach { i ->
        val isFilled = i.isEven()

        setAndProtect(6, i, isFilled)
        setAndProtect(i, 6, isFilled)
    }
}

private fun Int.isEven() = this % 2 == 0
