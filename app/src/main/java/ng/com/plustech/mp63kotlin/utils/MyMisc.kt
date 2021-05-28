package ng.com.plustech.mp63kotlin.utils

import kotlin.experimental.and
import kotlin.experimental.xor

class MyMisc() {

    companion object {
        @JvmStatic
        fun xor(key1: String, key2: String): String? {
            var result: String? = ""
            val arr1: ByteArray? = parseHexStr2Byte(key1)
            val arr2: ByteArray? = parseHexStr2Byte(key2)
            val arr3 = arr1?.let { ByteArray(it.size) }
            if (arr1 != null) {
                for (i in arr1.indices) {
                    arr3!![i] = (arr1[i] xor arr2!![i]) as Byte
                }
            }
            result = arr3?.let { parseByte2HexStr(it) }
            return result
        }

        fun parseHexStr2Byte(hexStr: String): ByteArray? {
            if (hexStr.length < 1) return null
            val result = ByteArray(hexStr.length / 2)
            for (i in 0 until hexStr.length / 2) {
                val high = hexStr.substring(i * 2, i * 2 + 1).toInt(16)
                val low = hexStr.substring(i * 2 + 1, i * 2 + 2).toInt(
                        16)
                result[i] = (high * 16 + low).toByte()
            }
            return result
        }

        fun parseByte2HexStr(buf: ByteArray): String? {
            val sb = StringBuffer()
            for (i in buf.indices) {
                var hex = Integer.toHexString((buf[i] and 0xFF.toByte()).toInt())
                if (hex.length == 1) {
                    hex = "0$hex"
                }
                sb.append(hex.toUpperCase())
            }
            return sb.toString()
        }

        fun GenerateIPEK(ksn: ByteArray?, bdk: ByteArray?): ByteArray? {
            val result: ByteArray
            val temp: ByteArray
            var temp2: ByteArray?
            val keyTemp: ByteArray
            result = ByteArray(16)
            temp = ByteArray(8)
            keyTemp = ByteArray(16)

//        Array.Copy(bdk, keyTemp, 16);
            System.arraycopy(bdk, 0, keyTemp, 0, 16) //Array.Copy(bdk, keyTemp, 16);
            //        Array.Copy(ksn, temp, 8);
            System.arraycopy(ksn, 0, temp, 0, 8) //Array.Copy(ksn, temp, 8);
            temp[7] = temp[7] and 0xE0.toByte()
            //        TDES_Enc(temp, keyTemp, out temp2);
            temp2 = Dukpt.encryptTripleDes(keyTemp, temp)
            //temp2 = TripleDES.encrypt(temp, keyTemp) //TDES_Enc(temp, keyTemp, out temp2);temp
            //        Array.Copy(temp2, result, 8);
            System.arraycopy(temp2, 0, result, 0, 8) //Array.Copy(temp2, result, 8);
            keyTemp[0] = keyTemp[0] xor 0xC0.toByte()
            keyTemp[1] = keyTemp[1] xor 0xC0.toByte()
            keyTemp[2] = keyTemp[2] xor 0xC0.toByte()
            keyTemp[3] = keyTemp[3] xor 0xC0.toByte()
            keyTemp[8] = keyTemp[8] xor 0xC0.toByte()
            keyTemp[9] = keyTemp[9] xor 0xC0.toByte()
            keyTemp[10] = keyTemp[10] xor 0xC0.toByte()
            keyTemp[11] = keyTemp[11] xor 0xC0.toByte()
            //        TDES_Enc(temp, keyTemp, out temp2);
            temp2 = Dukpt.encryptTripleDes(keyTemp, temp)
            //temp2 = TripleDES.encrypt(temp, keyTemp) //TDES_Enc(temp, keyTemp, out temp2);
            //        Array.Copy(temp2, 0, result, 8, 8);
            System.arraycopy(temp2, 0, result, 8, 8) //Array.Copy(temp2, 0, result, 8, 8);
            return result
        }
    }
}