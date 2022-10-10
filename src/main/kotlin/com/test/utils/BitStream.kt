package com.test.utils

/**
 * @author Anton Kurinnoy
 */
class BitStream(private val data: IntArray) {
    private var position = 0 // bit position

    private var cByte // current byte
            = 0
    private var cByteIndex = 0

    private var bit = 0

    fun bit(): Int {
        cByteIndex = position shr 3
        if (cByteIndex == data.size) return -1
        cByte = data[cByteIndex]
        bit = cByte shr 7 - position % 8 and 1
        position++
        return bit
    }

    // start on byte boundary
    fun restart() {
        if (position and 7 > 0) position += 8 - (position and 7)
    }

    fun getNextNBits(n: Int): Int {
        var r = 0
        for (i in 0 until n) r = r * 2 + bit()
        return r
    }
}