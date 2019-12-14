/*
 * QR Code generator library: Kotlin remix
 *
 * Original copyright (c) Project Nayuki. (MIT License)
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

package io.nayuki.qrcodegen;

import io.github.dector.quark.qr.SegmentMode;
import io.nayuki.qrcodegen.advanced.QrSegmentAdvanced;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;


/**
 * A segment of character/binary/control data in a QR Code symbol.
 * Instances of this class are immutable.
 * <p>The mid-level way to create a segment is to take the payload data and call a
 * static factory function such as {@link QrSegment#makeNumeric(String)}. The low-level
 * way to create a segment is to custom-make the bit buffer and call the {@link
 * QrSegment#QrSegment(Mode,int,BitBuffer) constructor} with appropriate values.</p>
 * <p>This segment class imposes no length restrictions, but QR Codes have restrictions.
 * Even in the most favorable conditions, a QR Code can only hold 7089 characters of data.
 * Any segment longer than this is meaningless for the purpose of generating QR Codes.
 * This class can represent kanji mode segments, but provides no help in encoding them
 * - see {@link QrSegmentAdvanced} for full kanji support.</p>
 */
public final class QrSegment {

    /*---- Static factory functions (mid level) ----*/

    /**
     * Returns a segment representing an Extended Channel Interpretation
     * (ECI) designator with the specified assignment value.
     * @param assignVal the ECI assignment number (see the AIM ECI specification)
     * @return a segment (not {@code null}) containing the data
     * @throws IllegalArgumentException if the value is outside the range [0, 10<sup>6</sup>)
     */
    public static QrSegment makeEci(int assignVal) {
        BitBuffer bb = new BitBuffer();
        if (assignVal < 0)
            throw new IllegalArgumentException("ECI assignment value out of range");
        else if (assignVal < (1 << 7))
            bb.appendBits(assignVal, 8);
        else if (assignVal < (1 << 14)) {
            bb.appendBits(2, 2);
            bb.appendBits(assignVal, 14);
        } else if (assignVal < 1_000_000) {
            bb.appendBits(6, 3);
            bb.appendBits(assignVal, 21);
        } else
            throw new IllegalArgumentException("ECI assignment value out of range");
        return new QrSegment(SegmentMode.ECI, 0, bb);
    }



    /*---- Instance fields ----*/

    /** The mode indicator of this segment. Not {@code null}. */
    public final SegmentMode mode;

    /** The length of this segment's unencoded data. Measured in characters for
     * numeric/alphanumeric/kanji mode, bytes for byte mode, and 0 for ECI mode.
     * Always zero or positive. Not the same as the data's bit length. */
    public final int numChars;

    // The data bits of this segment. Not null. Accessed through getData().
    final BitBuffer data;


    /*---- Constructor (low level) ----*/

    /**
     * Constructs a QR Code segment with the specified attributes and data.
     * The character count (numCh) must agree with the mode and the bit buffer length,
     * but the constraint isn't checked. The specified bit buffer is cloned and stored.
     * @param md the mode (not {@code null})
     * @param numCh the data length in characters or bytes, which is non-negative
     * @param data the data bits (not {@code null})
     * @throws NullPointerException if the mode or data is {@code null}
     * @throws IllegalArgumentException if the character count is negative
     */
    public QrSegment(SegmentMode md, int numCh, BitBuffer data) {
        mode = Objects.requireNonNull(md);
        Objects.requireNonNull(data);
        if (numCh < 0)
            throw new IllegalArgumentException("Invalid value");
        numChars = numCh;
        this.data = data.clone();  // Make defensive copy
    }


    /*---- Methods ----*/

    /**
     * Returns the data bits of this segment.
     * @return a new copy of the data bits (not {@code null})
     */
    public BitBuffer getData() {
        return data.clone();  // Make defensive copy
    }


    // Calculates the number of bits needed to encode the given segments at the given version.
    // Returns a non-negative number if successful. Otherwise returns -1 if a segment has too
    // many characters to fit its length field, or the total bits exceeds Integer.MAX_VALUE.
    static int getTotalBits(List<QrSegment> segs, int version) {
        Objects.requireNonNull(segs);
        long result = 0;
        for (QrSegment seg : segs) {
            Objects.requireNonNull(seg);
            int ccbits = seg.mode.numCharCountBits(version);
            if (seg.numChars >= (1 << ccbits))
                return -1;  // The segment's length doesn't fit the field's bit width
            result += 4L + ccbits + seg.data.bitLength();
            if (result > Integer.MAX_VALUE)
                return -1;  // The sum will overflow an int type
        }
        return (int)result;
    }

}
