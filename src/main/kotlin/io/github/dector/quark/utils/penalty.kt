package io.github.dector.quark.utils

import io.github.dector.quark.qr.SquareField
import io.github.dector.quark.qr.finderPenaltyAddHistory
import java.util.Arrays
import kotlin.math.abs

// Note that size is odd, so black/total != 1/2
// Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
// Add white border to initial run
// 2*2 blocks of modules having same color
// Balance of black and white modules
// Add white border to initial run
// Adjacent modules in column having same color, and finder-like patterns
// Adjacent modules in row having same color, and finder-like patterns

// Calculates and returns the penalty score based on state of this QR Code's current modules.
// This is used by the automatic mask choice algorithm to find the mask pattern that yields the lowest score.
fun calculatePenaltyScore(canvas: SquareField): Int {
    val size = canvas.size

    // Can only be called immediately after a white run is added, and
    // returns either 0, 1, or 2. A helper function for getPenaltyScore().
    fun finderPenaltyCountPatterns(runHistory: IntArray): Int {
        val n = runHistory[1]
        assert(n <= size * 3)
        val core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n
        return ((if (core && runHistory[0] >= n * 4 && runHistory[6] >= n) 1 else 0)
            + if (core && runHistory[6] >= n * 4 && runHistory[0] >= n) 1 else 0)
    }

    // Must be called at the end of a line (row or column) of modules. A helper function for getPenaltyScore().
    fun finderPenaltyTerminateAndCount(currentRunColor: Boolean, startRunLength: Int, runHistory: IntArray): Int {
        var currentRunLength = startRunLength
        if (currentRunColor) { // Terminate black run
            finderPenaltyAddHistory(currentRunLength, runHistory)
            currentRunLength = 0
        }
        currentRunLength += size // Add white border to final run
        finderPenaltyAddHistory(currentRunLength, runHistory)
        return finderPenaltyCountPatterns(runHistory)
    }

    var result = 0
    // Adjacent modules in row having same color, and finder-like patterns
    val runHistory = IntArray(7)
    for (y in 0 until size) {
        var runColor = false
        var runX = 0
        Arrays.fill(runHistory, 0)
        var padRun = size // Add white border to initial run
        for (x in 0 until size) {
            if (canvas[x, y] == runColor) {
                runX++
                if (runX == 5) result += PENALTY_N1 else if (runX > 5) result++
            } else {
                finderPenaltyAddHistory(runX + padRun, runHistory)
                padRun = 0
                if (!runColor) result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                runColor = canvas[x, y]
                runX = 1
            }
        }
        result += finderPenaltyTerminateAndCount(runColor, runX + padRun, runHistory) * PENALTY_N3
    }

    // Adjacent modules in column having same color, and finder-like patterns
    for (x in 0 until size) {
        var runColor = false
        var runY = 0
        Arrays.fill(runHistory, 0)
        var padRun = size // Add white border to initial run
        for (y in 0 until size) {
            if (canvas[x, y] == runColor) {
                runY++
                if (runY == 5) result += PENALTY_N1 else if (runY > 5) result++
            } else {
                finderPenaltyAddHistory(runY + padRun, runHistory)
                padRun = 0
                if (!runColor) result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                runColor = canvas[x, y]
                runY = 1
            }
        }
        result += finderPenaltyTerminateAndCount(runColor, runY + padRun, runHistory) * PENALTY_N3
    }

    // 2*2 blocks of modules having same color
    for (y in 0 until size - 1) {
        for (x in 0 until size - 1) {
            val color = canvas[x, y]
            if (color == canvas[x + 1, y] && color == canvas[x, y + 1] && color == canvas[x + 1, y + 1]) result += PENALTY_N2
        }
    }

    // Balance of black and white modules
    var black = 0
    (0 until canvas.size).forEach { x ->
        (0 until canvas.size).forEach { y ->
            if (canvas[x, y]) black++
        }
    }
    val total = size * size // Note that size is odd, so black/total != 1/2

    // Compute the smallest integer k >= 0 such that (45-5k)% <= black/total <= (55+5k)%
    val k = (abs(black * 20 - total * 10) + total - 1) / total - 1
    result += k * PENALTY_N4
    return result
}
// Special snowflake
// step = ceil[(size - 13) / (numAlign*2 - 2)] * 2

// For use in getPenaltyScore(), when evaluating which mask is best.
private const val PENALTY_N1 = 3
private const val PENALTY_N2 = 3
private const val PENALTY_N3 = 40
private const val PENALTY_N4 = 10
