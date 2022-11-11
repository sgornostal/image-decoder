package com.test.utils

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * @author Anton Kurinnoy
 */
class DCT3(private val precision: Int) {
    // inverse dct
    private var components: IntArray = IntArray(64)
    private var zigzag: Array<IntArray> = arrayOf(
        intArrayOf(0, 1, 5, 6, 14, 15, 27, 28),
        intArrayOf(2, 4, 7, 13, 16, 26, 29, 42),
        intArrayOf(3, 8, 12, 17, 25, 30, 41, 43),
        intArrayOf(9, 11, 18, 24, 31, 40, 44, 53),
        intArrayOf(10, 19, 23, 32, 39, 45, 52, 54),
        intArrayOf(20, 22, 33, 38, 46, 51, 55, 60),
        intArrayOf(21, 34, 37, 47, 50, 56, 59, 61),
        intArrayOf(35, 36, 48, 49, 57, 58, 62, 63)
    )

    fun setComponent(index: Int, value: Int) {
        components[index] = value
    }

    fun zigzagRearrange() {
        for (x in 0..7) {
            for (y in 0..7) {
                zigzag[x][y] = components[zigzag[x][y]]
            }
        }
    }

    fun dct3(): Array<IntArray> {
        val matrix = Array(8) { IntArray(8) }
        for (i in 0..7) {
            for (j in 0..7) {
                matrix[i][j] = j
            }
        }

        for (x in 0..7) {
            for (y in 0..7) {
                var s = 0.0
                for (u in 0 until precision) {
                    for (v in 0 until precision) {
                        s += (zigzag[v][u]
                                * (if (u == 0) 1.0f / sqrt(2.0) else 1.0f * cos((2.0 * x + 1.0) * u * Math.PI / 16.0))
                                * (if (v == 0) 1.0f / sqrt(2.0) else 1.0f * cos((2.0 * y + 1.0) * v * Math.PI / 16.0)))
                    }
                }
                matrix[y][x] = Math.floorDiv(s.toInt(), 4)
            }
        }
        return matrix
    }
}