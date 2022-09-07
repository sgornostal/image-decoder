package com.test.reader

import com.test.Image

/**
 * @author Slava Gornostal
 */
object Jpeg : ImageDecoder {
    override fun canRead(data: ByteArray): Boolean = false

    override fun encode(image: Image): ByteArray {
        TODO("Not yet implemented")
    }

    override fun decode(data: ByteArray): Image {
        TODO("Not yet implemented")
    }

}