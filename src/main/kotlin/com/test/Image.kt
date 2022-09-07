package com.test

/**
 * @author Slava Gornostal
 */
class Image(val width: Int, val height: Int, val colors: IntArray) {

    val length = width * height

    operator fun get(x: Int, y: Int): Int = this.colors[y * width + x]

    operator fun get(index: Int): Int = this.colors[index]

    operator fun set(x: Int, y: Int, color: Int) {
        this.colors[y * width + x] = color
    }

    operator fun set(index: Int, color: Int) {
        this.colors[index] = color
    }
}
