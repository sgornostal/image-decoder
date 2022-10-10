package com.test.reader

import com.test.Image
import com.test.utils.ByteStreamReader
import com.test.utils.ByteStreamWriter
import java.io.IOException

/**
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 */
object Bmp : ImageDecoder {
    override fun canRead(data: ByteArray): Boolean = false

    override fun encode(image: Image): ByteArray {

        val imageSize = image.length * 24 / 8
        val fileSize = 54 + imageSize

        val stream = ByteStreamWriter()
        // FILE HEADER
        stream.writeInt('B'.code)
        stream.writeInt('M'.code)
        stream.write4bytes(fileSize) //fileSize = dataOffset + imageSize
        stream.write2bytes(0) //reserved1
        stream.write2bytes(0) //reserved2
        stream.write4bytes(54) //dataOffset
        // INFO HEADER
        stream.write4bytes(40) //headerSize
        stream.write4bytes(image.width) //width
        stream.write4bytes(image.height) //height
        stream.write2bytes(1) //planes
        stream.write2bytes(24) //bitsPerPixel
        stream.write4bytes(0) //compression
        stream.write4bytes(imageSize) //imageSize = width * height * bitsPerPixel / 8
        stream.write4bytes(0) //pixelsPerMeterX
        stream.write4bytes(0) //pixelsPerMeterY
        stream.write4bytes(0) //usedColors
        stream.write4bytes(0) //importantColors
        stream.writeColors(image)


        return stream.getWrittenData()
    }

    override fun decode(data: ByteArray): Image {

        val stream = ByteStreamReader(data)

        // FILE HEADER
        val header = stream.readSignature()
        if (header != "BM") throw IOException("File format mast be 'bmp'!")
        val fileSize = stream.read4bytes()
        val reserved1 = stream.read2bytes()
        val reserved2 = stream.read2bytes()
        val dataOffset = stream.read4bytes()
        // INFO HEADER
        val headerSize = stream.read4bytes()
        val width = stream.read4bytes()
        val height = stream.read4bytes()
        val planes = stream.read2bytes()
        val bitsPerPixel = stream.read2bytes()
        val compression = stream.read4bytes()
        if (compression != 0) throw IOException("Unsupported compression value!")
        val imageSize = stream.read4bytes()
        val pixelsPerMeterX = stream.read4bytes()
        val pixelsPerMeterY = stream.read4bytes()
        val usedColors = stream.read4bytes()
        val importantColors = stream.read4bytes()
        if (dataOffset > 54) {
            for (n in 0 until dataOffset - 54) stream.readByte()
        }
        val colors = readColors(stream, bitsPerPixel, width, height)

        return Image(
            width = width,
            height = height,
            colors = colors
        )
    }

    private fun readColors(stream: ByteStreamReader, bitsPerPixel: Int, width: Int, height: Int): IntArray {
        val skip = when (val padding = 4 - (width * 24 / 8 % 4)) {
            4 -> 0
            else -> padding
        }
        val result: Array<IntArray> = Array(height) {
            IntArray(width)
        }
        when (bitsPerPixel) {
            24, 32 -> {
                for (i in height - 1 downTo 0) {
                    for (j in 0 until width) {
                        result[i][j] = stream.read3bytes()
                    }
                    stream.skip(skip)
                }
            }
            else -> throw IllegalArgumentException("Unsupported bits per pixel value")
        }

        return result.flatMap { it.asIterable() }.toIntArray()
    }
}