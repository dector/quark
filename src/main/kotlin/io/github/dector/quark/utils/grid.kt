package io.github.dector.quark.utils

fun withGrid(xRange: IntRange, yRange: IntRange, receiver: (x: Int, y: Int) -> Unit) {
    xRange.forEach { x ->
        yRange.forEach { y ->
            receiver(x, y)
        }
    }
}
