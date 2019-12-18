package io.github.dector.quark.utils

import java.util.BitSet as JvmBitSet

actual typealias BitSet = JvmBitSet

actual fun BitSet.copy(): BitSet =
    clone() as JvmBitSet
