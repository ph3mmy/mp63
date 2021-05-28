package ng.com.plustech.mp63kotlin

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ng.com.plustech.mp63kotlin.interfaces.RetrofitInterface
import ng.com.plustech.mp63kotlin.repository.ServerRepository
import ng.com.plustech.mp63kotlin.retrofit.RetrofitClient
import ng.com.plustech.mp63kotlin.utils.Constants
import ng.com.plustech.mp63kotlin.utils.DUKPK2009_CBC
import ng.com.plustech.mp63kotlin.utils.ServerUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDateTime

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    val pkek = "AC5E0AC03BD1544E6DE71D6CDF73A00F"
    val pkcv = "EB74B9"
    val mek = "20DC4ABFE7119CA339EAD965A18A803F"
    val mkcv = "5F0C02"
    val sek = "F93071129118A40B8C48097D6FFAA569"
    val skcv = "DDEFD4"


    @Test
    fun addition_isCorrect() {

//        byte[] re = DUKPK2009_CBC.GetDUKPTKey(ksn.getBytes(), bdk.getBytes());
//        System.out.println(DUKPK2009_CBC.parseByte2HexStr(re));
        val pinKsn = "FFFF9876543210000007"
        val pinBlock = "969EB6D08CE2C5F5"
        val realPan = "5399831639539028"
        val date: String = DUKPK2009_CBC.getDate(
            pinKsn,
            pinBlock,
            DUKPK2009_CBC.Enum_key.PIN,
            DUKPK2009_CBC.Enum_mode.CBC
        )
        val parsCarN = "0000" + realPan.substring(realPan.length - 13, realPan.length - 1)
        val s: String = DUKPK2009_CBC.xor(parsCarN, date)
        println("PIN: $s\n")

        assertEquals(4, 2 + 2)
    }

    @Test
    fun decryptMEKDownloadedFromServer() {
        //Step 1
        //This requires the combined key
        //the result is cmk
        val cmk = calculateCMK()
        System.out.println(cmk)

        assertEquals(4, 2 + 2)
    }

    @Test
    fun calculateCMKTest() {
        val result = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(Constants.CombinedKey), DUKPK2009_CBC.parseHexStr2Byte((mek)))
        val cmk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("CMK: "+cmk)
        assertEquals(1, 1)
    }

    fun calculateCMK(): String {

        val result = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(Constants.CombinedKey), DUKPK2009_CBC.parseHexStr2Byte((mek)))
        val cmk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("CMK: "+cmk)
        return cmk
    }

    @Test
    fun calculateCSKTest() {
        val cmk = calculateCMK()

        val result = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(cmk), DUKPK2009_CBC.parseHexStr2Byte((sek)))
        val csk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("CSK: $csk")
        assertEquals(1, 1)
    }

    fun calculateCSK(): String {
        val cmk = calculateCMK()

        val result = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(cmk), DUKPK2009_CBC.parseHexStr2Byte((sek)))
        val csk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("CSK: $csk")
        return csk
    }

    @Test
    fun calculateCPKTest() {
        val cmk = calculateCMK()

        val result = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(cmk), DUKPK2009_CBC.parseHexStr2Byte((pkek)))
        val cpk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("CPK: "+cpk)
        assertEquals(1, 1)
    }

    fun calculateCPK(): String {
        val cmk = calculateCMK()

        val result = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(cmk), DUKPK2009_CBC.parseHexStr2Byte((pkek)))
        val cpk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("CPK: "+cpk)
        return cpk
    }

    fun calculateESK(): String {
        val csk = calculateCSK()
        val result = DUKPK2009_CBC.TriDesEncryption(DUKPK2009_CBC.parseHexStr2Byte(Constants.EncryptionKey), DUKPK2009_CBC.parseHexStr2Byte(csk))
        val esk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("ESK: "+esk)
        return esk
    }

    @Test
    fun decryptDownloadedSessionKey() {

        //System.out.println(csk)

        assertEquals(4, 2 + 2)
    }

    @Test
    fun decryptDownloadedPINKey() {
        val res = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(Constants.CombinedKey), DUKPK2009_CBC.parseHexStr2Byte((mek)))
        val cmk = DUKPK2009_CBC.parseByte2HexStr(res)

        val result = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(cmk), DUKPK2009_CBC.parseHexStr2Byte((pkek)))
        val cpk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println(cpk)

        assertEquals(4, 2 + 2)
    }

    @Test
    fun encryptCSK() {
        val cmk = calculateCMK()
        val csk = calculateCSK()
        println("CSK: $csk")

        //the encryption
        val esk = calculateESK()
        System.out.println("ESK: $esk")

        assertEquals(4, 2 + 2)
    }

    @Test
    fun encryptCPK() {
        val cmk = calculateCMK()
        val csk = calculateCSK()
        val cpk = calculateCPK()
        System.out.println("CPK: "+cpk)

        //the encryption
        val result = DUKPK2009_CBC.TriDesEncryption(DUKPK2009_CBC.parseHexStr2Byte(Constants.EncryptionKey), DUKPK2009_CBC.parseHexStr2Byte(cpk))
        val epk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("EPK: "+epk)

        assertEquals(4, 2 + 2)
    }

    @Test
    fun testServerCall() {
        val retrofitInterface: RetrofitInterface? = RetrofitClient.retrofitInterface
        val serverRepository = retrofitInterface?.let { ServerRepository(it) }
        val myScope = GlobalScope
        runBlocking {
            myScope.launch {
                val result = serverRepository?.getMasterKey()

                println(result)
            }
        }


    }

    @Test
    fun dateTest() {
        val parser =  SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        val formatter = SimpleDateFormat("HHmmssddMMyyyy")
        val curDate = LocalDateTime.now().toString()
        val formattedDate = formatter.format(parser.parse(curDate))
        println(curDate)
        println(formattedDate)
    }

    @Test
    fun randomNumberTest() {
        val r = ServerUtil.generateRandomNumber(20);
        println(r)
    }
}