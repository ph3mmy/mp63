package ng.com.plustech.mp63kotlin.utils

import ng.com.plustech.mp63kotlin.models.MasterKey
import ng.com.plustech.mp63kotlin.models.PinKey
import ng.com.plustech.mp63kotlin.models.SessionKey
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*

object ServerUtil {
    fun calculateCMK(masterKey: MasterKey): String {
        val mek = masterKey.MEK
        val mkcv = masterKey.MKCV

        val result = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(Constants.CombinedKey), DUKPK2009_CBC.parseHexStr2Byte((mek)))
        val cmk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("CMK: "+cmk)
        return cmk
    }

    fun calculateCSK(sessionKey: SessionKey, masterKey: MasterKey): String {
        val cmk = calculateCMK(masterKey)

        val sek = sessionKey.SEK
        val skcv = sessionKey.SKCV

        val result = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(cmk), DUKPK2009_CBC.parseHexStr2Byte((sek)))
        val csk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("CSK: $csk")
        return csk
    }

    fun calculateCPK(pinKey: PinKey, masterKey: MasterKey): String {
        val cmk = calculateCMK(masterKey)

        val pkek = pinKey.PKEK
        val pkcv = pinKey.PKCV

        val result = DUKPK2009_CBC.TriDesDecryptionECB(DUKPK2009_CBC.parseHexStr2Byte(cmk), DUKPK2009_CBC.parseHexStr2Byte((pkek)))
        val cpk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("CPK: "+cpk)
        return cpk
    }

    fun calculateESK(sessionKey: SessionKey, masterKey: MasterKey): String {
        val csk = calculateCSK(sessionKey, masterKey)

        //the encrytpion
        val result = DUKPK2009_CBC.TriDesEncryption(DUKPK2009_CBC.parseHexStr2Byte(Constants.EncryptionKey), DUKPK2009_CBC.parseHexStr2Byte(csk))
        val esk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("ESK: "+esk)
        return esk
    }

    fun calculateEPK(pinKey: PinKey, masterKey: MasterKey): String {
        val cpk = calculateCPK(pinKey, masterKey)

        //the encryption
        val result = DUKPK2009_CBC.TriDesEncryption(DUKPK2009_CBC.parseHexStr2Byte(Constants.EncryptionKey), DUKPK2009_CBC.parseHexStr2Byte(cpk))
        val epk = DUKPK2009_CBC.parseByte2HexStr(result)
        System.out.println("EPK: "+epk)
        return epk
    }

    fun generateRandomNumber(length: Int): String {
        var myRand: String? = null
        val parser =  SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        val formatter = SimpleDateFormat("HHmmssddMMyyyy")
        var curDate:String? = null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            curDate = LocalDateTime.now().toString()
            curDate = formatter.format(parser.parse(curDate))
        } else {
            val c = Calendar.getInstance()
            curDate =  c.get(Calendar.HOUR_OF_DAY).toString() + c.get(Calendar.MINUTE).toString() + c.get(Calendar.SECOND).toString() + c.get(Calendar.DAY_OF_MONTH) + c.get(Calendar.MONTH).toString() + c.get(Calendar.YEAR).toString()
        }

        if( length < 14) {
            myRand = curDate?.substring(0, length)!!
        } else {
            curDate += curDate
           myRand = curDate?.substring(0, length)!!
        }

        return myRand
    }
}