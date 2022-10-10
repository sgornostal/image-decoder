package com.test.reader

import com.test.Image
import com.test.utils.*
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.floor

/**
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 */
object Png : ImageDecoder {
    override fun canRead(data: ByteArray): Boolean = false

    override fun encode(image: Image): ByteArray {

        val stream = ByteStreamWriter()

        intArrayOf(137, 80, 78, 71, 13, 10, 26, 10).forEach { stream.writeInt(it) } //png signature
        writeChunk(stream, "IHDR", image)
        writeChunk(stream, "IDAT", image)
        writeChunk(stream, "IEND", image)

        return stream.getWrittenData()
    }

    override fun decode(data: ByteArray): Image {

        val stream = ByteStreamReader(data)

        val header = readChunkHeader(stream)

        val image = Image()
        readChunks(stream, image, true)
        val imageData = image.rawImageData.toByteArray().zlibDecompress()
        val data = ByteStreamReader(imageData)
        val colors = mutableListOf<Int>()
        val bpp = when (image.colorType) {
            2 -> 3
            6 -> 4
            else -> throw IOException("Unknown color type")
        }
        var previousLine = mutableListOf<Byte>()
        for (i in 0 until image.height) {
            val filterType = data.readByteInt()
            val currentLine = mutableListOf<Byte>()
            (0 until image.width * bpp).forEach { _ -> currentLine.add(data.readByte()) }
//            println("filterType: $filterType")
            when (filterType) {
                0 -> Unit
                1 -> for (n in bpp until currentLine.size) currentLine[n] =
                    (currentLine[n] + currentLine[n - bpp]).toByte()
                2 -> for (n in 0 until currentLine.size) currentLine[n] = (currentLine[n] + previousLine[n]).toByte()
                3 -> {
                    for (n in 0 until bpp) {
                        val a = currentLine[n].toInt() and 0xff
                        val b = previousLine[n].toInt() and 0xff
                        currentLine[n] = (a + b / 2).toByte()
                    }
                    for (n in bpp until currentLine.size) {
                        val a = currentLine[n].toInt() and 0xff
                        val b = currentLine[n - bpp].toInt() and 0xff
                        val c = previousLine[n].toInt() and 0xff
                        currentLine[n] = (a + floor((b + c) / 2.0)).toInt().toByte()
                    }
                }
                4 -> {
                    for (n in 0 until bpp) currentLine[n] = (currentLine[n] + previousLine[n]).toByte()
                    for (n in bpp until currentLine.size) currentLine[n] = (currentLine[n] + paethPredictor(
                        currentLine[n - bpp].toInt() and 0xff,
                        previousLine[n].toInt() and 0xff,
                        previousLine[n - bpp].toInt() and 0xff
                    )).toByte()
                }
                else -> println("filterType: $filterType")
            }
            previousLine = currentLine.toMutableList()
            currentLine.chunked(bpp)
                .map { ByteBuffer.wrap(byteArrayOf(0, it[0], it[1], it[2])).int }
                .forEach { colors.add(it) }
        }

        return Image(
            width = image.width,
            height = image.height,
            colors = colors.toIntArray()
        )
    }

    private fun readChunkHeader(stream: ByteStreamReader): List<Int> {
        val result = mutableListOf<Int>()
        for (i in 1..8) result.add(stream.readByteInt())

        return result
    }

    private fun readChunks(stream: ByteStreamReader, image: Image, firstHeader: Boolean = false) {
        val length = stream.readBytesToInt(4)
        val type = stream.readBytesToString(4)

        if (firstHeader && type != "IHDR") {
            throw IOException("First header is not a IHDR")
        }
        println("$type: $length")
        when (type) {
            "IHDR" -> readIHDR(stream, image)
            "PLTE" -> readPLTE(stream, length, image)
            "IDAT" -> readIDAT(stream, length, image)
            "IEND" -> return
            else -> stream.skip(length)
        }
        val crc = stream.readBytesToString(4)

        readChunks(stream, image)
    }

    private fun readIHDR(stream: ByteStreamReader, image: Image) {
        val width = stream.readBytesToInt(4)
        val height = stream.readBytesToInt(4)
        val bitDepth = stream.readBytesToInt(1)
        val colorType = stream.readBytesToInt(1)
        val compressionMethod = stream.readBytesToInt(1)
        val filterMethod = stream.readBytesToInt(1)
        val interlaceMethod = stream.readBytesToInt(1)

        image.width = width
        image.height = height
        image.colorType = colorType
    }

    private fun readPLTE(stream: ByteStreamReader, length: Int, image: Image) {
        image.palette = stream.read(length)
    }

    private fun readIDAT(stream: ByteStreamReader, length: Int, image: Image) {
        val dataBytes = stream.read(length)
        image.rawImageData.write(dataBytes)
    }

    private fun paethPredictor(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return if ((pa <= pb) && (pa <= pc)) a else if (pb <= pc) b else c
    }

    private fun writeChunk(stream: ByteStreamWriter, name: String, image: Image) {
        var crc = CRC32.initialValue

        when (name) {
            "IHDR" -> {
                stream.writeIntToBytes(13, 4)
                stream.writeString("IHDR")
                stream.writeIntToBytes(image.width, 4)
                stream.writeIntToBytes(image.height, 4)
                stream.writeInt(8) // bitDepth
                stream.writeInt(2) // colorType
                stream.writeInt(0) // compressionMethod
                stream.writeInt(0) // filterMethod
                stream.writeInt(0) // interlaceMethod

                crc = CRC32.update(crc, name.toByteArray(), 0, name.toByteArray().size)
                val writtenData = stream.getWrittenData()
                val data = writtenData.sliceArray(writtenData.size - 13 until writtenData.size)
                crc = CRC32.update(crc, data, 0, data.size)
            }
            "IDAT" -> {
                val data = ByteStreamWriter()
                val colors = image.colors.toList().chunked(image.width).map { it.toIntArray() }
                for (y in 0 until image.height) {
                    data.writeInt(0) //no filter
                    colors[y].forEach { data.write3bytesRev(it) }
                }
                val compressedData = data.getWrittenData().zlibCompress()
                stream.writeIntToBytes(compressedData.size, 4)
                stream.writeString("IDAT")
                compressedData.forEach { stream.writeByte(it) }
                crc = CRC32.update(crc, name.toByteArray(), 0, name.toByteArray().size)
                crc = CRC32.update(crc, compressedData, 0, compressedData.size)
            }
            "IEND" -> {
                stream.writeIntToBytes(0, 4)
                stream.writeString("IEND")
                crc = CRC32.update(crc, name.toByteArray(), 0, name.toByteArray().size)
                crc = CRC32.update(crc, byteArrayOf(), 0, 0)
            }
            else -> throw IOException("Unknown chunk")
        }

        stream.writeIntToBytes(crc, 4)
    }
}