package com.test

import com.test.utils.ImageIO

/**
 * @author Slava Gornostal
 */
fun main() {
    val eye = ImageIO.read("eye.jpg")
    ImageIO.grayscale(eye)
    ImageIO.gaussianBlur(eye)
    ImageIO.write("eye-grayscale-blur.jpg", "jpg", eye)
}