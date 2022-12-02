package com.test.reader

import com.soywiz.korio.dynamic.KDynamic.Companion.toLong
import com.soywiz.korio.stream.*
import com.test.*
import com.test.utils.*
import java.util.*
import kotlin.collections.HashMap
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


        val y = mutableListOf<Int>()
//        val cb = mutableListOf<Int>()
//        val cr = mutableListOf<Int>()

        val colors = image.colors.toMutableList().chunked(image.width).map { it.toMutableList() }

        for (i in colors.indices) {
            colors[i].forEach {
                val color: Color = it
                val yCbCr = convertRGBtoYCbCr(color.red(), color.green(), color.blue())
                y.add(yCbCr[0])
//                cb.add(yCbCr[1])
//                cr.add(yCbCr[2])
            }
        }

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

        val yTable = y.chunked(colors[0].size).map { it.toMutableList() }
//        val cbTable = cb.chunked(colors[0].size).map { it.toMutableList().filterIndexed { index, _ -> index % 2 == 0} }.filterIndexed { index, _ -> index % 2 == 0}
//        val crTable = cr.chunked(colors[0].size).map { it.toMutableList().filterIndexed { index, _ -> index % 2 == 0} }.filterIndexed { index, _ -> index % 2 == 0}

        /*for (i in cbTable.indices step 2) {
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
        }*/


        val horiz = colors[0].size / 8
        val vert = colors.size / 8
        val blockCount = horiz * vert

        val yMCU = MutableList(blockCount) { Block() }
//        val cbMCU = MutableList(blockCount / 4) { Block() }
//        val crMCU = MutableList(blockCount / 4) { Block() }

//        val allMCU = MutableList(yMCU.size + cbMCU.size + crMCU.size) { Block() }

        var n = 0
        var m = 0
        for (i in yTable.indices) {
            for (j in 0 until yTable[i].size) {
                val blockNumber = i / 8 * vert + (j / 8 + 1)
                if (m > 7) m = 0
                yMCU[blockNumber - 1].set(n, m++, yTable[i][j] - 128)
            }
            n++
            if (n > 7) n = 0
        }

        /*n = 0
        m = 0
        for (i in cbTable.indices) {
            for (j in 0 until cbTable[i].size) {
                val blockNumber = i / 8 * vert / 2 + (j / 8 + 1)
                if (m > 7) m = 0
                cbMCU[blockNumber - 1].set(n, m++, cbTable[i][j] - 128)
                crMCU[blockNumber - 1].set(n, m++, crTable[i][j] - 128)
            }
            n++
            if (n > 7) n = 0
        }*/

        yMCU.forEach { it.dctTransform() }
//        cbMCU.forEach { it.dctTransform() }
//        crMCU.forEach { it.dctTransform() }

        yMCU.forEach { it.quantization(0) }
//        cbMCU.forEach { it.quantization(1) }
//        crMCU.forEach { it.quantization(1) }

        yMCU.forEach { it.zigzag() }
//        cbMCU.forEach { it.zigzag() }
//        crMCU.forEach { it.zigzag() }

        /*var l = 0
        var k = 0
        val yMCUchunked = yMCU.chunked(horiz)
        var index = 0
        (0 until allMCU.size step 6).forEach { _ ->
            allMCU[index] = yMCUchunked[k][l + 0]
            allMCU[index + 1] = yMCUchunked[k][l + 1]
            allMCU[index + 2] = yMCUchunked[k + 1][l + 0]
            allMCU[index + 3] = yMCUchunked[k + 1][l + 1]
            l += 2
            if (l >= horiz){
                l = 0
                k += 2
            }

            allMCU[index + 4] = cbMCU[0]
            allMCU[index + 5] = crMCU[0]
            cbMCU.removeAt(0)
            crMCU.removeAt(0)
            index += 6
        }*/

        val frequenciesYDC = HashMap<Int, Int>()
        yMCU.map { it.getDC() }.forEach {
            var count = frequenciesYDC[it]
            if (count == null) count = 0
            frequenciesYDC[it] = count + 1
        }
        val frequenciesYAC = HashMap<Int, Int>()
        yMCU.map { it.getAC() }.flatten().forEach {
            var count = frequenciesYAC[it]
            if (count == null) count = 0
            frequenciesYAC[it] = count!! + 1
        }
        /*val frequenciesCbCrDC = HashMap<Int, Int>()
        cbMCU.map { it.getDC() }.forEach {
            var count = frequenciesCbCrDC[it]
            if (count == null) count = 0
            frequenciesCbCrDC[it] = count!! + 1
        }
        crMCU.map { it.getDC() }.forEach {
            var count = frequenciesCbCrDC[it]
            if (count == null) count = 0
            frequenciesCbCrDC[it] = count!! + 1
        }
        val frequenciesCbCrAC = HashMap<Int, Int>()
        cbMCU.map { it.getAC() }.flatten().forEach {
            var count = frequenciesCbCrAC[it]
            if (count == null) count = 0
            frequenciesCbCrAC[it] = count!! + 1
        }
        crMCU.map { it.getAC() }.flatten().forEach {
            var count = frequenciesCbCrAC[it]
            if (count == null) count = 0
            frequenciesCbCrAC[it] = count!! + 1
        }*/

//        var rootNode: HuffmanNode? = HuffmanNode.encode(intArrayOf(1,2,3,4,5,6), intArrayOf(5,9,12,13,16,45))
//        var codes = HuffmanNode.getCodes(rootNode!!)
//        println(codes)
//        codes.forEach { i, s ->
//            println("i: $i => s: $s")
//        }
//        println("end")


        val allDC = yMCU.map { it.getDC() }.toIntArray()
        val differencesDC = IntArray(allDC.size)
        for (i in allDC.indices){
            if (i != 0){
                differencesDC[i] = allDC[i] - allDC[i-1]
            }
        }
        differencesDC[0] = allDC[0] //возможно это лишнее, инфа по этому поводу противоречивая
        val differencesDCCodes =  differencesDC.map { getCodeForDC(it) }

//        var rootNode: HuffmanNode? = HuffmanNode.encode(frequenciesYDC.keys.toIntArray(), frequenciesYDC.values.toIntArray())
//        var codes = HuffmanNode.getCodes(rootNode!!)
//        val numberOfCodesYDC = Array(16) {0} // length
//        codes.forEach { numberOfCodesYDC[it.value.length-1] += 1 } //почему обычно первый идёт 0?
//        var sortedValues = codes.toList().sortedBy { (_, value) -> value.length }.toMap().keys // aka symbols
//        val huffmanTables1 = numberOfCodesYDC.map { it.toByte() }.toByteArray() + sortedValues.map { it.toByte() }.toByteArray()
//        val huffmanTables1Length = numberOfCodesYDC.size + numberOfCodesYDC.sum()
//        val huffmanTables1 = intArrayOf(0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).map { it.toByte() }.toByteArray()
        val huffmanTables1 = intArrayOf(1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,4,2,6).map { it.toByte() }.toByteArray()

//        rootNode = HuffmanNode.encode(frequenciesYAC.keys.toIntArray(), frequenciesYAC.values.toIntArray())
//        codes = HuffmanNode.getCodes(rootNode!!)
//        val numberOfCodesYAC = Array(162) {0} // length
//        codes.forEach { numberOfCodesYAC[it.value.length] += 1 }
//        sortedValues = codes.toList().sortedBy { (_, value) -> value.length }.toMap().keys // aka symbols
//        val huffmanTables2 = numberOfCodesYAC.map { it.toByte() }.toByteArray() + sortedValues.map { it.toByte() }.toByteArray()
//        val huffmanTables2Length = numberOfCodesYAC.size + numberOfCodesYAC.sum()
//        val huffmanTables2 = intArrayOf(0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 125, 1, 2, 3, 0, 4, 17, 5, 18, 33, 49, 65, 6, 19, 81, 97, 7, 34, 113, 20, 50, 129, 145, 161, 8, 35, 66, 177, 193, 21, 82, 209, 240, 36, 51, 98, 114, 130, 9, 10, 22, 23, 24, 25, 26, 37, 38, 39, 40, 41, 42, 52, 53, 54, 55, 56, 57, 58, 67, 68, 69, 70, 71, 72, 73, 74, 83, 84, 85, 86, 87, 88, 89, 90, 99, 100, 101, 102, 103, 104, 105, 106, 115, 116, 117, 118, 119, 120, 121, 122, 131, 132, 133, 134, 135, 136, 137, 138, 146, 147, 148, 149, 150, 151, 152, 153, 154, 162, 163, 164, 165, 166, 167, 168, 169, 170, 178, 179, 180, 181, 182, 183, 184, 185, 186, 194, 195, 196, 197, 198, 199, 200, 201, 202, 210, 211, 212, 213, 214, 215, 216, 217, 218, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250).map { it.toByte() }.toByteArray()
        val huffmanTables2 = intArrayOf(0,2,2,3,1,0,0,0,0,0,0,0,0,0,0,0,1,2,3,17,0,4,33,34).map { it.toByte() }.toByteArray()

        /*rootNode = HuffmanNode.encode(frequenciesCbCrDC.keys.toIntArray(), frequenciesCbCrDC.values.toIntArray())
        codes = HuffmanNode.getCodes(rootNode!!)
        val numberOfCodesCbCrDC = Array(16) {0} // length
        codes.forEach { numberOfCodesCbCrDC[it.value.length] += 1 }
        sortedValues = codes.toList().sortedBy { (_, value) -> value.length }.toMap().keys // aka symbols
        val huffmanTables3 = numberOfCodesCbCrDC.map { it.toByte() }.toByteArray() + sortedValues.map { it.toByte() }.toByteArray()

        rootNode = HuffmanNode.encode(frequenciesCbCrAC.keys.toIntArray(), frequenciesCbCrAC.values.toIntArray())
        codes = HuffmanNode.getCodes(rootNode!!)
        val numberOfCodesCbCrAC = Array(162) {0} // length
        codes.forEach { numberOfCodesCbCrAC[it.value.length] += 1 }
        sortedValues = codes.toList().sortedBy { (_, value) -> value.length }.toMap().keys // aka symbols
        val huffmanTables4 = numberOfCodesCbCrAC.map { it.toByte() }.toByteArray() + sortedValues.map { it.toByte() }.toByteArray()*/


        val stream = ByteStreamWriter()
        val syncStream = MemorySyncStreamToByteArray {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) //SOI - Start Of Image
            writeApp(this)
            writeDQT(this, yMCU.first().getQuantTable())
            writeDHT(this, huffmanTables1, 0x00.toByte(), huffmanTables1.size)
            writeDHT(this, huffmanTables2, 0x10.toByte(), huffmanTables2.size)
            writeSOF(this, image)
            writeSOS(this, yMCU, differencesDCCodes)
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD9.toByte())) //EOI - End Of Image
        }
//        stream.writeByteArray(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) //SOI - Start Of Image
////        writeApp(stream)
//        writeDQT(stream, yMCU.first().getQuantTable())
////        writeDQT(stream, yMCU.first().getQuantTables())
//        writeDHT(stream, huffmanTables1, 0x00.toByte(), huffmanTables1.size)
//        writeDHT(stream, huffmanTables2, 0x10.toByte(), huffmanTables2.size)
////        writeDHT(stream, huffmanTables3, 0x01.toByte())
////        writeDHT(stream, huffmanTables4, 0x11.toByte())
//        writeSOF(stream)
//        writeSOS(stream, yMCU, differencesDCCodes)
//        stream.writeByteArray(byteArrayOf(0xFF.toByte(), 0xD9.toByte())) //EOI - End Of Image

        println("end")

        return syncStream
    }

    private fun writeSOS(stream: SyncStream, yMCU: List<Block>, differencesDCCodes: List<String>) {
//        stream.writeByteArray(byteArrayOf(0xFF.toByte(), 0xDA.toByte()))
//        stream.write2bytes(5)
//        stream.writeInt(1) //number of channels Y Cb Cr
//        stream.writeByte(0x01.toByte()) // Y
//        stream.writeByte(0x00.toByte())

        stream.writeBytes(byteArrayOf(0xFF.toByte(), 0xDA.toByte()))
        stream.write16BE(5)
        stream.write8(1) //number of channels Y Cb Cr
        stream.writeBytes(byteArrayOf(0x01.toByte(), 0x00.toByte())) // 01 - Y, 0. - tab for dc, .0 - tab for ac


//        stream.writeInt(2) // Cb
//        stream.writeByte(0x11.toByte())
//        stream.writeInt(3) // Cr
//        stream.writeByte(0x11.toByte())
//        stream.writeInt(0)
//        stream.writeInt(63)
//        stream.writeInt(0)
        var result = ""
        yMCU.mapIndexed { index, block ->
            result += makeResultData(differencesDCCodes[index], getCodeForAC(block.getAC()))
        }
        stream.write(result.chunked(8).map { convertBinaryToDecimal(it.toLong()).toByte() }.toByteArray())
    }

    private fun writeSOF(stream: SyncStream, image: Image) {
//        stream.writeByteArray(byteArrayOf(0xFF.toByte(), 0xC0.toByte()))
//        stream.write2bytes(11)
//        stream.writeInt(8) //precision
//        stream.write2bytes(height)
//        stream.write2bytes(width)
//        stream.writeInt(1) //components
//        stream.writeInt(1) // component id
//        stream.writeByte(0x11.toByte()) // sampling factor
//        stream.writeByte(0x00.toByte()) // quantization table id

//        stream.writeInt(2) // component id
//        stream.writeByte(0x11.toByte()) // sampling factor
//        stream.writeByte(0x01.toByte()) // quantization table id
//        stream.writeInt(3) // component id
//        stream.writeByte(0x11.toByte()) // sampling factor
//        stream.writeByte(0x01.toByte()) // quantization table id

        stream.writeBytes(byteArrayOf(0xFF.toByte(), 0xC0.toByte()))
        stream.write16BE(11)
        stream.write8(8) //precision
        stream.write16BE(image.height)
        stream.write16BE(image.width)
        stream.write8(1) //components
        stream.write8(1) // component id
        stream.writeBytes(byteArrayOf(0x11.toByte(), 0x00.toByte())) // sampling factor, quantization table id
    }

    private fun writeDHT(stream: SyncStream, data: ByteArray, byte: Byte, length: Int) {
//        stream.writeByteArray(byteArrayOf(0xFF.toByte(), 0xC4.toByte()))
//        stream.write2bytes(length + 3)
//        stream.writeByte(byte)
//        stream.writeByteArray(data)

        stream.writeBytes(byteArrayOf(0xFF.toByte(), 0xC4.toByte()))
        stream.write16BE(length + 3)
        stream.writeBytes(byteArrayOf(byte))
        stream.writeBytes(data)
    }

    private fun writeDQT(stream: SyncStream, data: ByteArray) {
//        stream.writeByteArray(byteArrayOf(0xFF.toByte(), 0xDB.toByte()))
//        stream.write2bytes(67) // length
//        stream.writeInt(0) // 0, 1 - Y, CbCr
////        stream.writeByteArray(byteArrayOf(0x84.toByte(), 0x00.toByte())) // 84 - hexdecimal, 132 - decimal, 64*2+2 + 2(length)
//        stream.writeByteArray(data)

        stream.writeBytes(byteArrayOf(0xFF.toByte(), 0xDB.toByte()))
        stream.write16BE(67)
        stream.write8(0)
        stream.writeBytes(data)
    }

    private fun writeApp(stream: SyncStream) {
//        val JFIF = ByteArray(2)
//        JFIF[0] = 0xff.toByte()
//        JFIF[1] = 0xe0.toByte()
//        stream.writeByteArray(JFIF)
//        stream.writeBytes(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x10.toByte(), 0x4A.toByte(), 0x46.toByte(), 0x49.toByte(), 0x46.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),))
        stream.writeBytes(byteArrayOf(0xff.toByte(), 0xe0.toByte()))
    }


    private fun makeResultData(dcCode: String, acCodes: String): String {
//        println("dcCode: $dcCode")
//        println("dcCodeSize: ${dcCode.length}")
//        println("acCodes: $acCodes")
//        println("acCodesSize: ${acCodes.length}")
//        println((dcCode + acCodes) .chunked(8))
        return (dcCode + acCodes) //.chunked(8).map { Integer.parseInt(it).toByte() }.toByteArray()
    }

    fun convertBinaryToDecimal(num: Long): Int {
        var num = num
        var decimalNumber = 0
        var i = 0
        var remainder: Long

        while (num.toInt() != 0) {
            remainder = num % 10
            num /= 10
            decimalNumber += (remainder * Math.pow(2.0, i.toDouble())).toInt()
            ++i
        }
        return decimalNumber
    }

    private fun getCodeForDC(dc: Int): String {
        val bits = getBits(dc)

        val category = when {
            dc > 0 -> getCategoryFromRange(dc)
            dc < 0 -> getCategoryFromRange(dc * -1)
            else -> 0
        }
        val code = getCodeByCategory(category)

        return code + bits
    }

    private fun getCategoryFromRange(value: Int): Int {
        val tmp = when{
            value > 0 -> value
            else -> value * -1
        }

        return when(tmp){
            1 -> 1
            2,3 -> 2
            in 4..7 -> 3
            in 8..15 -> 4
            in 16..31 -> 5
            in 32..63 -> 6
            in 64..127 -> 7
            in 128..255 -> 8
            in 256..511 -> 9
            in 512..1023 -> 10
            else -> 0
        }
    }


    private fun getBits(value: Int): String {
        val bits = when{
            value > 0 -> return Integer.toBinaryString(value)
            value < 0 -> {
                val tmp = -1 * value
                (1..10).forEach {
                    if (tmp < 2.0.pow(it.toDouble())){
                        return Integer.toBinaryString(tmp.inv()).takeLast(it)
                    }
                }
            }
            else -> return ""
        }

        return bits.toString()
    }

    private fun getCodeByCategory(category: Int): String? {
        val table = hashMapOf(
            0 to "00",
            1 to "010",
            2 to "011",
            3 to "100",
            4 to "101",
            5 to "110",
            6 to "1110",
            7 to "11110",
            8 to "111110",
            9 to "1111110",
            10 to "11111110",
            11 to "111111110",
        )

        return table[category]
    }

    private fun getCodeForAC(ac: List<Int>): String {
        val pairs: List<Pair<Int, Int>> = makePairs(ac) // List<Pair<zero number, value>>
        val rrrrssss = mutableListOf<Pair<Int, Int>>() //Pair<zero number/RRRR, category/SSSS>
        pairs.forEach { rrrrssss.add(Pair(it.first, getCategoryFromRange(it.second) )) }
        val codes = mutableListOf<String>()
        rrrrssss.forEach { codes.add(getCodeByRunSize(it)) }
        val bits = mutableListOf<String>()
        pairs.forEach { bits.add(getBits(it.second)) }

        var result = ""
        (0 until bits.size).forEach { result += codes[it] + bits[it] } //переделать на sequence?
        result += "1010" //(EOB)

        return result
    }

    private fun makePairs(ac: List<Int>): List<Pair<Int, Int>>{
        var zeroCount = 0
        val result = mutableListOf<Pair<Int, Int>>()
        ac.forEach { value ->
            when(value){
                0 -> zeroCount++
                else -> {
                    when {
                        zeroCount > 16 -> {
                            val count = zeroCount / 16
                            (1..count).forEach { _ -> result.add(Pair(15,0)) }
                            result.add(Pair(zeroCount-(16*count),value))
                            zeroCount = 0
                        }
                        else -> {
                            result.add(Pair(zeroCount,value))
                            zeroCount = 0
                        }
                    }
                }
            }
        }

        return result
    }

    private fun getCodeByRunSize(pair: Pair<Int, Int>): String =
        when(pair){
            Pair(0,0) -> "1010" // EOB
            Pair(15,0) -> "11111111001" //ZRL
            Pair(0,1) -> "00"
            Pair(0,2) -> "01"
            Pair(0,3) -> "100"
            Pair(0,4) -> "1011"
            Pair(0,5) -> "11010"
            Pair(0,6) -> "1111000"
            Pair(0,7) -> "11111000"
            Pair(0,8) -> "1111110110"
            Pair(0,9) -> "1111111110000010"
            Pair(0,10)-> "1111111110000011"
            Pair(1,1) -> "1100"
            Pair(1,2) -> "11011"
            Pair(1,3) -> "1111001"
            Pair(1,4) -> "111110110"
            Pair(1,5) -> "11111110110"
            Pair(1,6) -> "1111111110000100"
            Pair(1,7) -> "1111111110000101"
            Pair(1,8) -> "1111111110000110"
            Pair(1,9) -> "1111111110000111"
            Pair(1,10)-> "1111111110001000"
            Pair(2,1) -> "11100"
            Pair(2,2) -> "11111001"
            Pair(2,3) -> "1111110111"
            Pair(2,4) -> "111111110100"
            Pair(2,5) -> "1111111110001001"
            Pair(2,6) -> "1111111110001010"
            Pair(2,7) -> "1111111110001011"
            Pair(2,8) -> "1111111110001100"
            Pair(2,9) -> "1111111110001101"
            Pair(2,10)-> "1111111110001110"
            Pair(3,1) -> "111010"
            Pair(3,2) -> "111110111"
            Pair(3,3) -> "111111110101"
            Pair(3,4) -> "1111111110001111"
            Pair(3,5) -> "1111111110010000"
            Pair(3,6) -> "1111111110010001"
            Pair(3,7) -> "1111111110010010"
            Pair(3,8) -> "1111111110010011"
            Pair(3,9) -> "1111111110010100"
            Pair(3,10)-> "1111111110010101"
            Pair(4,1) -> "111011"
            Pair(4,2) -> "1111111000"
            Pair(4,3) -> "1111111110010110"
            Pair(4,4) -> "1111111110010111"
            Pair(4,5) -> "1111111110011000"
            Pair(4,6) -> "1111111110011001"
            Pair(4,7) -> "1111111110011010"
            Pair(4,8) -> "1111111110011011"
            Pair(4,9) -> "1111111110011100"
            Pair(4,10)-> "1111111110011101"
            Pair(5,1) -> "1111010"
            Pair(5,2) -> "11111110111"
            Pair(5,3) -> "1111111110011110"
            Pair(5,4) -> "1111111110011111"
            Pair(5,5) -> "1111111110100000"
            Pair(5,6) -> "1111111110100001"
            Pair(5,7) -> "1111111110100010"
            Pair(5,8) -> "1111111110100011"
            Pair(5,9) -> "1111111110100100"
            Pair(5,10)-> "1111111110100101"
            Pair(6,1) -> "1111011"
            Pair(6,2) -> "111111110110"
            Pair(6,3) -> "1111111110100110"
            Pair(6,4) -> "1111111110100111"
            Pair(6,5) -> "1111111110101000"
            Pair(6,6) -> "1111111110101001"
            Pair(6,7) -> "1111111110101010"
            Pair(6,8) -> "1111111110101011"
            Pair(6,9) -> "1111111110101100"
            Pair(6,10)-> "1111111110101101"
            Pair(7,1) -> "11111010"
            Pair(7,2) -> "111111110111"
            Pair(7,3) -> "1111111110101110"
            Pair(7,4) -> "1111111110101111"
            Pair(7,5) -> "1111111110110000"
            Pair(7,6) -> "1111111110110001"
            Pair(7,7) -> "1111111110110010"
            Pair(7,8) -> "1111111110110011"
            Pair(7,9) -> "1111111110110100"
            Pair(7,10)-> "1111111110110101"
            Pair(8,1) -> "111111000"
            Pair(8,2) -> "111111111000000"
            Pair(8,3) -> "1111111110110110"
            Pair(8,4) -> "1111111110110111"
            Pair(8,5) -> "1111111110111000"
            Pair(8,6) -> "1111111110111001"
            Pair(8,7) -> "1111111110111010"
            Pair(8,8) -> "1111111110111011"
            Pair(8,9) -> "1111111110111100"
            Pair(8,10)-> "1111111110111101"
            Pair(9,1) -> "111111001"
            Pair(9,2) -> "1111111110111110"
            Pair(9,3) -> "1111111110111111"
            Pair(9,4) -> "1111111111000000"
            Pair(9,5) -> "1111111111000001"
            Pair(9,6) -> "1111111111000010"
            Pair(9,7) -> "1111111111000011"
            Pair(9,8) -> "1111111111000100"
            Pair(9,9) -> "1111111111000101"
            Pair(9,10)-> "1111111111000110"
            Pair(10,1) -> "111111010"
            Pair(10,2) -> "1111111111000111"
            Pair(10,3) -> "1111111111001000"
            Pair(10,4) -> "1111111111001001"
            Pair(10,5) -> "1111111111001010"
            Pair(10,6) -> "1111111111001011"
            Pair(10,7) -> "1111111111001100"
            Pair(10,8) -> "1111111111001101"
            Pair(10,9) -> "1111111111001110"
            Pair(10,10)-> "1111111111001111"
            Pair(11,1) -> "1111111001"
            Pair(11,2) -> "1111111111010000"
            Pair(11,3) -> "1111111111010001"
            Pair(11,4) -> "1111111111010010"
            Pair(11,5) -> "1111111111010011"
            Pair(11,6) -> "1111111111010100"
            Pair(11,7) -> "1111111111010101"
            Pair(11,8) -> "1111111111010110"
            Pair(11,9) -> "1111111111010111"
            Pair(11,10)-> "1111111111011000"
            Pair(12,1) -> "1111111010"
            Pair(12,2) -> "1111111111011001"
            Pair(12,3) -> "1111111111011010"
            Pair(12,4) -> "1111111111011011"
            Pair(12,5) -> "1111111111011100"
            Pair(12,6) -> "1111111111011101"
            Pair(12,7) -> "1111111111011110"
            Pair(12,8) -> "1111111111011111"
            Pair(12,9) -> "1111111111100000"
            Pair(12,10)-> "1111111111100001"
            Pair(13,1) -> "11111111000"
            Pair(13,2) -> "1111111111100010"
            Pair(13,3) -> "1111111111100011"
            Pair(13,4) -> "1111111111100100"
            Pair(13,5) -> "1111111111100101"
            Pair(13,6) -> "1111111111100110"
            Pair(13,7) -> "1111111111100111"
            Pair(13,8) -> "1111111111101000"
            Pair(13,9) -> "1111111111101001"
            Pair(13,10)-> "1111111111101010"
            Pair(14,1) -> "1111111111101011"
            Pair(14,2) -> "1111111111101100"
            Pair(14,3) -> "1111111111101101"
            Pair(14,4) -> "1111111111101110"
            Pair(14,5) -> "1111111111101111"
            Pair(14,6) -> "1111111111110000"
            Pair(14,7) -> "1111111111110001"
            Pair(14,8) -> "1111111111110010"
            Pair(14,9) -> "1111111111110011"
            Pair(14,10)-> "1111111111110100"
            Pair(15,1) -> "1111111111110101"
            Pair(15,2) -> "1111111111110110"
            Pair(15,3) -> "1111111111110111"
            Pair(15,4) -> "1111111111111000"
            Pair(15,5) -> "1111111111111001"
            Pair(15,6) -> "1111111111111010"
            Pair(15,7) -> "1111111111111011"
            Pair(15,8) -> "1111111111111100"
            Pair(15,9) -> "1111111111111101"
            Pair(15,10)-> "1111111111111110"
            else -> ""
        }


    class HuffmanNode : Comparable<HuffmanNode> {
        var freq = 0
        var int = 0

        var left: HuffmanNode? = null
        var right: HuffmanNode? = null

        companion object {
            // recursive function to print the
            // huffman-code through the tree traversal.
            // Here s is the huffman - code generated.

            fun encode(charArray: IntArray, charFreq: IntArray): HuffmanNode? {
                val numCharacters = charArray.size

                // makes a min-priority queue(min-heap)
                val pQueue = PriorityQueue(numCharacters, HuffmanNode::compareTo)

                for (i in 0 until numCharacters) {
                    val huffNode = HuffmanNode()

                    huffNode.int = charArray[i]
                    huffNode.freq = charFreq[i]

                    huffNode.left = null
                    huffNode.right = null

                    pQueue.add(huffNode)
                }

                var rootNode: HuffmanNode? = null

                // Here we will extract the two minimum value
                // from the heap each time until
                // its size reduces to 1, extract until
                // all the nodes are extracted.
                while (pQueue.size > 1) {

                    val first = pQueue.poll()
                    val second = pQueue.poll()
                    val newHuffNode = HuffmanNode()

                    newHuffNode.freq = first.freq + second.freq
                    newHuffNode.int = -1

                    newHuffNode.left = first
                    newHuffNode.right = second

                    rootNode = newHuffNode
                    pQueue.add(newHuffNode)
                }
                return rootNode
            }

//            private val codes = mutableListOf<String>()
            private val codesHashMap = HashMap<Int, String>()
//            fun getCodes(root: HuffmanNode): List<String> {
            fun getCodes(root: HuffmanNode): HashMap<Int, String> {
                makeCodes(root, "")
//                return codes.toList()
                return codesHashMap
            }
            private fun makeCodes(root: HuffmanNode, s: String){
                if (root.left == null
                    && root.right == null
//                    && root.int != -1
                ) {
//                    codes.add(s)
                    codesHashMap[root.int] = s
                    return
                }

                makeCodes(root.left!!, "${s}0")
                makeCodes(root.right!!, "${s}1")
            }
        }

        override fun compareTo(other: HuffmanNode) =
            this.freq - other.freq
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
    private var qY: Array<IntArray> = arrayOf(
        intArrayOf(16, 11, 10, 16, 24, 40, 51, 61),
        intArrayOf(12, 12, 14, 19, 26, 58, 60, 55),
        intArrayOf(14, 13, 16, 24, 40, 57, 69, 56),
        intArrayOf(14, 17, 22, 29, 51, 87, 80, 62),
        intArrayOf(18, 22, 37, 56, 68, 109, 103, 77),
        intArrayOf(24, 35, 55, 64, 81, 104, 113, 92),
        intArrayOf(49, 64, 78, 87, 103, 121, 120, 101),
        intArrayOf(72, 92, 95, 98, 112, 100, 103, 99)
    )
    private var qCbCr: Array<IntArray> = arrayOf(
        intArrayOf(17, 18, 24, 47, 99, 99, 99, 99),
        intArrayOf(18, 21, 26, 66, 99, 99, 99, 99),
        intArrayOf(24, 26, 56, 99, 99, 99, 99, 99),
        intArrayOf(47, 66, 99, 99, 99, 99, 99, 99),
        intArrayOf(99, 99, 99, 99, 99, 99, 99, 99),
        intArrayOf(99, 99, 99, 99, 99, 99, 99, 99),
        intArrayOf(99, 99, 99, 99, 99, 99, 99, 99),
        intArrayOf(99, 99, 99, 99, 99, 99, 99, 99)
    )

    private val quantizationTable = arrayOf(qY, qCbCr)

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

    fun getQuantTable(): ByteArray = qY.map { it.toList() }.flatten().map { it.toByte() }.toByteArray()

    fun getQuantTables(): ByteArray =
        quantizationTable.flatten().map { it.toList() }.flatten().map { it.toByte() }.toByteArray()


    fun dctTransform() {
        var ci: Double
        var cj: Double
        var dct1: Double
        var sum: Double

        for (i in 0 until 8) {
            for (j in 0 until 8) {
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
                for (x in 0 until 8) {
                    for (y in 0 until 8) {
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

    fun quantization(type: Int) {
        for (i in 0 until 8) {
            for (j in 0 until 8) {
                this.set(i, j, this.get(i, j) / quantizationTable[type][i][j])
            }
        }
    }

    fun zigzag() {
        for (i in 0 until 8) {
            for (j in 0 until 8) {
                zzData[zigzag[i][j]] = data[i][j]
            }
        }
    }

    fun getDC(): Int {
        return zzData.first()
    }

    fun getAC(): List<Int> {
        return zzData.drop(0)
    }

    fun getZZData(): Array<Int> {
        return zzData
    }

    fun zzDataToByteArray(): ByteArray {
        return zzData.foldIndexed(ByteArray(zzData.size)) { i, a, v -> a.apply { set(i, v.toByte()) } }
    }
}










