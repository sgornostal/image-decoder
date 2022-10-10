package com.test.utils

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 */
operator fun Array<IntArray>.times(array: Array<IntArray>): Array<IntArray> =
    Array(array.size) { i ->
        IntArray(array[0].size) { j ->
            this[i][j] * array[i][j]
        }
    }

fun Array<IntArray>.sum(): Int = this.sumOf { it.sum() }

fun ByteArray.zlibCompress(): ByteArray {
    val bytes = this

    val output = ByteArray(bytes.size * 4)
    val compressor = Deflater().apply {
        setInput(bytes)
        finish()
    }
    val compressedDataLength: Int = compressor.deflate(output)
    compressor.end()
    return output.copyOfRange(0, compressedDataLength)
}

fun ByteArray.zlibDecompress(): ByteArray {
    val inflater = Inflater()
    val outputStream = ByteArrayOutputStream()

    return outputStream.use {
        val buffer = ByteArray(1024)

        inflater.setInput(this)

        var count = -1
        while (count != 0) {
            count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        inflater.end()
        outputStream.toByteArray()
    }
}