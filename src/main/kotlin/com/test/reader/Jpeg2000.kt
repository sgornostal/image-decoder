package com.test.reader

import com.test.Image
import com.test.utils.*

/**
 * @author Anton Kurinnoy
 */
object Jpeg2000 : ImageDecoder {
    override fun canRead(data: ByteArray): Boolean = false

    override fun encode(image: Image): ByteArray {

        val stream = ByteStreamWriter()
//        stream.writeByteArray(byteArrayOf(0x00, 0x00, 0x00, 0x0C, 0x6A, 50, 20, 20, 0x0D, 0x0A, 87, 0x0A))
        stream.write4bytes(12) //signature length
        stream.writeString("jP  ") //signature || type
        stream.writeByteArray(byteArrayOf(0x0D, 0x0A, 87, 0x0A))
        stream.write4bytes(20) //ftyp length
        stream.writeString("ftyp") //type
        stream.writeByteArray(byteArrayOf(0x6a, 70, 32, 20)) //brand
        stream.write4bytes(0) //minorVersion
        stream.writeString("jp2 ") //compatibility list
        stream.write4bytes(111) //jp2h length???
        stream.writeString("jp2h") //type
        stream.write4bytes(111) //ihdr length???
        stream.writeString("ihdr") //type









        return byteArrayOf()
    }

    override fun decode(data: ByteArray): Image {

        val stream = ByteStreamReader(data)

        return Image(
            width = 10,
            height = 10,
            colors = intArrayOf()
        )
    }
}