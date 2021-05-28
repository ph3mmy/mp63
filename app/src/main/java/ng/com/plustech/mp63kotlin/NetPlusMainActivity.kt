package ng.com.plustech.mp63kotlin

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.gson.Gson
import com.mf.mpos.pub.CommEnum
import com.mf.mpos.pub.CommEnum.KEYINDEX
import com.mf.mpos.pub.Controler
import com.mf.mpos.pub.EmvTagDef
import com.mf.mpos.pub.param.ReadCardParam
import com.mf.mpos.pub.param.ReadCardParam.onStepListener
import com.mf.mpos.pub.result.ReadCardResult
import com.mf.mpos.pub.result.ReadPosInfoResult
import com.mf.mpos.util.Misc
import com.netpluspay.nibssclient.models.*
import com.netpluspay.nibssclient.service.NibssApiWrapper
import com.netpluspay.nibssclient.util.app.NibssClient
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import ng.com.plustech.mp63kotlin.adapters.BluetoothListAdapter
import ng.com.plustech.mp63kotlin.databinding.ActivityMainBinding
import ng.com.plustech.mp63kotlin.interfaces.RetrofitInterface
import ng.com.plustech.mp63kotlin.models.*
import ng.com.plustech.mp63kotlin.repository.ServerRepository
import ng.com.plustech.mp63kotlin.retrofit.RetrofitClient
import ng.com.plustech.mp63kotlin.utils.*
import ng.com.plustech.mp63kotlin.viewmodels.MainActivityViewModel
import ng.com.plustech.mp63kotlin.viewmodels.MainActivityViewModelFactory
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec


class NetPlusMainActivity : AppCompatActivity() {
    val PREFS_NAME = "ng.com.plustech.mp63kotlin.SHARED_PREFERENCES"
    val REQUEST_ENABLE_BT = 1
    private lateinit var binding: ActivityMainBinding

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var mAdapter: BluetoothListAdapter
    private lateinit var serverRepository: ServerRepository
    private lateinit var retrofitInterface: RetrofitInterface
    private lateinit var viewModel: MainActivityViewModel
    private val compositeDisposable = CompositeDisposable()

    var amount: Long = 0
    var keyDownloadInitCount: Int = 0
    var unknownDeviceCounter: Int = 1
    var redFlag = false //this is used to determine if the key downloads were successful and transaction can be processed

    private lateinit var esk: String
    private lateinit var skcv: String
    private lateinit var epk: String
    private lateinit var pkcv: String
    private lateinit var serverParams: ParameterResponse
    private lateinit var masterKey: MasterKey
    private lateinit var sessionKey: SessionKey
    private lateinit var pinKey: PinKey

    private lateinit var sharedPreferences: SharedPreferences


    //card read result holder
    private var cardResult: ReadCardResult? = null
    private val TAG = "MainActivity"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        retrofitInterface = RetrofitClient.retrofitInterface!!
        serverRepository = ServerRepository(retrofitInterface!!).getInstance()!!
        viewModel = ViewModelProvider(this, MainActivityViewModelFactory(serverRepository)).get(
            MainActivityViewModel::class.java
        )

        /*sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        esk = sharedPreferences.getString("esk", "").toString()
        epk = sharedPreferences.getString("epk", "").toString()
        skcv = sharedPreferences.getString("skcv", "").toString()
        pkcv = sharedPreferences.getString("pkcv", "").toString()

        val masterKeyString = sharedPreferences.getString("masterKey", "").toString()

        Log.e("MasterKeyString", masterKeyString);
        masterKey = if(masterKeyString != "") {
            val mkObj = JSONObject(masterKeyString)
            MasterKey(mkObj.getString("status"), mkObj.getString("MEK"), mkObj.getString("MKCV"))
        } else {
            MasterKey("", "", "")
        }

        val sessionKeyString = sharedPreferences.getString("sessionKey", "").toString()
        sessionKey = if(sessionKeyString != "" ) {
            val mkObj = JSONObject(sessionKeyString)
            SessionKey(mkObj.getString("status"), mkObj.getString("SEK"), mkObj.getString("SKCV"))
        } else {
            SessionKey("", "", "")
        }

        val pinKeyString = sharedPreferences.getString("pinKey", "").toString()
        pinKey = if(pinKeyString != "" ) {
            val mkObj = JSONObject(pinKeyString)
            PinKey(mkObj.getString("status"), mkObj.getString("PKEK"), mkObj.getString("PKCV"))
        } else {
            PinKey("", "", "")
        }

        val parameterResponseString = sharedPreferences.getString("serverParam", "").toString()
        serverParams = if(parameterResponseString != "" ) {
            val mkObj = JSONObject(parameterResponseString)
            ParameterResponse(
                mkObj.getString("status"),
                mkObj.getString("CA"),
                mkObj.getString("CAL"),
                mkObj.getString(
                    "CC"
                ),
                mkObj.getString("MT")
            )
        } else {
            ParameterResponse("", "", "", "", "")
        }*/

        initNetPlus()
//        keyDownloadInit()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(
                        this@NetPlusMainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) !==
                    PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this@NetPlusMainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )) {
                        ActivityCompat.requestPermissions(
                            this@NetPlusMainActivity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
                        )
                    } else {
                        ActivityCompat.requestPermissions(
                            this@NetPlusMainActivity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
                        )
                    }
                }
            }
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            SweetDialogUtils.changeAlertType(
                this@NetPlusMainActivity,
                "Device does not support Bluetooth",
                SweetAlertDialog.ERROR_TYPE
            )
        } else {
            if (bluetoothAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        }

        //Initialize the MPOS Device SDK. 6 stands for Manufacturer ID
        Controler.Init(this, CommEnum.CONNECTMODE.BLUETOOTH, 6)

        //Bluetooth Adapter configuration
        mAdapter = BluetoothListAdapter(this) { selectedDevice: Device ->
            deviceHasBeenSelected(selectedDevice)
        }
        binding.listView.adapter = mAdapter
        binding.listView.isFastScrollAlwaysVisible = true

        viewEntryPoint()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter(BLUETOOTH_SERVICE)
        )

        binding.checkConnectionBtn.setOnClickListener { checkDeviceConnection() }

        binding.disconnectDeviceBtn.setOnClickListener { disconnectDevice() }

        binding.enterAmountBtn.setOnClickListener { enterAmount() }

        binding.payBtn.setOnClickListener { pay() }

        binding.loadKeysBtn.setOnClickListener {
            downloadKey()
//            loadTestKeys()
        }

        //downloadKeysFromServer()
    }

    // step 1
    private fun initNetPlus() {
        NibssClient.init("epms-client.cert.pem", "epms-client.key.pem")
        NibssClient.useTestEnvironment(true)
        NibssClient.useSSL(true)
    }

    // step 2
    private fun downloadKey() {
        if (Controler.posConnected()) {
            SweetDialogUtils.showProgress(this@NetPlusMainActivity, "Downloading Key...", false)
            val params =
                ConfigurationParams(NetPlusConstants.terminalId, NetPlusConstants.terminalSerial)
            compositeDisposable.addAll(
                NibssApiWrapper.configureTerminal(this, params)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { t1, t2 ->
                        Log.e(TAG, "t1 == ${Gson().toJson(t1)} \nT2 == $t2")
                        t1?.let { keyHolder ->
                            loadKeyIntoDevice(keyHolder)
                        }
                        t2?.let {
                            SweetDialogUtils.changeAlertType(
                                this@NetPlusMainActivity,
                                "Error: ${it.message}",
                                SweetAlertDialog.ERROR_TYPE
                            )
                            Log.e(TAG, "exceptionnn == ${t2.message}")
                        }
                    }
            )
        } else {
            checkDeviceConnection()

        }
    }

    private fun loadKeyIntoDevice(keyHolder: KeyHolder) {
        loadAllKeysToDevice(keyHolder)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    if ((ContextCompat.checkSelfPermission(
                            this@NetPlusMainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) ===
                                PackageManager.PERMISSION_GRANTED)
                    ) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(resultCode == Activity.RESULT_OK) {
            Log.e("Switching on Bluetooth", "Result OK")
        } else {
            Log.e("Switching on Bluetooth", "Result NOT OK")
        }
        super.onActivityResult(requestCode, resultCode, data)
        Log.e("Switching On Bluetooth", "ResultCode: $resultCode")
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            when(action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        var deviceName = device.name
                        if (deviceName == null) {
                            deviceName = "Unknown Device $unknownDeviceCounter"
                            unknownDeviceCounter++;
                        }
                        val deviceHardwareAddress = device.address // MAC address

                        Log.e(TAG, "Device Found: $deviceName")

                        mAdapter.addItem(Device(deviceName, deviceHardwareAddress, false))
                        mAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    // step 4
    private fun sendTransactionToServer(amountLong: Long, card: CardData) {
        runOnUiThread {
            Log.e("Card Details", card.toString())
            binding.cardResult.text = "Sending Transaction..."
            SweetDialogUtils.showProgress(this@NetPlusMainActivity, "Sending Transaction...", false)

            val paymentParams = MakePaymentParams(NetPlusConstants.terminalId, amountLong, 0L, card)
            Log.e(TAG, "payPARAMS = ${Gson().toJson(paymentParams)}")
            compositeDisposable.add(
                NibssApiWrapper.makePayment(this, paymentParams)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { t1, t2 ->
                        t1?.let {
                            SweetDialogUtils.changeAlertType(
                                this@NetPlusMainActivity,
                                "Transaction Completed. Response: $it",
                                SweetAlertDialog.SUCCESS_TYPE
                            )
                            Log.e(TAG, "successful transaction == ${Gson().toJson(it)}")
                        }
                        t2?.let {
                            SweetDialogUtils.changeAlertType(
                                this@NetPlusMainActivity,
                                "Error: ${it.message}",
                                SweetAlertDialog.ERROR_TYPE
                            )
                            binding.cardResult.text = "Error: ${it.message}"
                        }
                    }
            )
        }
    }

    private fun viewEntryPoint() {
        if (Controler.posConnected()) {
            //show result UI
            toggleBetweenDeviceListAndCardResult("result")
        } else {
            //show listview to display paired devices
            toggleBetweenDeviceListAndCardResult("devicelist")

            //call search for bluetooth
            startDiscovery()
        }
    }

    private fun deviceHasBeenSelected(device: Device) {
        Log.e("Selected Device", device.toString())
        if (device.selected) {
            connectDevice(device)
        }
    }

    private fun startDiscovery() {
        if(bluetoothAdapter != null) {
            //search for paired devices
            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter!!.bondedDevices
            for (device in pairedDevices) {
                mAdapter.addItem(Device(device.name, device.address, false))
            }
            //search for new devices
            // Register for broadcasts when a device is discovered.
            Log.e(TAG, "Scanning for bluetooth devices")

            try {
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                this@NetPlusMainActivity.registerReceiver(receiver, filter)
                bluetoothAdapter?.startDiscovery()
            } catch (e: Exception) {
                Log.e(TAG, "Error registering bluetooth broadcast receiver.")
            }
            //this@MainActivity.unregisterReceiver(receiver)
            mAdapter.notifyDataSetChanged()
        } else {
            SweetDialogUtils.showError(
                this@NetPlusMainActivity,
                "Device does not support Bluetooth"
            )
        }
    }

    private fun setEMVTLVParams() {
        //Controler.SetEmvParamTlv(Constants.EmvUseOnlinePin)
        Controler.SetEmvParamTlv(Constants.CountryTAG)
        Controler.SetEmvParamTlv(Constants.CurrencyTAG)
    }

    private fun connectDevice(device: Device) {
        SweetDialogUtils.showProgress(this@NetPlusMainActivity, "Connecting...", false)
        GlobalScope.launch {
            if (Controler.posConnected()) {
                Controler.disconnectPos()
            }
            val ret = Controler.connectPos(device.address)

            if (ret.bConnected) {
                if (Controler.posConnected()) {
                    bluetoothAdapter?.cancelDiscovery()
                    SweetDialogUtils.changeAlertType(
                        this@NetPlusMainActivity,
                        "Device Connected Successfully.",
                        SweetAlertDialog.SUCCESS_TYPE
                    )

                    val r: ReadPosInfoResult = Controler.ReadPosInfo()
                    Log.e("POS DETAILS", "S/N: ${r.sn}, Model: ${r.model}")
                    Log.e(
                        "POS DETAILS2",
                        "Init Status: ${r.initStatus}, CustomInfo ${r.customInfo}"
                    )
                    //Set device to use online pin instead of offline pin
                    Controler.SetEmvParamTlv(Constants.EmvUseOnlinePin)
                    setEMVTLVParams()
                }
            } else {
                SweetDialogUtils.changeAlertType(
                    this@NetPlusMainActivity,
                    "Could not connect to Device.",
                    SweetAlertDialog.ERROR_TYPE
                )
            }
            runOnUiThread {
                if (Controler.posConnected()) {
                    toggleBetweenDeviceListAndCardResult("result")
                    invalidateOptionsMenu()
                }
                Toast.makeText(this@NetPlusMainActivity, "Device Connected", Toast.LENGTH_SHORT)
            }
        }
    }

    private fun toggleBetweenDeviceListAndCardResult(show: String) {
        if (show == "result") {
            binding.textView2.visibility = View.VISIBLE
            binding.cardResult.visibility = View.VISIBLE
            binding.cardResult.text = ""
            binding.listView.visibility = View.GONE
        } else if (show == "devicelist") {
            binding.textView2.visibility = View.GONE
            binding.cardResult.visibility = View.GONE
            binding.listView.visibility = View.VISIBLE
        }
    }

    private fun checkDeviceConnection() {
        if (Controler.posConnected()) {
            SweetDialogUtils.showSuccess(this, "Device is connected.")
        } else {
            SweetDialogUtils.showError(this, "Device is not connected. Select a Device to connect.")
            invalidateOptionsMenu()
            viewEntryPoint()
        }
    }

    private fun getIndex(keyIndex: Int): KEYINDEX {
        try {
            val index: Int = keyIndex
            return KEYINDEX.values()[index]
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return KEYINDEX.values()[0]
    }

    private fun loadTestKeys() {

        val keyBuf = BytesUtil.hexString2ByteArray("F40379AB9E0EC533F40379AB9E0EC533")

        val kekD1 = BytesUtil.subBytes(keyBuf, 0, 8)
        val kekD2 = BytesUtil.subBytes(keyBuf, 8, 8)
        val kvcBuf = BytesUtil.hexString2ByteArray("00000000")
        val rawKcv = "00000000"

        val mainkeyRes = Controler.LoadMainKey(
            CommEnum.MAINKEYENCRYPT.PLAINTEXT,
            CommEnum.KEYINDEX.INDEX0, CommEnum.MAINKEYTYPE.DOUBLE, kekD1, kekD2, kvcBuf)

        Log.d(TAG, "mainKeyRes == ${mainkeyRes.loadResult} " )

        val pinKey = "F40379AB9E0EC533F40379AB9E0EC533"
        val macKey = "F40379AB9E0EC533F40379AB9E0EC533"
        val tdkKey = "F40379AB9E0EC533F40379AB9E0EC533"
        val pinKvc = "82E13665"

        val keyD: String = pinKey + rawKcv + macKey + rawKcv + tdkKey + rawKcv

        val keyArrays = Misc.asc2hex(keyD)
        Log.d(TAG, "updateWorkingKey key:$keyD")

        val workKey = Controler.LoadWorkKey(
            CommEnum.KEYINDEX.INDEX0,
            CommEnum.WORKKEYTYPE.DOUBLEMAG,
            keyArrays,
            keyArrays.size
        )

        Log.d(TAG, "workKeyRes == ${workKey.loadResult} " )
    }


    private  val encryptionCipher = Cipher.getInstance("DESede/ECB/Nopadding", "BC")
    private  val decryptionCipher = Cipher.getInstance("DESede/ECB/Nopadding", "BC")

    fun ByteArray.tripleDesEncrypt(key: ByteArray): ByteArray {
        val skey = SecretKeySpec(key, "DESede")
        encryptionCipher.init(Cipher.ENCRYPT_MODE, skey)
        return encryptionCipher.doFinal(this)
    }

    fun ByteArray.tripleDesDecrypt(key: ByteArray): ByteArray {
        val skey = SecretKeySpec(key, "DESede")
        decryptionCipher.init(Cipher.DECRYPT_MODE, skey)
        return decryptionCipher.doFinal(this)
    }

    // step 3
    private fun loadAllKeysToDevice(keyHolder: KeyHolder) {
        //Set device to use online pin instead of offline pin
        Controler.SetEmvParamTlv(Constants.EmvUseOnlinePin)
        setEMVTLVParams()

        val rawKcv = "0000000000000000"
        val masterKeyByteArray = BytesUtil.hexString2ByteArray(keyHolder.masterKey)!!

        val masterKey1 = BytesUtil.hexString2ByteArray(keyHolder.masterKey!!.substring(0, 16))
        val masterKey2 = BytesUtil.hexString2ByteArray(keyHolder.masterKey!!.substring(16, 32))
        val rawKVCByte = BytesUtil.hexString2ByteArray(rawKcv)

        val masterKeyKCV = rawKVCByte?.tripleDesEncrypt(masterKeyByteArray)


        val mainkeyRes = Controler.LoadMainKey(
            CommEnum.MAINKEYENCRYPT.PLAINTEXT,
            CommEnum.KEYINDEX.INDEX0, CommEnum.MAINKEYTYPE.DOUBLE, masterKey1, masterKey2, masterKeyKCV)

        Log.e(TAG, "mainKeyRes == ${mainkeyRes.loadResult} " )

        val encryptedPinKey = keyHolder.pinKey!!
        val encryptedPinKeyByteArray = BytesUtil.hexString2ByteArray(encryptedPinKey)
        val decryptedPinKey = encryptedPinKeyByteArray?.tripleDesDecrypt(masterKeyByteArray)!!

        val clearDecryptedKey = BytesUtil.bytes2Hex(decryptedPinKey)
        Log.e(TAG, "clearDecryptedKey: $clearDecryptedKey" )

        val pinKeyKCV = rawKVCByte?.tripleDesEncrypt(BytesUtil.hexString2ByteArray(keyHolder.clearPinKey)!!)
        val pinKeyKCVHex = BytesUtil.bytes2Hex(pinKeyKCV)
        val pinKeyKCVSubstring8 = pinKeyKCVHex?.substring(0, 8)
        val pinKeyKCVHexString = keyHolder.pinKey + pinKeyKCVSubstring8

        val keysKCVHex = pinKeyKCVHexString + pinKeyKCVHexString + pinKeyKCVHexString
        val keysByteArray = BytesUtil.hexString2ByteArray(keysKCVHex)!!

        val workKey = Controler.LoadWorkKey(
            CommEnum.KEYINDEX.INDEX0,
            CommEnum.WORKKEYTYPE.DOUBLEMAG,
            keysByteArray,
            keysByteArray.size
        )

        Log.e(TAG, "workKeyRes == ${workKey.loadResult} " )

        val sb = StringBuilder()
        sb.append("LoadDukpt result:" + workKey.loadResult)
//        sb.append("\n\nLoadDukpt checkvalue:" + Misc.hex2asc(workKey.checkvalue))

        if (workKey.loadResult) {
            SweetDialogUtils.changeAlertType(this@NetPlusMainActivity, sb.toString(),
                SweetAlertDialog.SUCCESS_TYPE)
            Log.e("LoadDukpt", sb.toString())
        } else {
            SweetDialogUtils.changeAlertType(
                this@NetPlusMainActivity,
                "Error: \nUnable to load key to device",
                SweetAlertDialog.ERROR_TYPE
            )
        }

    }
    private fun asc_to_bcd(asc: Byte): Byte {
        return if (asc >= '0'.toByte() && asc <= '9'.toByte()) (asc - '0'.toByte()).toByte()
        else if (asc >= 'A'.toByte() && asc <= 'F'.toByte()) (asc - 'A'.toByte() + 10).toByte()
        else if (asc >= 'a'.toByte() && asc <= 'f'.toByte()) (asc - 'a'.toByte() + 10).toByte()
        else (asc - 48).toByte()
    }

    private fun ASCII_To_BCD(ascii: ByteArray, asc_len: Int): ByteArray {
        val bcd = ByteArray(asc_len / 2)
        var j = 0
        for (i in 0 until (asc_len + 1) / 2) {
            bcd[i] = asc_to_bcd(ascii[j++])
            bcd[i] = ((if (j >= asc_len) 0x00 else asc_to_bcd(ascii[j++])) + (bcd[i] shl 4)).toByte()
        }
        return bcd
    }


    infix fun Byte.shl(that: Int): Int = this.toInt().shl(that)
    infix fun Int.shl(that: Byte): Int = this.shl(that.toInt()) // Not necessary in this case because no there's (Int shl Byte)
    infix fun Byte.shl(that: Byte): Int = this.toInt().shl(that.toInt()) // Not necessary in this case because no there's (Byte shl Byte)

    infix fun Byte.and(that: Int): Int = this.toInt().and(that)
    infix fun Int.and(that: Byte): Int = this.and(that.toInt()) // Not necessary in this case because no there's (Int and Byte)
    infix fun Byte.and(that: Byte): Int = this.toInt().and(that.toInt()) // Not necessary in this case because no there's (Byte and Byte)

    private fun disconnectDevice() {
        if (Controler.posConnected()) {
            Controler.disconnectPos()
            SweetDialogUtils.showSuccess(this, "Device successfully disconnected.")

            invalidateOptionsMenu()
        } else {
            SweetDialogUtils.showSuccess(this, "No Device was initially connected.")
        }
    }

    private fun enterAmount() {
        binding.amountEditText.visibility = View.VISIBLE
    }

    private fun pay() {
        val stringAmount: String = binding.amountEditText.text.toString()
        try {
            if (stringAmount.length < 0) {
                SweetDialogUtils.showError(this, "Invalid Amount provided.")
                return
            }
            if (stringAmount.toDouble() <= 0) {
                SweetDialogUtils.showError(this, "Invalid Amount provided.")
                return
            } else {
                amount = (stringAmount.toDouble() * 100).toLong() //convert to kobo
            }
        } catch (e: Exception) {
            SweetDialogUtils.showError(this, "Invalid Amount provided.")
            return
        }

        if(redFlag) {
            SweetDialogUtils.showError(
                this,
                "Parameters from Server are missing. Please click on Download Parameters or restart your app."
            )
            return
        }

        var sb: StringBuilder? = null
        GlobalScope.launch {
            if (Controler.posConnected()) {
                //reset pos
                Controler.ResetPos()

                sb = readCardDetails(amount, stepListener)
            } else {
                SweetDialogUtils.showError(this@NetPlusMainActivity, "No Device connected.")
            }

            runOnUiThread {
                var errorMessage: String? = null

                if (cardResult?.cardType == 0) {
                    //user cancelled operation
                    errorMessage = "User cancelled operation"
                    binding.cardResult.text = errorMessage
                    SweetDialogUtils.showError(this@NetPlusMainActivity, errorMessage)
                } else if (cardResult?.cardType == 4) {
                    //timeout
                    errorMessage = "Operation timeout"
                    binding.cardResult.text = errorMessage
                    SweetDialogUtils.showError(this@NetPlusMainActivity, errorMessage)
                } else if (cardResult?.cardType == 5) {
                    //read card error
                    errorMessage = "Read card error"
                    binding.cardResult.text = errorMessage
                    SweetDialogUtils.showError(this@NetPlusMainActivity, errorMessage)
                } else {
                    if (sb == null && !Controler.posConnected()) {
                        binding.cardResult.text = "No Device connected."
                    } else {
                        binding.cardResult.text = sb.toString()
                    }
                    binding.amountEditText.text.clear()
                    binding.amountEditText.visibility = View.GONE
                }
            }
        }
    }

    private fun readCardDetails(
        amount: Long,
        listener: ReadCardParam.onStepListener
    ): StringBuilder {
        val param = ReadCardParam()
        //set the amount
        param.amount = amount
        // set the transaction type
        param.transType = CommEnum.TRANSTYPE.FUNC_SALE

        val timeOut: Byte = 30
        param.isAllowfallback = true
        param.isPinInput = 1.toByte()
        //param.cardmode = 0x01.toByte()

        param.pinMaxLen = 6.toByte()
        param.cardTimeout = timeOut as Byte
        param.setRequireReturnCardNo(0x1.toByte())
        param.emvTransactionType = 0x00.toByte()
        param.isForceonline = false
        param.transName = "Consume"
        param.tags = getTags()
        param.onSteplistener = listener

        //read the card
        val ret: ReadCardResult = Controler.ReadCard(param)
        //background call to server to download keys
        val sb = StringBuilder()


        cardResult = ret
        sb.append("results:${ret.commResult.toDisplayName()}\n")
        sb.append("\ncardType:${ret.cardType}\n")
        sb.append("\nICData KSN: ${ret.ic_ksn}\n")

        when (ret.cardType) {
            0 -> sb.append("cardType:User Cancelled Operation".trimIndent())
            1 -> sb.append("cardType:Mag Card".trimIndent())
            2 -> sb.append("cardType:IC Card".trimIndent())
            3 -> sb.append("cardType:RF Card".trimIndent())
            4 -> sb.append("cardType:Timeout".trimIndent())
            5 -> sb.append("cardType:Read Card Error".trimIndent())
            else -> {
                sb.append("cardType: Unknown".trimIndent())
            }
        }

        val card = CardData(
            ret.track2,
            CardData.getNibssTags(ret.icData),
            ret.pansn,
            "051"
        ).also { cardData ->
            cardData.pinBlock = ret.pinblock
        }

        Log.e(TAG, "PINBLK == ${ret.pinblock}\nKSN == ${ret.pin_ksn}")
        val decryptedPINBlock = DUKPK2009_CBC.getDate(
            ret.pin_ksn,
            ret.pinblock,
            DUKPK2009_CBC.Enum_key.PIN,
            DUKPK2009_CBC.Enum_mode.CBC
        )
        val parsCarN = "0000" + ret.pan.substring(ret.pan.length - 13, ret.pan.length - 1)
        val s: String = DUKPK2009_CBC.xor(parsCarN, decryptedPINBlock)
        println("PINzzzzz === $s\n")
        sendTransactionToServer(amount, card)
        Log.e(TAG, sb.toString());

        return sb

    }

    private val stepListener = onStepListener { step ->
        when (step.toInt()) {
            1 -> showResult("Insert Card...")
            2 -> showResult("Reading Card...")
            3 -> showResult("Waiting for PIN..")
            4 -> {
            }
            5 -> {
            }
            6 -> {
            }
            7 -> {
            }
        }
    }

    private fun showResult(result: String) {
        //Log.d(TAG, result);
        binding.cardResult.text = result
    }

    private fun getTags(): List<ByteArray>? {
        val tags: MutableList<ByteArray> = ArrayList()

        tags.add(EmvTagDef.EMV_TAG_9F02_TM_AUTHAMNTN)
        tags.add(EmvTagDef.EMV_TAG_9F26_IC_AC)
        tags.add(EmvTagDef.EMV_TAG_4F_IC_AID)
        tags.add(EmvTagDef.EMV_TAG_9F27_IC_CID)
        tags.add(EmvTagDef.EMV_TAG_9F10_IC_ISSAPPDATA)
        tags.add(EmvTagDef.EMV_TAG_9F37_TM_UNPNUM)
        tags.add(EmvTagDef.EMV_TAG_9F36_IC_ATC)
        tags.add(EmvTagDef.EMV_TAG_95_TM_TVR)
        tags.add(EmvTagDef.EMV_TAG_9A_TM_TRANSDATE)
        tags.add(EmvTagDef.EMV_TAG_9C_TM_TRANSTYPE)
        tags.add(EmvTagDef.EMV_TAG_5F2A_TM_CURCODE)
        tags.add(EmvTagDef.EMV_TAG_82_IC_AIP)
        tags.add(EmvTagDef.EMV_TAG_9F17_IC_PINTRYCNTR)
        tags.add(EmvTagDef.EMV_TAG_9F1A_TM_CNTRYCODE)
        tags.add(EmvTagDef.EMV_TAG_9F03_TM_OTHERAMNTN)
        tags.add(EmvTagDef.EMV_TAG_9F33_TM_CAP)
        tags.add(EmvTagDef.EMV_TAG_9F34_TM_CVMRESULT)
        tags.add(EmvTagDef.EMV_TAG_9F35_TM_TERMTYPE)
        tags.add(EmvTagDef.EMV_TAG_9F1E_TM_IFDSN)
        tags.add(EmvTagDef.EMV_TAG_84_IC_DFNAME)
        tags.add(EmvTagDef.EMV_TAG_9F09_TM_APPVERNO)
        tags.add(EmvTagDef.EMV_TAG_9F63_TM_BIN)
        tags.add(EmvTagDef.EMV_TAG_9F41_TM_TRSEQCNTR)
        return tags
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_status -> true
            else ->                 // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val deviceStatusIcon = menu!!.findItem(R.id.action_status)
        // set your desired icon here based on a flag if you like
        if (Controler.posConnected()) {
            deviceStatusIcon.icon = ContextCompat.getDrawable(this, R.drawable.check)
        } else {
            deviceStatusIcon.icon = ContextCompat.getDrawable(this, R.drawable.cancel)
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(receiver)
        super.onPause()
    }

    override fun onDestroy() {
        Controler.Destory()
        super.onDestroy()
        unregisterReceiver(receiver)
        compositeDisposable.dispose()
    }
}