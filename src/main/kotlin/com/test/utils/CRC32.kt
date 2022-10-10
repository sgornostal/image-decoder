package com.test.utils

/**
 * @author Anton Kurinnoy
 */
object CRC32 {
    val initialValue = 0

    private val TABLE = IntArray(0x100) {
        var c = it
        for (k in 0 until 8) c = (if ((c and 1) != 0) -0x12477ce0 xor (c ushr 1) else c ushr 1)
        c
    }

    fun update(old: Int, data: ByteArray, offset: Int, len: Int): Int {
        var c = old.inv()
        val table = TABLE
        for (n in offset until offset + len) c = table[(c xor (data[n].toInt() and 0xFF)) and 0xff] xor (c ushr 8)
        return c.inv()
    }
}