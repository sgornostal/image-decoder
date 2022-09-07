package com.test.reader

import com.test.Image

/**
 * @author Slava Gornostal
 */
interface ImageDecoder {
    fun canRead(data: ByteArray): Boolean
    fun encode(image: Image): ByteArray
    fun decode(data: ByteArray): Image
}