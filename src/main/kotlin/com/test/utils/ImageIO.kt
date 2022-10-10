package com.test.utils

import com.test.*
import com.test.reader.Bmp
import com.test.reader.Jpeg
import com.test.reader.Png
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

/**
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 */
object ImageIO {

    private val decoders = listOf(Bmp, Png, Jpeg)

    fun readCustom(filePath: String): Image {
        val data = File(filePath).readBytes()
        val decoder = decoders.firstOrNull { it.canRead(data) } ?: throw IllegalStateException("Unsupported format")
        return decoder.decode(data)
    }

    fun writeCustom(filePath: String, format: String, image: Image) {
        val decoder = when (format) {
            "bmp" -> Bmp
            "png" -> Png
            "jpg" -> Jpeg
            else -> throw IllegalStateException("Unsupported format")
        }
        val data = decoder.encode(image)
        File(filePath).writeBytes(data)
    }

    fun read(filePath: String): Image {
        val bufferedImage = ImageIO.read(File(filePath))
        return createImage(bufferedImage.width, bufferedImage.height) { x, y ->
            bufferedImage.getRGB(
                x,
                y
            )
        }
    }

    fun write(filePath: String, format: String, image: Image) {
        val bufferedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        for (i in 0 until image.height) {
            for (j in 0 until image.width) {
                val newColor = Color(
                    image[j, i].red(),
                    image[j, i].green(),
                    image[j, i].blue()
                )
                bufferedImage.setRGB(j, i, newColor.rgb)
            }
        }
        ImageIO.write(bufferedImage, format, File(filePath))
    }

    private fun createImage(
        width: Int,
        height: Int,
        getColorAt: (x: Int, y: Int) -> Int
    ): Image =
        Image(
            width = width,
            height = height,
            colors = IntArray(width * height) { index -> getColorAt(index % width, index / width) }
        )

    fun grayscale(image: Image) {
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                image[x, y] = image[x, y].toGray()
            }
        }
    }

    fun gaussianBlur(
        image: Image,
        mask: Array<IntArray> = arrayOf(intArrayOf(1, 2, 1), intArrayOf(2, 4, 2), intArrayOf(1, 2, 1)),
        divisor: Int = 16
    ) {
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val (xmin, xmax) = max(x - 1, 0) to min(x + 1, image.width - 1)
                val (ymin, ymax) = max(y - 1, 0) to min(y + 1, image.height - 1)
                val pixels = arrayOf(
                    intArrayOf(image[xmin, ymin].gray(), image[x, ymin].gray(), image[xmax, ymin].gray()),
                    intArrayOf(image[xmin, y].gray(), image[x, y].gray(), image[xmax, y].gray()),
                    intArrayOf(image[xmin, ymax].gray(), image[x, ymax].gray(), image[xmax, ymax].gray()),
                )
                image[x, y] = createGrayColor(min((pixels * mask).sum() / divisor, 255))
            }
        }
    }

}