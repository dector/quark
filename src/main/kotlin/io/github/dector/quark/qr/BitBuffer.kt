/*
 * Kotlin QR code generation library based on Java library from Project Nayuki
 * (https://www.nayuki.io/page/qr-code-generator-library)
 *
 * Kotlin translation and refactoring done by dector (https://github.com/dector)
 * for quark project (https://github.com/dector/quark)
 *
 * Original library copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

package io.github.dector.quark.qr

import java.util.BitSet

interface BitBuffer {

    val bitLength: Int

    fun getBit(index: Int): Int
    operator fun get(index: Int): Boolean

    fun copy(): BitBuffer

    companion object
}

interface MutableBitBuffer : BitBuffer {

    fun appendBits(value: Int, len: Int)
    fun appendData(bb: BitBuffer)

    override fun copy(): BitBuffer
}

/**
 * An appendable sequence of bits (0s and 1s). Mainly used by [QrSegment].
 */
class RealBitBuffer(
    private val data: BitSet = BitSet(),
    bitLength: Int = 0  // Non-negative
) : MutableBitBuffer, Cloneable {

    /**
     * Returns the length of this sequence, which is a non-negative value.
     *
     * @return the length of this sequence
     */
    override var bitLength: Int = bitLength
        private set

    init {
        require(bitLength >= 0)
    }

    /**
     * Returns the bit at the specified index, yielding 0 or 1.
     *
     * @param index the index to get the bit at
     *
     * @return the bit at the specified index
     *
     * @throws IndexOutOfBoundsException if index &lt; 0 or index &#x2265; bitLength
     */
    override fun getBit(index: Int): Int {
        if (index < 0 || index >= bitLength) throw IndexOutOfBoundsException()
        return if (data[index]) 1 else 0
    }

    /**
     * Appends the specified number of low-order bits of the specified value to this
     * buffer. Requires 0 &#x2264; len &#x2264; 31 and 0 &#x2264; val &lt; 2<sup>len</sup>.
     *
     * @param value the value to append
     * @param len the number of low-order bits in the value to take
     *
     * @throws IllegalArgumentException if the value or number of bits is out of range
     * @throws IllegalStateException if appending the data would make bitLength exceed Integer.MAX_VALUE
     */
    override fun appendBits(value: Int, len: Int) {
        require(!(len < 0 || len > 31 || value ushr len != 0)) { "Value out of range" }
        check(Int.MAX_VALUE - bitLength >= len) { "Maximum length reached" }
        var i = len - 1
        while (i >= 0) {
            // Append bit by bit
            data[bitLength] = getBit(value, i)
            i--
            bitLength++
        }
    }

    /**
     * Appends the content of the specified bit buffer to this buffer.
     *
     * @param bb the bit buffer whose data to append
     *
     * @throws IllegalStateException if appending the data would make bitLength exceed Integer.MAX_VALUE
     */
    override fun appendData(bb: BitBuffer) {
        check(Int.MAX_VALUE - bitLength >= bb.bitLength) { "Maximum length reached" }
        var i = 0
        while (i < bb.bitLength) {
            // Append bit by bit
            data[bitLength] = bb[i]
            i++
            bitLength++
        }
    }

    override fun get(index: Int) = data[index]

    override fun copy(): BitBuffer = clone()

    /**
     * Returns a new copy of this buffer.
     *
     * @return a new copy of this buffer
     */
    public override fun clone(): RealBitBuffer = try {
        RealBitBuffer(
            data = data.clone() as BitSet,
            bitLength = bitLength
        )
    } catch (e: CloneNotSupportedException) {
        throw AssertionError(e)
    }
}

fun BitBuffer.Companion.create(): MutableBitBuffer = RealBitBuffer()
