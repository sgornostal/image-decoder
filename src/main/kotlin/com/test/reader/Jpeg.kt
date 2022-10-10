package com.test.reader

import com.test.Image
import com.test.utils.*
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * @author Slava Gornostal
 */
object Jpeg : ImageDecoder {

    // huffman
    private val hTables: MutableMap<Int, HuffmanTable> =
        mutableMapOf() // <ht header, ht> DC Y, CbCr : 0, 1 AC Y, CbCr : 16, 17

    // qt
    private val qTables: MutableMap<Int, IntArray> =
        mutableMapOf() // <qt destination, 8x8 table> destination Y : 0 CbCr : 1

    // sof
    private var precision = 0 // bit precision
    private var width = 0
    private var height = 0
    private var mcuWidth = 0
    private var mcuHeight = 0
    private var mcuHSF = 0 // horizontal sample factor
    private var mcuVSF = 0 // vertical sample factor
    private var colour = false // chroma components exist in jpeg
    private var mode = 0 // 0 baseline 1 progressive(not supported yet)

    //dri
    private var restartInterval = 0

    override fun canRead(data: ByteArray): Boolean = true

    override fun encode(image: Image): ByteArray {

        val stream = ByteStreamWriter()
        val y = mutableListOf<Int>()
        val cb = mutableListOf<Int>()
        val cr = mutableListOf<Int>()

        val colors = image.colors.toMutableList().chunked(image.width).map { it.toMutableList() }

        val horizontal = 8 - image.width % 8
        val vertical = 8 - image.height % 8
        if (horizontal in 1..7) {
            for (i in colors.indices) {
                (1..horizontal).forEach { _ -> colors[i].add(colors[i].last()) }
            }
        }
        if (vertical in 1..7) {
            (1..vertical).forEach { _ -> colors.toMutableList().add(colors.last()) }
        }

        for (i in colors.indices) {
            colors[i].forEach {
                val yCbCr = convertRGBtoYCbCr(it shr 0, it shr 8, it shr 16)
                y.add(yCbCr[0])
                cb.add(yCbCr[1])
                cr.add(yCbCr[2])
            }
        }

        val yTable = y.chunked(colors[0].size).map { it.toMutableList() }
        val cbTable = cb.chunked(colors[0].size).map { it.toMutableList() }
        val crTable = cr.chunked(colors[0].size).map { it.toMutableList() }
        for (i in cbTable.indices step 2) {
            for (j in 0 until cbTable[i].size step 2) {
                val sredneeCb = (cbTable[i][j] + cbTable[i][j + 1] + cbTable[i + 1][j] + cbTable[i + 1][j + 1]) / 4
                cbTable[i][j] = sredneeCb
                cbTable[i][j + 1] = sredneeCb
                cbTable[i + 1][j] = sredneeCb
                cbTable[i + 1][j + 1] = sredneeCb
                val sredneeCr = (crTable[i][j] + crTable[i][j + 1] + crTable[i + 1][j] + crTable[i + 1][j + 1]) / 4
                crTable[i][j] = sredneeCr
                crTable[i][j + 1] = sredneeCr
                crTable[i + 1][j] = sredneeCr
                crTable[i + 1][j + 1] = sredneeCr
            }
        }

        val horiz = colors[0].size / 8
        val vert = colors.size / 8
        val blockCount = horiz * vert

        val yMCU = MutableList(blockCount) { Block() }
        val cbMCU = MutableList(blockCount) { Block() }
        val crMCU = MutableList(blockCount) { Block() }


        for (i in cbTable.indices) {
            for (j in 0..cbTable[i].size) {
                val blockNumber = j + j * (i / 8)
                yMCU[blockNumber].set(i, j, yTable[i][j] - 128)
                cbMCU[blockNumber].set(i, j, cbTable[i][j] - 128)
                crMCU[blockNumber].set(i, j, crTable[i][j] - 128)
            }
        }

        yMCU.forEach { it.dctTransform() }
        cbMCU.forEach { it.dctTransform() }
        crMCU.forEach { it.dctTransform() }

        yMCU.forEach { it.quantization() }
        cbMCU.forEach { it.quantization() }
        crMCU.forEach { it.quantization() }

        yMCU.forEach { it.zigzag() }
        cbMCU.forEach { it.zigzag() }
        crMCU.forEach { it.zigzag() }




        return byteArrayOf()
    }

    override fun decode(data: ByteArray): Image {

        val stream = ByteStreamReader(data)
        val imgData = mutableListOf<Int>()

        for (i in data.indices) {
            imgData.add(stream.readByteInt())
        }

        var mode = -1 // 'uninitialized' value, use first sof marker encountered

        val colors = arrayListOf<Int>()

        for (i in imgData.indices) {
            if (imgData[i] == 0xff) {
                val m = imgData[i] shl 8 or imgData[i + 1]
                when (m) {
                    0xffe0 -> println("-- JFIF --");
                    0xffe1 -> println("-- EXIF --");
                    0xffc4 -> { // dht
                        val length = imgData[i + 2] shl 8 or imgData[i + 3]
                        decodeHuffmanTables(imgData.toIntArray().copyOfRange(i + 4, i + 2 + length))
                    }
                    0xffdb -> { // qt
                        val length = imgData[i + 2] shl 8 or imgData[i + 3]
                        decodeQuantizationTables(imgData.toIntArray().copyOfRange(i + 4, i + 2 + length))
                    }
                    0xffdd -> { // dri
                        val length = imgData[i + 2] shl 8 or imgData[i + 3]
                        restartInterval = imgData.toIntArray().copyOfRange(i + 4, i + 2 + length).sum()
                    }
                    0xffc0 -> { // sof-0 baseline
                        val length = imgData[i + 2] shl 8 or imgData[i + 3]
                        decodeStartOfFrame(imgData.toIntArray().copyOfRange(i + 4, i + 2 + length))
                        if (mode == -1) mode = 0
                    }
                    0xffc2 -> { // sof-1 progressive
                        if (mode == -1) mode = 1;
                    }
                    0xffda -> { // sos
                        val length = imgData[i + 2] shl 8 or imgData[i + 3]
                        val convertedMCU = decodeStartOfScan(
                            imgData.toIntArray().copyOfRange(i + 2 + length, imgData.size - 2)
                        ) // last 2 two bytes are 0xffd9 - EOI

                        val colorsMatrix = Array(height) {
                            IntArray(width) { 0 }
                        }

                        var blockCount = 0
                        val a = ceil((height / mcuHeight.toFloat()).toDouble()).toInt()
                        val b = ceil((width / mcuWidth.toFloat()).toDouble()).toInt()
                        for (ii in 0 until a) {
                            for (j in 0 until b) {
                                for (y in 0 until mcuHeight) { // mcu block
                                    for (x in 0 until mcuWidth) {
                                        val xx = j * mcuWidth + x
                                        val yy = ii * mcuHeight + y
                                        colorsMatrix[yy][xx] = convertedMCU[blockCount][y][x]
                                    }
                                }
                                blockCount++
                            }
                        }

                        for (ii in colorsMatrix) {
                            for (j in ii) {
                                colors.add(j)
                            }
                        }
                    }
                }
            }
        }

        return Image(
            width = width,
            height = height,
            colors = colors.toIntArray()
        )
    }

    private fun decodeHuffmanTables(chunk: IntArray) {
        val cd = chunk[0] // 00, 01, 10, 11 - 0, 1, 16, 17 - Y DC, CbCr DC, Y AC, CbCr AC
        val lengths = chunk.copyOfRange(1, 17)
        val to = 17 + lengths.sum()
        val symbols = chunk.copyOfRange(17, to)
        val lookup = HashMap<Int, IntArray>() // code lengths, symbol(s)
        var si = 0
        for (i in lengths.indices) {
            val l = lengths[i]
            val symbolsOfLengthI = IntArray(l)
            for (j in 0 until l) {
                symbolsOfLengthI[j] = symbols[si]
                si++
            }
            lookup[i + 1] = symbolsOfLengthI
        }
        hTables[cd] = HuffmanTable(lookup)
        val newChunk = chunk.copyOfRange(to, chunk.size)
        if (newChunk.isNotEmpty()) decodeHuffmanTables(newChunk)
    }

    private fun decodeQuantizationTables(chunk: IntArray) {
        val d = chunk[0] // 0, 1 - Y, CbCr
        val table = chunk.copyOfRange(1, 65) // 8x8 qt 64 values
        qTables[d] = table
        val newChunk = chunk.copyOfRange(65, chunk.size)
        if (newChunk.isNotEmpty()) decodeQuantizationTables(newChunk)
    }

    private fun decodeStartOfFrame(chunk: IntArray) {
        precision = chunk[0]
        height = chunk[1] shl 8 or chunk[2]
        width = chunk[3] shl 8 or chunk[4]
        val noc = chunk[5] // 1 grey-scale, 3 colour
        colour = noc == 3

        // component sample factor stored relatively, so y component sample factor contains information about how
        // large mcu is.
        for (i in 0 until noc) {
            val id = chunk[6 + i * 3] // 1 = Y, 2 = Cb, 3 = Cr, 4 = I, 5 = Q
            val factor = chunk[7 + i * 3]
            if (id == 1) { // y component, check sample factor to determine mcu size
                mcuHSF = factor shr 4 // first nibble (horizontal sample factor)
                mcuVSF = factor and 0x0f // second nibble (vertical sample factor)
                mcuWidth = 8 * mcuHSF
                mcuHeight = 8 * mcuVSF
                println("JPEG Sampling Factor -> " + mcuHSF + "x" + mcuVSF + if (mcuHSF == 1 && mcuVSF == 1) " (No Subsampling)" else " (Chroma Subsampling)")
            }
            // int table = chunk[8+(i*3)];
        }
    }

    private fun decodeStartOfScan(imgData: IntArray): MutableList<Array<IntArray>> {
        var imgData = imgData
        if (mode != 0) {
            System.err.println("This decoder only supports baseline JPEG images.")
            return mutableListOf()
        }
        println("Decoding Scan Image Data...")
        val imgDataList: MutableList<Int> = ArrayList(imgData.size)
        for (b in imgData) imgDataList.add(b)

        // check for and remove stuffing byte and restart markers
        var k = 0
        while (k < imgDataList.size) {
            if (imgDataList[k] == 0xff) {
                val nByte = imgDataList[k + 1]
                if (nByte == 0x00) { // stuffing byte
                    imgDataList.removeAt(k + 1)
                }
                if (nByte >= 0xd0 && nByte <= 0xd7) { // remove restart marker
                    imgDataList.removeAt(k) // remove 0xff
                    imgDataList.removeAt(k) // remove 0xdn
                }
            }
            k++
        }

        // convert back to int[]
        imgData = IntArray(imgDataList.size)
        for (i in imgDataList.indices) imgData[i] = imgDataList[i]

        // list of converted matrices to write to file
        val convertedMCUs: MutableList<Array<IntArray>> = ArrayList()

        // start decoding
        var restartCount = restartInterval // for restart markers, interval obtained from DRI marker
        val stream = BitStream(imgData)
        val oldDCCoes = intArrayOf(0, 0, 0) // Y, Cb, Cr

        // matrices
        var yMatrices: MutableList<Array<IntArray>>
        var yMatrix: Array<IntArray>?
        var cbMatrix: Array<IntArray>? = null
        var crMatrix: Array<IntArray>? = null
        outer@ for (i in 0 until ceil((height / mcuHeight.toFloat()).toDouble()).toInt()) { // cast to float to avoid rounding errors
            for (j in 0 until ceil((width / mcuWidth.toFloat()).toDouble()).toInt()) {

                // mcu
                yMatrices = ArrayList() // 2x2 - y0 y1 y2 y3 | 2x1 - y0 y1 | 1x1 y0

                // loop to obtain all luminance (y) matrices, which is greater than 1 if there is chroma subsampling
                for (k in 0 until mcuVSF) {
                    for (l in 0 until mcuHSF) {
                        yMatrix = createMatrix(stream, 0, oldDCCoes, 0)
                        if (yMatrix == null) // end of bit stream
                            break@outer
                        else
                            yMatrices.add(yMatrix)
                    }
                }
                if (colour) {
                    cbMatrix = createMatrix(stream, 1, oldDCCoes, 1)
                    crMatrix = createMatrix(stream, 1, oldDCCoes, 2)
                    if (cbMatrix == null || crMatrix == null) break@outer  // end of bit stream
                }
                convertedMCUs.add(
                    convertMCU(
                        yMatrices,
                        cbMatrix,
                        crMatrix
                    )
                )
                if (restartInterval != 0) { // dri marker exists in image
                    if (--restartCount == 0) {
                        restartCount = restartInterval // reset counter to interval

                        // reset DC coefficients
                        oldDCCoes[0] = 0
                        oldDCCoes[1] = 0
                        oldDCCoes[2] = 0
                        stream.restart() // set bit stream to start again on byte boundary
                    }
                }
            }
        }
//        createDecodedBitMap(convertedMCUs)
        return convertedMCUs
    }

    private fun convertMCU(
        yMatrices: List<Array<IntArray>>,
        cbMatrix: Array<IntArray>?,
        crMatrix: Array<IntArray>?
    ): Array<IntArray> {
        // int values representing pixel colour or just luminance (greyscale image) in the sRGB ColorModel 0xAARRGGBB
        val convertedMCU = Array(mcuHeight) { IntArray(mcuWidth) }
        for (r in convertedMCU.indices) {
            for (c in convertedMCU[r].indices) {

                // luminance
                val yMatrixIndex = r / 8 * mcuHSF + c / 8
                val yMatrix = yMatrices[yMatrixIndex]
                val y = yMatrix[r % 8][c % 8]
                val channels: FloatArray = if (colour) { // rgb or just luminance for greyscale
                    // chrominance
                    val cb = cbMatrix?.get(r / mcuVSF)?.get(c / mcuHSF)
                    val cr = crMatrix?.get(r / mcuVSF)?.get(c / mcuHSF)
//                    val cb = cbMatrix[r / mcuVSF][c / mcuHSF]
//                    val cr = crMatrix[r / mcuVSF][c / mcuHSF]

                    floatArrayOf(
                        y + 1.402f * cr!!,  // red
                        y - (0.344f * cb!!) - (0.714f * cr),  // green
                        y + (1.772f * cb) // blue
                    )
                } else {
                    floatArrayOf(y.toFloat())
                }
                for (chan in channels.indices) {
                    channels[chan] += 128f // shift block

                    // clamp block
                    if (channels[chan] > 255) channels[chan] = 255f
                    if (channels[chan] < 0) channels[chan] = 0f
                }
                convertedMCU[r][c] =
                    0xff shl 24 or (channels[0].toInt() shl 16) or (channels[if (colour) 1 else 0].toInt() shl 8) or channels[if (colour) 2 else 0].toInt() // 0xAARRGGBB
            }
        }
        return convertedMCU
    }

    private fun createMatrix(stream: BitStream, key: Int, oldDCCoes: IntArray, oldDCCoIndex: Int): Array<IntArray>? {
        val inverseDCT = DCT3(precision)
        var code = hTables[key]!!.getCode(stream)
        if (code == -1) return null // end of bit stream
        var bits = stream.getNextNBits(code)
        oldDCCoes[oldDCCoIndex] += decodeComponent(bits, code)
        // oldDCCo[oldDCCoIndex] is now new dc coefficient

        // set new dc value to old dc value multiplied by the first value in quantization table
        inverseDCT.setComponent(
            0,
            oldDCCoes[oldDCCoIndex] * qTables[key]!![0]
        )
        var index = 1
        while (index < 64) {
            code = hTables[key + 16]!!.getCode(stream)
            if (code == 0) {
                break // end of block
            } else if (code == -1) {
                return null // end of bit stream
            }

            // read first nibble of each code to find number of leading zeros
            val nib: Int = code shr 4
            if (nib > 0) {
                index += nib
                code = code and 0x0f // chop off preceding nibble
            }
            bits = stream.getNextNBits(code)
            if (index < 64) { // if haven't reached end of mcu
                val acCo: Int = decodeComponent(bits, code) // ac coefficient
                inverseDCT.setComponent(
                    index,
                    acCo * qTables[key]!![index]
                )
                index++
            }
        }
        inverseDCT.zigzagRearrange()
        return inverseDCT.dct3()
    }

    private fun decodeComponent(bits: Int, code: Int): Int { // decodes to find signed value from bits
        val c = 2.0.pow((code - 1).toDouble()).toFloat()
        return (if (bits >= c) bits else bits - (c * 2 - 1)).toInt()
    }

    private fun convertRGBtoYCbCr(red: Int, green: Int, blue: Int): Array<Int> {
        val y = 0 + 0.299 * red + 0.587 * green + 0.114 * blue
        val cb = 128 - 0.168736 * red - 0.331 * green + 0.5 * blue
        val cr = 128 + 0.5 * red - 0.418688 * green - 0.081312 * blue

        return arrayOf(y.toInt(), cb.toInt(), cr.toInt())
    }

}

class Block {
    private var q: Array<IntArray> = arrayOf(
        intArrayOf(16, 11, 10, 16, 24, 40, 51, 61),
        intArrayOf(12, 12, 14, 19, 26, 58, 60, 55),
        intArrayOf(14, 13, 16, 24, 40, 57, 69, 56),
        intArrayOf(14, 17, 22, 29, 51, 87, 80, 62),
        intArrayOf(18, 22, 37, 56, 68, 109, 103, 77),
        intArrayOf(24, 35, 55, 64, 81, 104, 113, 92),
        intArrayOf(49, 64, 78, 87, 103, 121, 120, 101),
        intArrayOf(72, 92, 95, 98, 112, 100, 103, 99)
    )

    val zigzag = arrayOf(
        intArrayOf(0, 1, 5, 6, 14, 15, 27, 28),
        intArrayOf(2, 4, 7, 13, 16, 26, 29, 42),
        intArrayOf(3, 8, 12, 17, 25, 30, 41, 43),
        intArrayOf(9, 11, 18, 24, 31, 40, 44, 53),
        intArrayOf(10, 19, 23, 32, 39, 45, 52, 54),
        intArrayOf(20, 22, 33, 38, 46, 51, 55, 60),
        intArrayOf(21, 34, 37, 47, 50, 56, 59, 61),
        intArrayOf(35, 36, 48, 49, 57, 58, 62, 63)
    )

    private val data = Array(8) {
        IntArray(8) { 0 }
    }

    private val zzData = Array(64) { 0 }

    fun set(height: Int, width: Int, value: Int) {
        data[height][width] = value
    }

    fun get(height: Int, width: Int): Int {
        return data[height][width]
    }

    fun dctTransform() {
        var ci: Double
        var cj: Double
        var dct1: Double
        var sum: Double

        for (i in 0..8) {
            for (j in 0..8) {
                val a = sqrt(8.0)
                val b = sqrt(2.0)
                ci = when (i) {
                    0 -> 1 / a
                    else -> b / a
                }
                cj = when (j) {
                    0 -> 1 / a
                    else -> b / a
                }

                sum = 0.0
                for (x in 0..8) {
                    for (y in 0..8) {
                        dct1 = this.get(x, y) *
                                cos((2 * x + 1) * i * Math.PI / 16) *
                                cos((2 * y + 1) * j * Math.PI / 16)
                        sum += dct1
                    }
                }
                this.set(i, j, (ci * cj * sum).toInt())
            }
        }
    }

    fun quantization() {
        for (i in 0..8) {
            for (j in 0..8) {
                this.set(i, j, this.get(i, j) / q[i][j])
            }
        }
    }

    fun zigzag() {
        val zz1d = mutableListOf<Int>()
        val data1d = mutableListOf<Int>()
        for (i in 0..8) {
            for (j in 0..8) {
                zz1d.add(zigzag[i][j])
                data1d.add(this.get(i, j))
            }
        }

        for (i in 0..63) {
            zzData[i] = data1d[zz1d[i]]
        }
    }
}