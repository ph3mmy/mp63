package ng.com.plustech.mp63kotlin.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.experimental.and

object BytesUtil {
    private fun getBytes(data: Byte): ByteArray? {
        val bytes = ByteArray(1)
        bytes[0] = data
        return bytes
    }

    private fun getBytes(data: String, charsetName: String): ByteArray? {
        val charset = Charset.forName(charsetName)
        return data.toByteArray(charset)
    }

    fun getBytes(data: String): ByteArray? {
        return getBytes(data, "GBK")
    }

    private fun getByte(bytes: ByteArray?): Byte {
        return bytes!![0]
    }

    private fun getString(bytes: ByteArray?, charsetName: String): String? {
        return String(bytes!!, Charset.forName(charsetName))
    }

    private fun getString(bytes: ByteArray?): String? {
        return getString(bytes, "GBK")
    }

    fun hexString2ByteArray(hexStr: String?): ByteArray? {
        if (hexStr == null) return null
        if (hexStr.length % 2 != 0) {
            return null
        }
        val data = ByteArray(hexStr.length / 2)
        for (i in 0 until hexStr.length / 2) {
            val hc = hexStr[2 * i]
            val lc = hexStr[2 * i + 1]
            val hb = hexChar2Byte(hc)
            val lb = hexChar2Byte(lc)
            if (hb < 0 || lb < 0) {
                return null
            }
            val n: Int = hb.toInt() shl 4
            data[i] = (n + lb).toByte()
        }
        return data
    }

    fun hexChar2Byte(c: Char): Byte {
        if (c >= '0' && c <= '9') return (c - '0').toByte()
        if (c >= 'a' && c <= 'f') return (c - 'a' + 10).toByte()
        return if (c >= 'A' && c <= 'F') (c - 'A' + 10).toByte() else -1
    }

    fun streamCopy(srcArrays: List<ByteArray?>): ByteArray? {
        var destAray: ByteArray? = null
        val bos = ByteArrayOutputStream()
        try {
            for (srcArray in srcArrays) {
                bos.write(srcArray)
            }
            bos.flush()
            destAray = bos.toByteArray()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                bos.close()
            } catch (e: IOException) {
            }
        }
        return destAray
    }

    fun getString(map: Map<String?, Any?>, key: String?): String? {
        return getString(map[key] as ByteArray?)
    }

    fun getBytes(map: Map<String?, Any?>, key: String?): ByteArray? {
        return map[key] as ByteArray?
    }

    fun getByte(map: Map<String?, Any?>, key: String?): Byte {
        return getByte(map[key] as ByteArray?)
    }

    fun putString(map: MutableMap<String?, Any?>, key: String?, s: String) {
        map[key] = getBytes(s)
    }

    fun putBytes(map: MutableMap<String?, Any?>, key: String?, b: ByteArray?) {
        map[key] = b
    }

    fun putByte(map: MutableMap<String?, Any?>, key: String?, b: Byte) {
        map[key] = getBytes(b)
    }

    fun printHex(b: ByteArray, len: Int): String? {
        val builder = StringBuilder()
        for (i in 0 until len) {
            var hex = Integer.toHexString((b[i] and 0xFF.toByte()).toInt())
            if (hex.length == 1) {
                hex = "0$hex"
            }
            builder.append(hex.toUpperCase() + " ")
        }
        return builder.toString()
    }


    fun bytes2Hex(b: ByteArray?): String? {
        if (b == null) {
            return ""
        }
        val builder = StringBuilder()
        for (i in b.indices) {
            var hex = Integer.toHexString((b[i] and 0xFF.toByte()).toInt())
            if (hex.length == 1) {
                hex = "0$hex"
            }
            builder.append(hex.toUpperCase())
        }
        return builder.toString()
    }

    fun subBytes(src: ByteArray?, begin: Int, count: Int): ByteArray? {
        val bs = ByteArray(count)
        System.arraycopy(src, begin, bs, 0, count)
        return bs
    }

    fun conver(byteBuffer: ByteBuffer): ByteArray? {
        val len = byteBuffer.limit() - byteBuffer.position()
        val bytes = ByteArray(len)
        if (byteBuffer.isReadOnly) {
            return null
        } else {
            byteBuffer[bytes]
        }
        return bytes
    }

    fun merge(vararg data: ByteArray?): ByteArray? {
        return if (data == null) {
            null
        } else {
            var bytes: ByteArray? = null
            for (i in data.indices) {
                bytes = mergeBytes(bytes, data[i])
            }
            bytes
        }
    }

    fun mergeBytes(bytesA: ByteArray?, bytesB: ByteArray?): ByteArray? {
        return if (bytesA != null && bytesA.size != 0) {
            if (bytesB != null && bytesB.size != 0) {
                val bytes = ByteArray(bytesA.size + bytesB.size)
                System.arraycopy(bytesA, 0, bytes, 0, bytesA.size)
                System.arraycopy(bytesB, 0, bytes, bytesA.size, bytesB.size)
                bytes
            } else {
                bytesA
            }
        } else {
            bytesB
        }
    }
}