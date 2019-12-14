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

import io.github.dector.quark.qr.Constants;
import io.github.dector.quark.qr.ErrorCorrectionLevel;
import io.github.dector.quark.qr.OtherKt;
import io.github.dector.quark.qr.QrSegment;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

import static io.github.dector.quark.qr.OtherKt.finderPenaltyAddHistory;
import static io.github.dector.quark.qr.OtherKt.reedSolomonComputeDivisor;
import static io.github.dector.quark.qr.OtherKt.reedSolomonComputeRemainder;
import static io.github.dector.quark.qr.QrConstants.MAX_VERSION;
import static io.github.dector.quark.qr.QrConstants.MIN_VERSION;
import static io.github.dector.quark.qr.QrConstants.PENALTY_N1;
import static io.github.dector.quark.qr.QrConstants.PENALTY_N2;
import static io.github.dector.quark.qr.QrConstants.PENALTY_N3;
import static io.github.dector.quark.qr.QrConstants.PENALTY_N4;
import static io.github.dector.quark.qr.SegmentsEncodingKt.getNumDataCodewords;
import static io.github.dector.quark.qr.SegmentsEncodingKt.getNumRawDataModules;


/**
 * A QR Code symbol, which is a type of two-dimension barcode.
 * Invented by Denso Wave and described in the ISO/IEC 18004 standard.
 * <p>Instances of this class represent an immutable square grid of black and white cells.
 * The class provides static factory functions to create a QR Code from text or binary data.
 * The class covers the QR Code Model 2 specification, supporting all versions (sizes)
 * from 1 to 40, all 4 error correction levels, and 4 character encoding modes.</p>
 * <p>Ways to create a QR Code object:</p>
 * <ul>
 *   <li><p>High level: Take the payload data and call {@link QrCode#encodeText(String, Ecc)}
 *     or {@link QrCode#encodeBinary(byte[], Ecc)}.</p></li>
 *   <li><p>Mid level: Custom-make the list of {@link QrSegment segments}
 *     and call {@link QrCode#encodeSegments(List, Ecc)} or
 *     {@link QrCode#encodeSegments(List, Ecc, int, int, int, boolean)}</p></li>
 *   <li><p>Low level: Custom-make the array of data codeword bytes (including segment headers and
 *     final padding, excluding error correction codewords), supply the appropriate version number,
 *     and call the {@link QrCode#QrCode(int, Ecc, byte[], int) constructor}.</p></li>
 * </ul>
 * <p>(Note that all ways require supplying the desired error correction level.)</p>
 *
 * @see QrSegment
 */
public final class QrCode {

    /*---- Instance fields ----*/

    // Public immutable scalar parameters:

    /**
     * The version number of this QR Code, which is between 1 and 40 (inclusive).
     * This determines the size of this barcode.
     */
    public final int version;

    /**
     * The width and height of this QR Code, measured in modules, between
     * 21 and 177 (inclusive). This is equal to version &#xD7; 4 + 17.
     */
    public final int size;

    /**
     * The error correction level used in this QR Code, which is not {@code null}.
     */
    @NotNull
    public final ErrorCorrectionLevel errorCorrectionLevel;

    /**
     * The index of the mask pattern used in this QR Code, which is between 0 and 7 (inclusive).
     * <p>Even if a QR Code is created with automatic masking requested (mask =
     * &#x2212;1), the resulting object still has a mask value between 0 and 7.
     */
    public final int mask;

    // Private grids of modules/pixels, with dimensions of size*size:

    // The modules of this QR Code (false = white, true = black).
    // Immutable after constructor finishes. Accessed through getModule().
    private boolean[][] modules;

    // Indicates function modules that are not subjected to masking. Discarded when constructor finishes.
    private boolean[][] isFunction;



    /*---- Constructor (low level) ----*/

    /**
     * Constructs a QR Code with the specified version number,
     * error correction level, data codeword bytes, and mask number.
     * <p>This is a low-level API that most users should not use directly. A mid-level
     * API is the {@link #encodeSegments(List, Ecc, int, int, int, boolean)} function.</p>
     *
     * @param ver           the version number to use, which must be in the range 1 to 40 (inclusive)
     * @param ecl           the error correction level to use
     * @param dataCodewords the bytes representing segments to encode (without ECC)
     * @param msk           the mask pattern to use, which is either &#x2212;1 for automatic choice or from 0 to 7 for fixed choice
     * @throws NullPointerException     if the byte array or error correction level is {@code null}
     * @throws IllegalArgumentException if the version or mask value is out of range,
     *                                  or if the data is the wrong length for the specified version and error correction level
     */
    public QrCode(int ver, ErrorCorrectionLevel ecl, byte[] dataCodewords, int msk) {
        // Check arguments and initialize fields
        if (ver < MIN_VERSION || ver > MAX_VERSION)
            throw new IllegalArgumentException("Version value out of range");
        if (msk < -1 || msk > 7)
            throw new IllegalArgumentException("Mask value out of range");
        version = ver;
        size = ver * 4 + 17;
        errorCorrectionLevel = Objects.requireNonNull(ecl);
        Objects.requireNonNull(dataCodewords);
        modules = new boolean[size][size];  // Initially all white
        isFunction = new boolean[size][size];

        // Compute ECC, draw modules, do masking
        drawFunctionPatterns();
        byte[] allCodewords = addEccAndInterleave(dataCodewords);
        drawCodewords(allCodewords);
        this.mask = handleConstructorMasking(msk);
        isFunction = null;
    }



    /*---- Public instance methods ----*/

    /**
     * Returns the color of the module (pixel) at the specified coordinates, which is {@code false}
     * for white or {@code true} for black. The top left corner has the coordinates (x=0, y=0).
     * If the specified coordinates are out of bounds, then {@code false} (white) is returned.
     *
     * @param x the x coordinate, where 0 is the left edge and size&#x2212;1 is the right edge
     * @param y the y coordinate, where 0 is the top edge and size&#x2212;1 is the bottom edge
     * @return {@code true} if the coordinates are in bounds and the module
     * at that location is black, or {@code false} (white) otherwise
     */
    public boolean getModule(int x, int y) {
        return 0 <= x && x < size && 0 <= y && y < size && modules[y][x];
    }


    /**
     * Returns a raster image depicting this QR Code, with the specified module scale and border modules.
     * <p>For example, toImage(scale=10, border=4) means to pad the QR Code with 4 white
     * border modules on all four sides, and use 10&#xD7;10 pixels to represent each module.
     * The resulting image only contains the hex colors 000000 and FFFFFF.
     *
     * @param scale  the side length (measured in pixels, must be positive) of each module
     * @param border the number of border modules to add, which must be non-negative
     * @return a new image representing this QR Code, with padding and scaling
     * @throws IllegalArgumentException if the scale or border is out of range, or if
     *                                  {scale, border, size} cause the image dimensions to exceed Integer.MAX_VALUE
     */
    public BufferedImage toImage(int scale, int border) {
        if (scale <= 0 || border < 0)
            throw new IllegalArgumentException("Value out of range");
        if (border > Integer.MAX_VALUE / 2 || size + border * 2L > Integer.MAX_VALUE / scale)
            throw new IllegalArgumentException("Scale or border too large");

        BufferedImage result = new BufferedImage((size + border * 2) * scale, (size + border * 2) * scale, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                boolean color = getModule(x / scale - border, y / scale - border);
                result.setRGB(x, y, color ? 0x000000 : 0xFFFFFF);
            }
        }
        return result;
    }


    /**
     * Returns a string of SVG code for an image depicting this QR Code, with the specified number
     * of border modules. The string always uses Unix newlines (\n), regardless of the platform.
     *
     * @param border the number of border modules to add, which must be non-negative
     * @return a string representing this QR Code as an SVG XML document
     * @throws IllegalArgumentException if the border is negative
     */
    public String toSvgString(int border) {
        if (border < 0)
            throw new IllegalArgumentException("Border must be non-negative");
        long brd = border;
        StringBuilder sb = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
                .append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n",
                        size + brd * 2))
                .append("\t<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")
                .append("\t<path d=\"");
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (getModule(x, y)) {
                    if (x != 0 || y != 0)
                        sb.append(" ");
                    sb.append(String.format("M%d,%dh1v1h-1z", x + brd, y + brd));
                }
            }
        }
        return sb
                .append("\" fill=\"#000000\"/>\n")
                .append("</svg>\n")
                .toString();
    }



    /*---- Private helper methods for constructor: Drawing function modules ----*/

    // Reads this object's version field, and draws and marks all function modules.
    private void drawFunctionPatterns() {
        // Draw horizontal and vertical timing patterns
        for (int i = 0; i < size; i++) {
            setFunctionModule(6, i, i % 2 == 0);
            setFunctionModule(i, 6, i % 2 == 0);
        }

        // Draw 3 finder patterns (all corners except bottom right; overwrites some timing modules)
        drawFinderPattern(3, 3);
        drawFinderPattern(size - 4, 3);
        drawFinderPattern(3, size - 4);

        // Draw numerous alignment patterns
        int[] alignPatPos = getAlignmentPatternPositions();
        int numAlign = alignPatPos.length;
        for (int i = 0; i < numAlign; i++) {
            for (int j = 0; j < numAlign; j++) {
                // Don't draw on the three finder corners
                if (!(i == 0 && j == 0 || i == 0 && j == numAlign - 1 || i == numAlign - 1 && j == 0))
                    drawAlignmentPattern(alignPatPos[i], alignPatPos[j]);
            }
        }

        // Draw configuration data
        drawFormatBits(0);  // Dummy mask value; overwritten later in the constructor
        drawVersion();
    }


    // Draws two copies of the format bits (with its own error correction code)
    // based on the given mask and this object's error correction level field.
    private void drawFormatBits(int msk) {
        // Calculate error correction code and pack bits
        int data = errorCorrectionLevel.getFormatBits() << 3 | msk;  // errCorrLvl is uint2, mask is uint3
        int rem = data;
        for (int i = 0; i < 10; i++)
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;  // uint15
        assert bits >>> 15 == 0;

        // Draw first copy
        for (int i = 0; i <= 5; i++)
            setFunctionModule(8, i, OtherKt.getBit(bits, i));
        setFunctionModule(8, 7, OtherKt.getBit(bits, 6));
        setFunctionModule(8, 8, OtherKt.getBit(bits, 7));
        setFunctionModule(7, 8, OtherKt.getBit(bits, 8));
        for (int i = 9; i < 15; i++)
            setFunctionModule(14 - i, 8, OtherKt.getBit(bits, i));

        // Draw second copy
        for (int i = 0; i < 8; i++)
            setFunctionModule(size - 1 - i, 8, OtherKt.getBit(bits, i));
        for (int i = 8; i < 15; i++)
            setFunctionModule(8, size - 15 + i, OtherKt.getBit(bits, i));
        setFunctionModule(8, size - 8, true);  // Always black
    }


    // Draws two copies of the version bits (with its own error correction code),
    // based on this object's version field, iff 7 <= version <= 40.
    private void drawVersion() {
        if (version < 7)
            return;

        // Calculate error correction code and pack bits
        int rem = version;  // version is uint6, in the range [7, 40]
        for (int i = 0; i < 12; i++)
            rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = version << 12 | rem;  // uint18
        assert bits >>> 18 == 0;

        // Draw two copies
        for (int i = 0; i < 18; i++) {
            boolean bit = OtherKt.getBit(bits, i);
            int a = size - 11 + i % 3;
            int b = i / 3;
            setFunctionModule(a, b, bit);
            setFunctionModule(b, a, bit);
        }
    }


    // Draws a 9*9 finder pattern including the border separator,
    // with the center module at (x, y). Modules can be out of bounds.
    private void drawFinderPattern(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));  // Chebyshev/infinity norm
                int xx = x + dx, yy = y + dy;
                if (0 <= xx && xx < size && 0 <= yy && yy < size)
                    setFunctionModule(xx, yy, dist != 2 && dist != 4);
            }
        }
    }


    // Draws a 5*5 alignment pattern, with the center module
    // at (x, y). All modules must be in bounds.
    private void drawAlignmentPattern(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++)
                setFunctionModule(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
        }
    }


    // Sets the color of a module and marks it as a function module.
    // Only used by the constructor. Coordinates must be in bounds.
    private void setFunctionModule(int x, int y, boolean isBlack) {
        modules[y][x] = isBlack;
        isFunction[y][x] = true;
    }


    /*---- Private helper methods for constructor: Codewords and masking ----*/

    // Returns a new byte string representing the given data with the appropriate error correction
    // codewords appended to it, based on this object's version and error correction level.
    private byte[] addEccAndInterleave(byte[] data) {
        Objects.requireNonNull(data);
        if (data.length != getNumDataCodewords(version, errorCorrectionLevel))
            throw new IllegalArgumentException();

        // Calculate parameter numbers
        int numBlocks = Constants.NUM_ERROR_CORRECTION_BLOCKS[errorCorrectionLevel.ordinal()][version];
        int blockEccLen = Constants.ECC_CODEWORDS_PER_BLOCK[errorCorrectionLevel.ordinal()][version];
        int rawCodewords = getNumRawDataModules(version) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;

        // Split data into blocks and append ECC to each block
        byte[][] blocks = new byte[numBlocks][];
        byte[] rsDiv = reedSolomonComputeDivisor(blockEccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            byte[] dat = Arrays.copyOfRange(data, k, k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1));
            k += dat.length;
            byte[] block = Arrays.copyOf(dat, shortBlockLen + 1);
            byte[] ecc = reedSolomonComputeRemainder(dat, rsDiv);
            System.arraycopy(ecc, 0, block, block.length - blockEccLen, ecc.length);
            blocks[i] = block;
        }

        // Interleave (not concatenate) the bytes from every block into a single sequence
        byte[] result = new byte[rawCodewords];
        for (int i = 0, k = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < blocks.length; j++) {
                // Skip the padding byte in short blocks
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[k] = blocks[j][i];
                    k++;
                }
            }
        }
        return result;
    }


    // Draws the given sequence of 8-bit codewords (data and error correction) onto the entire
    // data area of this QR Code. Function modules need to be marked off before this is called.
    private void drawCodewords(byte[] data) {
        Objects.requireNonNull(data);
        if (data.length != getNumRawDataModules(version) / 8)
            throw new IllegalArgumentException();

        int i = 0;  // Bit index into the data
        // Do the funny zigzag scan
        for (int right = size - 1; right >= 1; right -= 2) {  // Index of right column in each column pair
            if (right == 6)
                right = 5;
            for (int vert = 0; vert < size; vert++) {  // Vertical counter
                for (int j = 0; j < 2; j++) {
                    int x = right - j;  // Actual x coordinate
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;  // Actual y coordinate
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = OtherKt.getBit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                    // If this QR Code has any remainder bits (0 to 7), they were assigned as
                    // 0/false/white by the constructor and are left unchanged by this method
                }
            }
        }
        assert i == data.length * 8;
    }


    // XORs the codeword modules in this QR Code with the given mask pattern.
    // The function modules must be marked and the codeword bits must be drawn
    // before masking. Due to the arithmetic of XOR, calling applyMask() with
    // the same mask value a second time will undo the mask. A final well-formed
    // QR Code needs exactly one (not zero, two, etc.) mask applied.
    private void applyMask(int msk) {
        if (msk < 0 || msk > 7)
            throw new IllegalArgumentException("Mask value out of range");
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (msk) {
                    case 0:
                        invert = (x + y) % 2 == 0;
                        break;
                    case 1:
                        invert = y % 2 == 0;
                        break;
                    case 2:
                        invert = x % 3 == 0;
                        break;
                    case 3:
                        invert = (x + y) % 3 == 0;
                        break;
                    case 4:
                        invert = (x / 3 + y / 2) % 2 == 0;
                        break;
                    case 5:
                        invert = x * y % 2 + x * y % 3 == 0;
                        break;
                    case 6:
                        invert = (x * y % 2 + x * y % 3) % 2 == 0;
                        break;
                    case 7:
                        invert = ((x + y) % 2 + x * y % 3) % 2 == 0;
                        break;
                    default:
                        throw new AssertionError();
                }
                modules[y][x] ^= invert & !isFunction[y][x];
            }
        }
    }


    // A messy helper function for the constructor. This QR Code must be in an unmasked state when this
    // method is called. The given argument is the requested mask, which is -1 for auto or 0 to 7 for fixed.
    // This method applies and returns the actual mask chosen, from 0 to 7.
    private int handleConstructorMasking(int msk) {
        if (msk == -1) {  // Automatically choose best mask
            int minPenalty = Integer.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                applyMask(i);
                drawFormatBits(i);
                int penalty = getPenaltyScore();
                if (penalty < minPenalty) {
                    msk = i;
                    minPenalty = penalty;
                }
                applyMask(i);  // Undoes the mask due to XOR
            }
        }
        assert 0 <= msk && msk <= 7;
        applyMask(msk);  // Apply the final choice of mask
        drawFormatBits(msk);  // Overwrite old format bits
        return msk;  // The caller shall assign this value to the final-declared field
    }


    // Calculates and returns the penalty score based on state of this QR Code's current modules.
    // This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
    private int getPenaltyScore() {
        int result = 0;

        // Adjacent modules in row having same color, and finder-like patterns
        int[] runHistory = new int[7];
        for (int y = 0; y < size; y++) {
            boolean runColor = false;
            int runX = 0;
            Arrays.fill(runHistory, 0);
            int padRun = size;  // Add white border to initial run
            for (int x = 0; x < size; x++) {
                if (modules[y][x] == runColor) {
                    runX++;
                    if (runX == 5)
                        result += PENALTY_N1;
                    else if (runX > 5)
                        result++;
                } else {
                    finderPenaltyAddHistory(runX + padRun, runHistory);
                    padRun = 0;
                    if (!runColor)
                        result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3;
                    runColor = modules[y][x];
                    runX = 1;
                }
            }
            result += finderPenaltyTerminateAndCount(runColor, runX + padRun, runHistory) * PENALTY_N3;
        }
        // Adjacent modules in column having same color, and finder-like patterns
        for (int x = 0; x < size; x++) {
            boolean runColor = false;
            int runY = 0;
            Arrays.fill(runHistory, 0);
            int padRun = size;  // Add white border to initial run
            for (int y = 0; y < size; y++) {
                if (modules[y][x] == runColor) {
                    runY++;
                    if (runY == 5)
                        result += PENALTY_N1;
                    else if (runY > 5)
                        result++;
                } else {
                    finderPenaltyAddHistory(runY + padRun, runHistory);
                    padRun = 0;
                    if (!runColor)
                        result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3;
                    runColor = modules[y][x];
                    runY = 1;
                }
            }
            result += finderPenaltyTerminateAndCount(runColor, runY + padRun, runHistory) * PENALTY_N3;
        }

        // 2*2 blocks of modules having same color
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean color = modules[y][x];
                if (color == modules[y][x + 1] &&
                        color == modules[y + 1][x] &&
                        color == modules[y + 1][x + 1])
                    result += PENALTY_N2;
            }
        }

        // Balance of black and white modules
        int black = 0;
        for (boolean[] row : modules) {
            for (boolean color : row) {
                if (color)
                    black++;
            }
        }
        int total = size * size;  // Note that size is odd, so black/total != 1/2
        // Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
        int k = (Math.abs(black * 20 - total * 10) + total - 1) / total - 1;
        result += k * PENALTY_N4;
        return result;
    }



    /*---- Private helper functions ----*/

    // Returns an ascending list of positions of alignment patterns for this version number.
    // Each position is in the range [0,177), and are used on both the x and y axes.
    // This could be implemented as lookup table of 40 variable-length lists of unsigned bytes.
    private int[] getAlignmentPatternPositions() {
        if (version == 1)
            return new int[]{};
        else {
            int numAlign = version / 7 + 2;
            int step;
            if (version == 32)  // Special snowflake
                step = 26;
            else  // step = ceil[(size - 13) / (numAlign*2 - 2)] * 2
                step = (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
            int[] result = new int[numAlign];
            result[0] = 6;
            for (int i = result.length - 1, pos = size - 7; i >= 1; i--, pos -= step)
                result[i] = pos;
            return result;
        }
    }

    // Can only be called immediately after a white run is added, and
    // returns either 0, 1, or 2. A helper function for getPenaltyScore().
    private int finderPenaltyCountPatterns(int[] runHistory) {
        int n = runHistory[1];
        assert n <= size * 3;
        boolean core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n;
        return (core && runHistory[0] >= n * 4 && runHistory[6] >= n ? 1 : 0)
                + (core && runHistory[6] >= n * 4 && runHistory[0] >= n ? 1 : 0);
    }


    // Must be called at the end of a line (row or column) of modules. A helper function for getPenaltyScore().
    private int finderPenaltyTerminateAndCount(boolean currentRunColor, int currentRunLength, int[] runHistory) {
        if (currentRunColor) {  // Terminate black run
            finderPenaltyAddHistory(currentRunLength, runHistory);
            currentRunLength = 0;
        }
        currentRunLength += size;  // Add white border to final run
        finderPenaltyAddHistory(currentRunLength, runHistory);
        return finderPenaltyCountPatterns(runHistory);
    }
}
