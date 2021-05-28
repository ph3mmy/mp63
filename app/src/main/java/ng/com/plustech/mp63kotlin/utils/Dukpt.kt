package ng.com.plustech.mp63kotlin.utils

import java.math.BigInteger
import java.security.InvalidParameterException
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.DESKeySpec
import javax.crypto.spec.DESedeKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import kotlin.experimental.or

/**
 *
 * The Dukpt class acts a name-space for the Derived
 * Unique Key-Per-Transaction (Dukpt) standard using the
 * Data Encryption Standard, DES, (often referred to in practice as
 * "DEA", for Data Encryption Algorithm).
 *
 *
 * The functions provided attempt to aid a user in performing
 * encryption, decryption, and possibly more complex operations
 * using these.
 *
 *
 * There is also a set of conversion methods to hopefully make
 * the class even easier to interface with.  Many of these involve
 * the BitSet wrapper of java.util.BitSet which was designed to have
 * a proper "size()" function as Java's BitSet does not have a method
 * that returns the constructed length of the BitSet, only its actual
 * size in memory and its "logical" size (1 + the index of the left-most 1).
 *
 *
 * To further augment to the security of Dukpt, two "oblivate()" methods are
 * included, one for the extended BitSet and one for byte arrays.  These
 * overwrite their respective arguments with random data as supplied by
 * java.secruty.SecureRandom to ensure that their randomness is
 * cryptographically strong.  The default number of overwrites is specified by
 * the static constant NUM_OVERWRITES but the user can supply a different number
 * should they desire the option.
 *
 * @author Software Verde: Andrew Groot
 * @author Software Verde: Josh Green
 */
object Dukpt {
    const val NUM_OVERWRITES = 3
    const val KEY_REGISTER_BITMASK = "C0C0C0C000000000C0C0C0C000000000"
    private val DEFAULT_KEY_REGISTER_BITMASK: BitSet = Dukpt.toBitSet(Dukpt.toByteArray(Dukpt.KEY_REGISTER_BITMASK))
    const val DATA_VARIANT_BITMASK = "0000000000FF00000000000000FF0000"
    const val MAC_VARIANT_BITMASK = "000000000000FF00000000000000FF00"
    const val PIN_VARIANT_BITMASK = "00000000000000FF00000000000000FF"
    private val DEFAULT_VARIANT_BITMASK: BitSet = Dukpt.toBitSet(Dukpt.toByteArray(Dukpt.PIN_VARIANT_BITMASK))

    /**
     *
     * Computes a DUKPT (Derived Unique Key-Per-Transaction).
     *
     *
     * This is derived from the Base Derivation Key, which should
     * have been injected into the device and should remain secret,
     * and the Key Serial Number which is a concatenation of the
     * device's serial number and its encryption (or transaction)
     * counter.
     *
     * @see .getIpek
     *
     * @param baseDerivationKey The Base Derivation Key
     * @param keySerialNumber The Key Serial Number
     * @return A unique key for this set of data.
     * @throws Exception
     */
    @Throws(Exception::class)
    fun computeKey(baseDerivationKey: ByteArray, keySerialNumber: ByteArray): ByteArray {
        return Dukpt.computeKey(baseDerivationKey, keySerialNumber, Dukpt.DEFAULT_KEY_REGISTER_BITMASK, Dukpt.DEFAULT_VARIANT_BITMASK)
    }

    /**
     *
     * Computes a DUKPT (Derived Unique Key-Per-Transaction) using the provided key register bitmask and data variant
     * bitmask.
     *
     * @see .computeKey
     * @param baseDerivationKey
     * @param keySerialNumber
     * @param keyRegisterBitmask
     * @param dataVariantBitmask
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    internal fun computeKey(baseDerivationKey: ByteArray, keySerialNumber: ByteArray, keyRegisterBitmask: BitSet, dataVariantBitmask: BitSet): ByteArray {
        val bdk: BitSet = Dukpt.toBitSet(baseDerivationKey)
        val ksn: BitSet = Dukpt.toBitSet(keySerialNumber)
        val ipek: BitSet = Dukpt.getIpek(bdk, ksn, keyRegisterBitmask)

        // convert key for returning
        val key: BitSet = Dukpt._getCurrentKey(ipek, ksn, keyRegisterBitmask, dataVariantBitmask)
        val rkey: ByteArray = Dukpt.toByteArray(key)

        // secure memory
        Dukpt.obliviate(ksn)
        Dukpt.obliviate(bdk)
        Dukpt.obliviate(ipek)
        Dukpt.obliviate(key)
        return rkey
    }

    /**
     *
     * Computes the Initial PIN Encryption Key (Sometimes referred to as
     * the Initial PIN Entry Device Key).
     *
     *
     * Within the function, the transaction counter is removed.
     * This is because the IPEK should be seen as the Dukpt
     * (Derived Unique Key-Per-Transaction) corresponding to a brand
     * new transaction counter (assuming it starts at 0).
     *
     *
     * Due to the process under which one key is derived from a subset of
     * those before it, the IPEK can be used to quickly calculate the
     * DUKPT for any Key Serial Number, or more specifically, any
     * encryption count.
     *
     *
     * This algorithm was found in Annex A, section 6 on page 69
     * of the ANSI X9.24-1:2009 document.
     *
     * @param key The Base Derivation Key.
     * @param ksn The Key Serial Number.
     * @return The Initial PIN Encryption Key
     * @throws Exception
     */
    @Throws(Exception::class)
    fun getIpek(key: BitSet, ksn: BitSet): BitSet {
        return Dukpt.getIpek(key, ksn, Dukpt.DEFAULT_KEY_REGISTER_BITMASK)
    }

    /**
     *
     * Computes the Initial PIN Encryption Key using the provided key register bitmask.
     *
     * @see .getIpek
     * @param key
     * @param ksn
     * @param keyRegisterBitmask
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    internal fun getIpek(key: BitSet, ksn: BitSet, keyRegisterBitmask: BitSet?): BitSet {
        val ipek = arrayOfNulls<ByteArray>(2)
        val keyRegister: BitSet = key.get(0, key.size())
        val data: BitSet = ksn.get(0, ksn.size())
        data.clear(59, 80)
        ipek[0] = encryptTripleDes(Dukpt.toByteArray(keyRegister), toByteArray(data.get(0, 64)))
        keyRegister.xor(keyRegisterBitmask)
        ipek[1] = encryptTripleDes(Dukpt.toByteArray(keyRegister), toByteArray(data.get(0, 64)))
        val bipek: ByteArray = Dukpt.concat(ipek[0]!!, ipek[1]!!)
        val bsipek: BitSet = Dukpt.toBitSet(bipek)

        // secure memory
        obliviate(ipek[0]!!)
        obliviate(ipek[1]!!)
        obliviate(bipek)
        Dukpt.obliviate(keyRegister)
        Dukpt.obliviate(data)
        return bsipek
    }

    /**
     *
     * Computes a Dukpt (Derived Unique Key-Per-Transaction) given an IPEK
     * and Key Serial Number.
     *
     *
     * Here, a non-reversible operation is used to find one key from
     * another.  This is where the transaction counter comes in.  In order
     * to have the desired number of possible unique keys (over 1 million)
     * for a given device, a transaction counter size of 20 bits would
     * suffice.  However, by adding an extra bit and a constraint (that
     * keys must have AT MOST 9* bits set) the same number of values can be
     * achieved while allowing a user to calculate the key in at most 9
     * steps.
     *
     *
     * We have reason to believe that is actually 10 bits (as the
     * sum of the 21 choose i for i from 0 to 9 is only around 700,000 while
     * taking i from 0 to 10 yields exactly 2^20 (just over 1,000,000) values)
     * but regardless of the truth, our algorithm is not dependent upon this
     * figure and will work no matter how it is implemented in the encrypting
     * device or application.
     *
     *
     * This algorithm was found in Annex A, section 3 on pages 50-54
     * of the ANSI X9.24-1:2009 document.
     *
     * @param ipek The Initial PIN Encryption Key.
     * @param ksn The Key Serial Number.
     * @return The Dukpt that corresponds to this combination of values.
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun _getCurrentKey(ipek: BitSet, ksn: BitSet, keyRegisterBitmask: BitSet, dataVariantBitmask: BitSet): BitSet {
        var key: BitSet = ipek.get(0, ipek.size())
        val counter: BitSet = ksn.get(0, ksn.size())
        counter.clear(59, ksn.size())
        for (i in 59 until ksn.size()) {
            if (ksn.get(i)) {
                counter.set(i)
                val tmp: BitSet = Dukpt._nonReversibleKeyGenerationProcess(key, counter.get(16, 80), keyRegisterBitmask)
                // secure memory
                Dukpt.obliviate(key)
                key = tmp
            }
        }
        key.xor(dataVariantBitmask) // data encryption variant (e.g. To PIN)

        // secure memory
        Dukpt.obliviate(counter)
        return key
    }

    /**
     *
     * Creates a new key from a previous key and the right 64 bits of the
     * Key Serial Number for the desired transaction.
     *
     *
     * This algorithm was found in Annex A, section 2 on page 50
     * of the ANSI X9.24-1:2009 document.
     *
     * @param p_key The previous key to be used for derivation.
     * @param data The data to encrypt it with, usually the right 64 bits of the transaction counter.
     * @return A key that cannot be traced back to p_key.
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun _nonReversibleKeyGenerationProcess(p_key: BitSet, data: BitSet, keyRegisterBitmask: BitSet): BitSet {
        val keyreg: BitSet = p_key.get(0, p_key.size())
        var reg1: BitSet = data.get(0, data.size())
        // step 1: Crypto Register-1 XORed with the right half of the Key Register goes to Crypto Register-2.
        var reg2: BitSet = reg1.get(0, 64) // reg2 is being used like a temp here
        reg2.xor(keyreg.get(64, 128)) // and here, too, kind of
        // step 2: Crypto Register-2 DEA-encrypted using, as the key, the left half of the Key Register goes to Crypto Register-2
        reg2 = Dukpt.toBitSet(encryptDes(toByteArray(keyreg.get(0, 64)), Dukpt.toByteArray(reg2)))
        // step 3: Crypto Register-2 XORed with the right half of the Key Register goes to Crypto Register-2
        reg2.xor(keyreg.get(64, 128))
        // done messing with reg2

        // step 4: XOR the Key Register with hexadecimal C0C0 C0C0 0000 0000 C0C0 C0C0 0000 0000
        keyreg.xor(keyRegisterBitmask)
        // step 5: Crypto Register-1 XORed with the right half of the Key Register goes to Crypto Register-1
        reg1.xor(keyreg.get(64, 128))
        // step 6: Crypto Register-1 DEA-encrypted using, as the key, the left half of the Key Register goes to Crypto Register-1
        reg1 = Dukpt.toBitSet(encryptDes(toByteArray(keyreg.get(0, 64)), Dukpt.toByteArray(reg1)))
        // step 7: Crypto Register-1 XORed with the right half of the Key Register goes to Crypto Register-1
        reg1.xor(keyreg.get(64, 128))
        // done
        val reg1b: ByteArray = Dukpt.toByteArray(reg1)
        val reg2b: ByteArray = Dukpt.toByteArray(reg2)
        val key: ByteArray = Dukpt.concat(reg1b, reg2b)
        val rkey: BitSet = Dukpt.toBitSet(key)

        // secure memory
        Dukpt.obliviate(reg1)
        Dukpt.obliviate(reg2)
        obliviate(reg1b)
        obliviate(reg2b)
        obliviate(key)
        Dukpt.obliviate(keyreg)
        return rkey
    }
    /**
     *
     * Performs Single DES Encryption.
     *
     * @param key The key for encryption.
     * @param data The data to encrypt.
     * @param padding When true, PKCS5 Padding will be used.  This is most likely not desirable.
     * @return The encrypted.
     * @throws Exception
     */
    /**
     *
     * Performs Single DEA Encryption without padding.
     *
     * @param key The key for encryption.
     * @param data The data to encrypt.
     * @return The encrypted data.
     * @throws Exception
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun encryptDes(key: ByteArray, data: ByteArray, padding: Boolean = false): ByteArray {
        val iv = IvParameterSpec(ByteArray(8))
        val encryptKey = SecretKeyFactory.getInstance("DES").generateSecret(DESKeySpec(key))
        val encryptor: Cipher
        encryptor = if (padding) {
            Cipher.getInstance("DES/CBC/PKCS5Padding")
        } else {
            Cipher.getInstance("DES/CBC/NoPadding")
        }
        encryptor.init(Cipher.ENCRYPT_MODE, encryptKey, iv)
        return encryptor.doFinal(data)
    }

    /**
     *
     * Creates a data key, as described [here](https://idtechproducts.com/support/technical-blog/id/how-to-decrypt-credit-card-data-part-ii/).
     *
     *
     * NOTE: for standard usage, the derived key provided to this method must be generated using the [.DATA_VARIANT_BITMASK].
     *
     *
     * ### The following content is copied from the above-linked page ###
     *
     * <h3>Creating Data, PIN, and MAC Key Variants</h3>
     *
     *
     * ANSI X9.24 allows a DUKPT key to take on one of three final forms, called variants. The forms are MAC, PIN,
     * and Data. Let’s defer any discussion of what these various key types are used for in order to concentrate on how
     * they’re created.
     *
     *
     * The starting point for any of the variants is a DUKPT basis key (the derived key that we called curKey in Step
     * 5 further above). To get the MAC variant, you simply need to XOR the basis key (the “derived key”) with a special
     * constant:
     *
     * <pre>MACkey = derivedKey ^ 0x000000000000FF00000000000000FF00;</pre>
     *
     * The PIN variant, likewise, is created in similar fashion, but using a different constant:
     *
     * <pre>PINkey = derivedKey ^ 0x00000000000000FF00000000000000FF;</pre>
     *
     * The Data variant requires yet another constant:
     *
     * <pre>Datakey = derivedKey ^ 0x0000000000FF00000000000000FF0000;</pre>
     *
     * For MAC and PIN variants, the XOR operation constitutes the final step in creating the relevant session key. For the Data variant, it’s customary to perform one additional step, involving a one-way hash (to preclude any possibility of someone back-transforming a Data key into a MAC key). In pseudocode:
     *
     * <pre>
     * // left half:
     * var left = des(  EDE3KeyExpand( derivedKey ),
     * top8bytes( derivedKey ),
     * true,
     * CBC,
     * iv );
     *
     * // right half:
     * var right = des( EDE3KeyExpand( derivedKey ),
     * bottom8bytes( derivedKey ),
     * true,
     * CBC,
     * iv );
     *
     * finalDataKey = (left << 64) | right;  // combine halves
    </pre> *
     *
     *
     * In English: First, obtain a 24-byte version of your derived key, by using the EDE3 expansion method.
     * (This simply means copying the first 8 bytes of a 16-byte key onto the tail end of the key, creating a 24-byte
     * key in which the first and last 8 bytes are the same.) Use that key to TDES-encrypt the first 8 bytes of your
     * 16-byte derived key, thereby creating an 8-byte cipher. That’s the left half of the eventual data key. To create
     * the right half, use the same 24-byte key to encrypt the bottom 8 bytes of the derivedKey. Combine the two 8-byte
     * ciphers (left and right pieces), and you’re done.
     *
     * @param derivedKey
     * @return
     */
    @Throws(Exception::class)
    fun toDataKey(derivedKey: ByteArray): ByteArray {
        require(!(derivedKey == null || derivedKey.size != 16)) { "Invalid key provided: " + if (derivedKey == null) "null" else "length " + derivedKey.size }
        val left = Arrays.copyOfRange(derivedKey, 0, 8)
        val right = Arrays.copyOfRange(derivedKey, 8, 16)
        val leftEncrypted = encryptTripleDes(derivedKey, left)
        val rightEncrypted = encryptTripleDes(derivedKey, right)
        val dataKey: ByteArray = Dukpt.concat(leftEncrypted, rightEncrypted)
        obliviate(left)
        obliviate(right)
        obliviate(leftEncrypted)
        obliviate(rightEncrypted)
        return dataKey
    }
    /**
     *
     * Performs Single DES Decryption.
     *
     * @param key The key for decryption.
     * @param data The data to decrypt.
     * @param padding When true, PKCS5 Padding will be assumed.  This is most likely not desirable.
     * @return The decrypted data.
     * @throws Exception
     */
    /**
     *
     * Performs Single DES Decryption assuming no padding was used.
     *
     * @param key The key for decryption.
     * @param data The data to decrypt.
     * @return The decrypted data.
     * @throws Exception
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun decryptDes(key: ByteArray, data: ByteArray, padding: Boolean = false): ByteArray {
        val iv = IvParameterSpec(ByteArray(8))
        val decryptKey = SecretKeyFactory.getInstance("DES").generateSecret(DESKeySpec(key))
        val decryptor: Cipher
        decryptor = if (padding) {
            Cipher.getInstance("DES/CBC/PKCS5Padding")
        } else {
            Cipher.getInstance("DES/CBC/NoPadding")
        }
        decryptor.init(Cipher.DECRYPT_MODE, decryptKey, iv)
        return decryptor.doFinal(data)
    }
    /**
     *
     * Performs Triple DES Encryption.
     *
     * @param key The key for encryption.
     * @param data The data to encrypt.
     * @param padding When true, PKCS5 Padding will be used.  This is most likely not desirable.
     * @return The encrypted data.
     * @throws Exception
     */
    /**
     *
     * Performs Single DEA Encryption without padding.
     *
     * @param key The key for encryption.
     * @param data The data to encrypt.
     * @return The encrypted data.
     * @throws Exception
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun encryptTripleDes(key: ByteArray, data: ByteArray, padding: Boolean = false): ByteArray {
        val bskey: BitSet = Dukpt.toBitSet(key)
        val k1: BitSet
        val k2: BitSet
        val k3: BitSet
        if (bskey.size() === 64) {
            // single length
            k1 = bskey.get(0, 64)
            k2 = k1
            k3 = k1
        } else if (bskey.size() === 128) {
            // double length
            k1 = bskey.get(0, 64)
            k2 = bskey.get(64, 128)
            k3 = k1
        } else {
            // triple length
            if (bskey.size() !== 192) {
                throw InvalidParameterException("Key is not 8/16/24 bytes long.")
            }
            k1 = bskey.get(0, 64)
            k2 = bskey.get(64, 128)
            k3 = bskey.get(128, 192)
        }
        val kb1: ByteArray = Dukpt.toByteArray(k1)
        val kb2: ByteArray = Dukpt.toByteArray(k2)
        val kb3: ByteArray = Dukpt.toByteArray(k3)
        val key16: ByteArray = Dukpt.concat(kb1, kb2)
        val key24: ByteArray = Dukpt.concat(key16, kb3)
        val iv = IvParameterSpec(ByteArray(8))
        val encryptKey = SecretKeyFactory.getInstance("DESede").generateSecret(DESedeKeySpec(key24))
        val encryptor: Cipher
        encryptor = if (padding) {
            Cipher.getInstance("DESede/CBC/PKCS5Padding")
        } else {
            Cipher.getInstance("DESede/CBC/NoPadding")
        }
        encryptor.init(Cipher.ENCRYPT_MODE, encryptKey, iv)
        val bytes = encryptor.doFinal(data)

        // secure memory
        Dukpt.obliviate(k1)
        Dukpt.obliviate(k2)
        Dukpt.obliviate(k3)
        obliviate(kb1)
        obliviate(kb2)
        obliviate(kb3)
        obliviate(key16)
        obliviate(key24)
        Dukpt.obliviate(bskey)
        return bytes
    }
    /**
     *
     * Performs Triple DES Decryption.
     *
     * @param key The key for decryption.
     * @param data The data to decrypt.
     * @param padding When true, PKCS5 Padding will be assumed.  This is most likely not desirable.
     * @return The decrypted data.
     * @throws Exception
     */
    /**
     * Performs Triple DEA Decryption without padding.
     *
     * @param key The key for decryption.
     * @param data The data to decrypt.
     * @return The decrypted data.
     * @throws Exception
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun decryptTripleDes(key: ByteArray, data: ByteArray, padding: Boolean = false): ByteArray {
        val bskey: BitSet = Dukpt.toBitSet(key)
        val k1: BitSet
        val k2: BitSet
        val k3: BitSet
        if (bskey.size() === 64) {
            // single length
            k1 = bskey.get(0, 64)
            k2 = k1
            k3 = k1
        } else if (bskey.size() === 128) {
            // double length
            k1 = bskey.get(0, 64)
            k2 = bskey.get(64, 128)
            k3 = k1
        } else {
            // triple length
            if (bskey.size() !== 192) {
                throw InvalidParameterException("Key is not 8/16/24 bytes long.")
            }
            k1 = bskey.get(0, 64)
            k2 = bskey.get(64, 128)
            k3 = bskey.get(128, 192)
        }
        val kb1: ByteArray = Dukpt.toByteArray(k1)
        val kb2: ByteArray = Dukpt.toByteArray(k2)
        val kb3: ByteArray = Dukpt.toByteArray(k3)
        val key16: ByteArray = Dukpt.concat(kb1, kb2)
        val key24: ByteArray = Dukpt.concat(key16, kb3)
        val iv = IvParameterSpec(ByteArray(8))
        val encryptKey = SecretKeyFactory.getInstance("DESede").generateSecret(DESedeKeySpec(key24))
        val decryptor: Cipher
        decryptor = if (padding) Cipher.getInstance("DESede/CBC/PKCS5Padding") else Cipher.getInstance("DESede/CBC/NoPadding")
        decryptor.init(Cipher.DECRYPT_MODE, encryptKey, iv)
        val bytes = decryptor.doFinal(data)

        // secure memory
        Dukpt.obliviate(k1)
        Dukpt.obliviate(k2)
        Dukpt.obliviate(k3)
        obliviate(kb1)
        obliviate(kb2)
        obliviate(kb3)
        obliviate(key16)
        obliviate(key24)
        Dukpt.obliviate(bskey)
        return bytes
    }
    /**
     *
     * Performs Single AES Encryption.
     *
     *
     * This is supplied for use generic encryption and decryption purposes, but is not a part of the Dukpt algorithm.
     *
     * @param key The key for encryption.
     * @param data The data to encrypt.
     * @param padding When true, PKCS5 Padding will be used.  This is most likely not desirable.
     * @return The encrypted.
     * @throws Exception
     */
    /**
     *
     * Performs Single AES Encryption without padding.
     *
     *
     * This is supplied for use generic encryption and decryption purposes, but is not a part of the Dukpt algorithm.
     *
     * @param key The key for encryption.
     * @param data The data to encrypt.
     * @return The encrypted data.
     * @throws Exception
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun encryptAes(key: ByteArray, data: ByteArray, padding: Boolean = false): ByteArray {
        val iv = IvParameterSpec(ByteArray(16))
        val encryptKey = SecretKeySpec(key, "AES")
        val encryptor: Cipher
        encryptor = if (padding) {
            Cipher.getInstance("AES/CBC/PKCS5Padding")
        } else {
            Cipher.getInstance("AES/CBC/NoPadding")
        }
        encryptor.init(Cipher.ENCRYPT_MODE, encryptKey, iv)
        return encryptor.doFinal(data)
    }
    /**
     *
     * Performs Single AES Decryption.
     *
     *
     * This is supplied for use generic encryption and decryption purposes, but is not a part of the Dukpt algorithm.
     *
     * @param key The key for decryption.
     * @param data The data to decrypt.
     * @param padding When true, PKCS5 Padding will be assumed.  This is most likely not desirable.
     * @return The decrypted data.
     * @throws Exception
     */
    /**
     *
     * Performs Triple AES Decryption without padding.
     *
     *
     * This is supplied for use generic encryption and decryption purposes, but is not a part of the Dukpt algorithm.
     *
     * @param key The key for decryption.
     * @param data The data to decrypt.
     * @return The decrypted data.
     * @throws Exception
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun decryptAes(key: ByteArray, data: ByteArray, padding: Boolean = false): ByteArray {
        val iv = IvParameterSpec(ByteArray(16))
        val decryptKey = SecretKeySpec(key, "AES")
        val decryptor: Cipher
        decryptor = if (padding) {
            Cipher.getInstance("AES/CBC/PKCS5Padding")
        } else {
            Cipher.getInstance("AES/CBC/NoPadding")
        }
        decryptor.init(Cipher.DECRYPT_MODE, decryptKey, iv)
        return decryptor.doFinal(data)
    }

    /**
     *
     * Converts a byte into an extended BitSet.
     */
    fun toBitSet(b: Byte): BitSet {
        val bs = BitSet(8)
        for (i in 0..7) {
            if (b and ((1L shl i).toByte()) > 0) {
                bs.set(7 - i)
            }
        }
        return bs
    }

    /**
     *
     * Converts a byte array to an extended BitSet.
     */
    fun toBitSet(b: ByteArray): BitSet {
        val bs = BitSet(8 * b.size)
        for (i in b.indices) {
            for (j in 0..7) {
                if (b[i] and ((1L shl j).toByte()) > 0) {
                    bs.set(8 * i + (7 - j))
                }
            }
        }
        return bs
    }

    /**
     *
     * Converts an extended BitSet into a byte.
     *
     *
     * Requires that the BitSet be exactly 8 bits long.
     */
    fun toByte(b: BitSet): Byte {
        var value: Byte = 0
        for (i in 0 until b.size()) {
            if (b.get(i)) value = (value or ((1L shl 7 - i).toByte())) as Byte
        }
        return value
    }

    /**
     *
     * Converts a BitSet into a byte array.
     *
     *
     * Pads to the left with zeroes.
     *
     *
     * Note: this is different from [BitSet.toByteArray].
     */
    fun toByteArray(b: BitSet): ByteArray {
        val size = Math.ceil(b.size() / 8.0).toInt()
        val value = ByteArray(size)
        for (i in 0 until size) {
            value[i] = Dukpt.toByte(b.get(i * 8, Math.min(b.size(), (i + 1) * 8)))
        }
        return value
    }

    /**
     *
     * Converts a hexadecimal String into a byte array (Big-Endian).
     *
     * @param s A representation of a hexadecimal number without any leading qualifiers such as "0x" or "x".
     */
    fun toByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     *
     * Converts a byte array into a hexadecimal string (Big-Endian).
     *
     * @return A representation of a hexadecimal number without any leading qualifiers such as "0x" or "x".
     */
    fun toHex(bytes: ByteArray): String {
        val bi = BigInteger(1, bytes)
        return String.format("%0" + (bytes.size shl 1) + "X", bi)
    }

    /**
     *
     * Concatenates two byte arrays.
     *
     * @return The array a concatenated with b.  So if r is the returned array, r[0] = a[0] and r[a.length] = b[0].
     */
    fun concat(a: ByteArray, b: ByteArray): ByteArray {
        val c = ByteArray(a.size + b.size)
        for (i in a.indices) {
            c[i] = a[i]
        }
        for (i in b.indices) {
            c[a.size + i] = b[i]
        }
        return c
    }

    /**
     *
     * Overwrites the extended BitSet NUM_OVERWRITES times with random data for security purposes.
     */
    fun obliviate(b: BitSet) {
        Dukpt.obliviate(b, Dukpt.NUM_OVERWRITES)
    }

    /**
     *
     * Overwrites the extended BitSet with random data for security purposes.
     */
    fun obliviate(b: BitSet, n: Int) {
        val r = SecureRandom()
        for (i in 0 until Dukpt.NUM_OVERWRITES) {
            for (j in 0 until b.size()) {
                b.set(j, r.nextBoolean())
            }
        }
    }
    /**
     *
     * Overwrites the byte array with random data for security purposes.
     */
    /**
     *
     * Overwrites the byte array NUM_OVERWRITES times with random data for security purposes.
     */
    @JvmOverloads
    fun obliviate(b: ByteArray, n: Int = Dukpt.NUM_OVERWRITES) {
        for (i in 0 until n) {
            b[i] = 0x00
            b[i] = 0x01
        }
        val r = SecureRandom()
        for (i in 0 until n) {
            r.nextBytes(b)
        }
    }
}
