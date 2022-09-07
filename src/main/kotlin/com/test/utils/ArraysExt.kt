package com.test.utils

/**
 * @author Slava Gornostal
 */
operator fun Array<IntArray>.times(array: Array<IntArray>): Array<IntArray> =
    Array(array.size) { i ->
        IntArray(array[0].size) { j ->
            this[i][j] * array[i][j]
        }
    }

fun Array<IntArray>.sum(): Int = this.sumOf { it.sum() }