package com.test

import java.io.ByteArrayOutputStream

/**
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 */
class Image(var width: Int = 0, var height: Int = 0, var colorType: Int = 0, var colors: IntArray = intArrayOf()) {

    val length = width * height

    val rawImageData: ByteArrayOutputStream = ByteArrayOutputStream()

    var palette: ByteArray = byteArrayOf()

    operator fun get(x: Int, y: Int): Int = this.colors[y * width + x]

    operator fun get(index: Int): Int = this.colors[index]

    operator fun set(x: Int, y: Int, color: Int) {
        this.colors[y * width + x] = color
    }

    operator fun set(index: Int, color: Int) {
        this.colors[index] = color
    }
}
