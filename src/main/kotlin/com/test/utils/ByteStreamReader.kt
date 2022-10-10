package com.test.utils

import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * @author Anton Kurinnoy
 */
class ByteStreamReader(private val array: ByteArray) {
    var cursor: Int = 0

    fun readSignature(): String {
        cursor += 2
        if (cursor > array.size) {
            throw IOException("End of file")
        }
        return String(array.sliceArray(cursor - 2 until cursor))
    }

    fun readByteInt(): Int {
        cursor++
        if (cursor > array.size) {
            throw IOException("End of file")
        }
        return (array[cursor - 1].toInt() and 0xff)
    }

    fun readByte(): Byte {
        cursor++
        if (cursor > array.size) {
            throw IOException("End of file")
        }
        return array[cursor - 1]
    }

    fun read2bytes(): Int {
        cursor += 2
        if (cursor > array.size) {
            throw IOException("End of file")
        }
        return (array[cursor - 1].toInt() and 0xff shl 8) or
                (array[cursor - 2].toInt() and 0xff)
    }

    fun read3bytes(): Int {
        cursor += 3
        if (cursor > array.size) {
            throw IOException("End of file")
        }
        return (array[cursor - 3].toInt() and 0xff) or
                (array[cursor - 2].toInt() and 0xff shl 8) or
                (array[cursor - 1].toInt() and 0xff shl 16)
    }

    fun read4bytes(): Int {
        cursor += Int.SIZE_BYTES
        if (cursor > array.size) {
            throw IOException("End of file")
        }
        return (array[cursor - 1].toInt() shl 24) or
                (array[cursor - 2].toInt() and 0xff shl 16) or
                (array[cursor - 3].toInt() and 0xff shl 8) or
                (array[cursor - 4].toInt() and 0xff)
    }

    fun read(size: Int): ByteArray {
        cursor += size
        if (cursor > array.size) {
            throw IOException("End of file")
        }
        return array.sliceArray(cursor - size until cursor)
    }

    fun readBytesToInt(size: Int): Int {
        cursor += size
        if (cursor > array.size) {
            throw IOException("End of file")
        }
        val chunk = ByteBuffer.wrap(array.sliceArray(cursor - size until cursor))
        return BigInteger(chunk.array()).toInt()
    }

    fun readBytesToString(size: Int): String {
        cursor += size
        if (cursor > array.size) {
            throw IOException("End of file")
        }
        return String(array.sliceArray(cursor - size until cursor))
    }

    fun skip(length: Int) {
        cursor += length
    }
}