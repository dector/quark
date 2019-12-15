package io.github.dector.quark.qr

class SquareField(val size: Int) {

    init {
        require(size > 0)
    }

    private val data = Array(size) { BooleanArray(size) }

    operator fun set(x: Int, y: Int, value: Boolean) {
        data[x][y] = value
    }

    operator fun get(x: Int, y: Int): Boolean = data[x][y]

    fun copy(): SquareField = SquareField(size).also { other ->
        (0 until size).forEach { x ->
            (0 until size).forEach { y ->
                other[x, y] = this[x, y]
            }
        }
    }
}
