package com.test.utils

import com.test.Image
import java.nio.ByteBuffer

/**
 * @author Anton Kurinnoy
 */
class ByteStreamWriter(private var temArray: ByteArray = byteArrayOf()) {

    fun writeByte(data: Byte) {
        val t = ByteArray(1)
        t[0] = data

        temArray += t
    }

    fun writeInt(data: Int) {
        val t = ByteArray(1)
        t[0] = (data shr 0).toByte()

        temArray += t
    }

    fun write2bytes(data: Int) {
        val t = ByteArray(2)
        t[0] = (data shr 0).toByte()
        t[1] = (data shr 8).toByte()

        temArray += t
    }

    private fun write3bytes(data: Int) {
        val t = ByteArray(3)
        t[0] = (data shr 0).toByte()
        t[1] = (data shr 8).toByte()
        t[2] = (data shr 16).toByte()

        temArray += t
    }

    fun write4bytes(data: Int) {
        val t = ByteArray(4)
        t[0] = (data shr 0).toByte()
        t[1] = (data shr 8).toByte()
        t[2] = (data shr 16).toByte()
        t[3] = (data shr 24).toByte()

        temArray += t
    }

    fun write3bytesRev(data: Int) {
        val t = ByteArray(3)
        t[2] = (data shr 0).toByte()
        t[1] = (data shr 8).toByte()
        t[0] = (data shr 16).toByte()

        temArray += t
    }

    fun writeColors(image: Image) {
        image.colors.forEach {
            this.write3bytes(it)
        }
    }

    fun writeString(str: String) {
        str.toByteArray().forEach { temArray += it }
    }

    fun writeIntToBytes(data: Int, bytes: Int) {
        ByteBuffer.allocate(bytes).putInt(data).array().forEach { temArray += it }
    }

    fun getWrittenData(): ByteArray = temArray
}