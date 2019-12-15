package io.github.dector.quark.qr

data class Layer(
    val canvas: SquareField,
    val protectionMask: SquareField
) {
    init {
        require(canvas.size == protectionMask.size)
    }

    val size = canvas.size
}

operator fun Layer.set(x: Int, y: Int, isFilled: Boolean) {
    canvas[x, y] = isFilled
}

fun Layer.setAndProtect(x: Int, y: Int, isFilled: Boolean) {
    set(x, y, isFilled)
    protect(x, y)
}

fun Layer.protect(x: Int, y: Int) {
    protectionMask[x, y] = true
}
