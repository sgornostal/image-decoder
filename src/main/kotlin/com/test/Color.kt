package com.test

/**
 * @author Slava Gornostal
 */

typealias Color = Int

fun createColor(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b or 0x000000

fun createGrayColor(gray: Int) = (gray shl 16) or (gray shl 8) or gray or 0x000000

fun Color.red() = this ushr 16 and 0xFF
fun Color.green() = this ushr 8 and 0xFF
fun Color.blue() = this and 0xFF

fun Color.gray() = this and 0xFF

fun Color.toGray() = createGrayColor((0.2989 * red() + 0.5870 * green() + 0.1140 * blue()).toInt())