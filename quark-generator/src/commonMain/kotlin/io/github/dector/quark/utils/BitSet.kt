package io.github.dector.quark.utils

expect class BitSet() {

    operator fun get(index: Int): Boolean
    operator fun set(index: Int, value: Boolean)
}

expect fun BitSet.copy(): BitSet
