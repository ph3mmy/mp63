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
import androidx.lifecycle.Observer
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
import org.json.JSONObject
import java.util.*


class MainActivity : AppCompatActivity() {
    val PREFS_NAME = "ng.com.plustech.mp63kotlin.SHARED_PREFERENCES"
    val REQUEST_ENABLE_BT = 1
    private lateinit var binding: ActivityMainBinding

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var mAdapter: BluetoothListAdapter
    private lateinit var serverRepository: ServerRepository
    private lateinit var retrofitInterface: RetrofitInterface
    private lateinit var viewModel: MainActivityViewModel

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

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        }

        keyDownloadInit()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) !==
                    PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this@MainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
                        )
                    } else {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
                        )
                    }
                }
            }
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            SweetDialogUtils.changeAlertType(
                this@MainActivity,
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

        binding.loadKeysBtn.setOnClickListener { loadAllKeysToDevice() }

        //downloadKeysFromServer()
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
                            this@MainActivity,
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

    private fun keyDownloadInit() {
        //check if any is empty
        if(serverParams.status != "200" && masterKey.status != "00" && sessionKey.status != "00" && pinKey.status != "00" && epk == "" && esk == "" && skcv == "" && pkcv == "") {
            //fetch data from server and save to shared preference
            redFlag = true
            Log.e(TAG, "Download Key From Server Called")
            downloadKeysFromServer()
        } else if(serverParams.status != "200" && masterKey.status == "00" && sessionKey.status == "00" && pinKey.status == "00" && epk != "" && esk != "" && skcv != "" && pkcv != "") {
            //other params are available except serverParams
            //just download serverParams only
            Log.e("Information", "Retrying only Server Params Download")
            downloadParamsFromServer()
        } else {
            Log.e("Key Download", "Got all keys from local")
            GlobalScope.launch {
                val editor = sharedPreferences.edit()
                editor.putString("epk", epk)
                editor.putString("esk", esk)
                editor.putString("skcv", skcv)
                editor.putString("pkcv", pkcv)
                editor.putString("masterKey", Gson().toJson(masterKey))
                editor.putString("sessionKey", Gson().toJson(sessionKey))
                editor.putString("pinKey", Gson().toJson(pinKey))
                editor.putString("serverParam", Gson().toJson(serverParams))
                editor.apply()
            }
            redFlag = false
            Log.e("Key Download", "Keys are completely downloaded")
        }
    }

    private fun downloadKeysFromServer() {
        var keyDownloadCounter = 0
        var keyDownloadCounterRetry = 0
        Log.e(TAG, "Starting DownloadKeysFromServer now")
        viewModel.masterKey().observe(this@MainActivity, Observer {
            it?.let {
                masterKey = it
                keyDownloadCounter++
                Log.e("From Main", it.toString())
            }
        })

        viewModel.sessionKey().observe(this, androidx.lifecycle.Observer {
            it?.let {
                sessionKey = it
                keyDownloadCounter++
                Log.e("From Main", it.toString())
            }
        })

        viewModel.pinKey().observe(this, androidx.lifecycle.Observer {
            it?.let {
                pinKey = it
                keyDownloadCounter++
                Log.e("From Main", it.toString())
            }
        })

        GlobalScope.launch(Dispatchers.IO) {
            while (keyDownloadCounter < 3 && keyDownloadCounterRetry < 3) {
                Log.e(
                    "Keys Check",
                    "Session: ${sessionKey.status}, Pin: ${pinKey.status}, Master: ${masterKey.status}"
                )
                delay(5000)
                withContext(Dispatchers.Main) {
                    if(sessionKey.status == "00" && pinKey.status == "00") {
                        Log.e("Key Check", "I am here, about to download params")
                        val n_esk = ServerUtil.calculateESK(sessionKey, masterKey)
                        val n_epk = ServerUtil.calculateEPK(pinKey, masterKey)

                        esk = n_esk
                        skcv = sessionKey.SKCV
                        epk = n_epk
                        pkcv = pinKey.PKCV

                        downloadParamsFromServer()

                        Log.e("From Main ESK", n_esk)
                        Log.e("From Main EPK", n_epk)
                        Log.e("From Main SKCV", sessionKey.SKCV)
                    } else {
                        //error
                    }
                }
                keyDownloadCounterRetry++
            }
        }
    }

    private fun downloadParamsFromServer() {
        Log.e(TAG, "Now in Download Parameter Keys From Server Section")
        if(esk != "" && skcv != "") {
            Log.e(TAG, "Download Parameter Keys From Server Called")
            viewModel.parameterKey(Constants.TerminalID, esk, skcv).observe(
                this,
                androidx.lifecycle.Observer {
                    it?.let {
                        Log.e("From Main Parameter", it.toString())
                        serverParams = it
                        //run another check but prevent infinite loop
                        if (keyDownloadInitCount < 2) {
                            keyDownloadInit()
                            keyDownloadInitCount++
                        }
                    }
                })
        } else {
            downloadKeysFromServer()
        }
    }

    private fun downloadAIDToTerminal() {
        Log.e(TAG, "Loading AIDs to Terminal")
        for (j in Constants.AIDS.indices) {
            val aid: String = Constants.AIDS[j]
            Log.e(TAG, "Loading $j - $aid")
            Controler.ICAidManage(CommEnum.ICAIDACTION.ADD, Misc.asc2hex(aid))
        }
    }

    private fun sendTransactionToServer(card: Card) {
        runOnUiThread {
            Log.e("Card Details", card.toString())
            binding.cardResult.text = "Sending Transaction..."
            SweetDialogUtils.showProgress(this@MainActivity, "Sending Transaction...", false)

            try {
                viewModel.sendTransaction(Constants.URL_SUBMIT_TRANSACTION, card)
                    .observe(this, androidx.lifecycle.Observer {
                        it?.let {
                            SweetDialogUtils.changeAlertType(
                                this@MainActivity,
                                "Transaction Completed. Response: $it",
                                SweetAlertDialog.SUCCESS_TYPE
                            )
                            //binding.cardResult.text = "Transaction Completed. Response: $it"
                        }

                        if (it == null) {
                            SweetDialogUtils.changeAlertType(
                                this@MainActivity,
                                "Error: " + it.toString(),
                                SweetAlertDialog.ERROR_TYPE
                            )
                            //binding.cardResult.text = "Error: " + it.toString()
                        }
                    })
            } catch (e: Exception) {
                SweetDialogUtils.changeAlertType(
                    this@MainActivity,
                    "Error: $e",
                    SweetAlertDialog.ERROR_TYPE
                )
                binding.cardResult.text = "Error: $e"
            }
        }

//OLD METHOD
//        GlobalScope.launch(Dispatchers.IO) {
//            try {
//                //val cd = JSONObject("""{"encryptedSessionKey": "B61923C57B816DDBD6B24761BE3A690C","sessionKeyCheckValue": "CA46C3","encryptedPinKey": "CB6D992E9B6AF3DC1DD1136F20748FCA", "pinKeyCheckValue": "FA915E","stan": 464758,"terminalId": "2HIGY028","processingCode": "001000","minorAmount": 1000,"expiryDate": "230531","merchantType": "6010","posEntryMode": "051","cardSequenceNumber": "01","posConditionCode":"00","pinCaptureCode": "06","acquiringInstitutionId": "111111","encryptedTrack2":"7688858735422D2C89C8AECF0870DF38A2CF8C4B50F61F50","track2Ksn": "00768082701774E00192","track2Length": 32,"encryptedPinBlock": "1F56DE46A67155AC","pinBlockKsn": "00120082701774E00191","rrn": "595959694969","cardAcceptorId": "2HIGY028000Y028","cardAcceptorLocation": "2HIGY028 TRACTION                   LANG","transactionCurrencyCode": "566","emvData": "","posDataCode": "510101513344101","processorKey": "rubies"}""")
//                //val cdcard = Gson().fromJson(cd.toString(), Card::class.java)
//                val result = serverRepository.sendTransactionData(
//                    Constants.URL_SUBMIT_TRANSACTION,
//                    card
//                )
//
//                withContext(Dispatchers.Main) {
//                    Log.e("From Main Transaction", result.toString())
//                    if(result == null) {
//                        SweetDialogUtils.changeAlertType(this@MainActivity, "Error: "+result.toString(), SweetAlertDialog.ERROR_TYPE)
//                        binding.cardResult.text = "Error: "+result.toString()
//                    } else {
//                        SweetDialogUtils.changeAlertType(
//                            this@MainActivity,
//                            "Transaction Completed. Response: $result",
//                            SweetAlertDialog.SUCCESS_TYPE
//                        )
//                        binding.cardResult.text = "Transaction Completed. Response: $result"
//                    }
//                }
//            } catch (e: Exception) {
//                SweetDialogUtils.changeAlertType(this@MainActivity, "Error: $e", SweetAlertDialog.ERROR_TYPE)
//                binding.cardResult.text = "Error: $e"
//            }
//        }
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
                this@MainActivity.registerReceiver(receiver, filter)
                bluetoothAdapter?.startDiscovery()
            } catch (e: Exception) {
                Log.e(TAG, "Error registering bluetooth broadcast receiver.")
            }
            //this@MainActivity.unregisterReceiver(receiver)
            mAdapter.notifyDataSetChanged()
        } else {
            SweetDialogUtils.showError(
                this@MainActivity,
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
        SweetDialogUtils.showProgress(this@MainActivity, "Connecting...", false)
        GlobalScope.launch {
            if (Controler.posConnected()) {
                Controler.disconnectPos()
            }
            val ret = Controler.connectPos(device.address)

            if (ret.bConnected) {
                if (Controler.posConnected()) {
                    bluetoothAdapter?.cancelDiscovery()
                    SweetDialogUtils.changeAlertType(
                        this@MainActivity,
                        "Device Connected Successfully.",
                        SweetAlertDialog.SUCCESS_TYPE
                    )

                    //Load AIDs to the terminal
                    if(Constants.aidsCount < readAIDsFromDevice()) {
                        SweetDialogUtils.cancel(this@MainActivity)
                        SweetDialogUtils.showProgress(this@MainActivity, "Initializing Device...", false)
                        GlobalScope.launch {
                            downloadAIDToTerminal()
                            //readAIDsFromDevice()

                            runOnUiThread {
                                SweetDialogUtils.changeAlertType(this@MainActivity, "Device Initialization Completed.",
                                    SweetAlertDialog.SUCCESS_TYPE)
                            }
                        }
                    }
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
                    this@MainActivity,
                    "Could not connect to Device.",
                    SweetAlertDialog.ERROR_TYPE
                )
            }
            runOnUiThread {
                if (Controler.posConnected()) {
                    toggleBetweenDeviceListAndCardResult("result")
                    invalidateOptionsMenu()
                }
                Toast.makeText(this@MainActivity, "Device Connected", Toast.LENGTH_SHORT)
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

    private fun loadAllKeysToDevice() {
        //Set device to use online pin instead of offline pin
        Controler.SetEmvParamTlv(Constants.EmvUseOnlinePin)
        setEMVTLVParams()
        //Load DUKPT Start
        var key = BytesUtil.hexString2ByteArray(Constants.KEY)
        val ksn = BytesUtil.hexString2ByteArray(Constants.KSN)
        val keyType = 1; //plain ipek
        val keyIndex = 1; //set by me it can be a value between 0 - 9

        val bret = Controler.LoadDukpt(keyType.toByte(), getIndex(keyIndex), key, ksn)

        val sb = StringBuilder()
        sb.append("LoadDukpt result:" + bret.loadResult)
        sb.append("\n\nLoadDukpt checkvalue:" + Misc.hex2asc(bret.checkvalue))

        SweetDialogUtils.showSuccess(this@MainActivity, sb.toString())
        Log.e("LoadDukpt", sb.toString())
        //Load DUKPT Ends

        //Load KEK start
//        key = "11111111111111112222222222222222D2B91CC5"
//
//        var kek1: ByteArray? = ByteArray(8)
//        var kek2: ByteArray? = ByteArray(8)
//        var kvc: ByteArray? = ByteArray(4)
//
//        kek1 = Misc.asc2hex(key, 0, 16, 0)
//        kek2 = Misc.asc2hex(key, 16, 16, 0)
//        kvc = Misc.asc2hex(key, 32, 8, 0)
//
//        val result = Controler.LoadKek(CommEnum.KEKTYPE.DOUBLE, kek1, kek2, kvc)
//        SweetDialogUtils.showSuccess(this@MainActivity, result.loadResult.toString())
//        Log.e("LoadKek", result.loadResult.toString())
//        //Load KEK Ends
//
//        //Load Master Key Start
//        val keyb = "1111111111111111111111111111111182E13665"
//
//        var kekD1: ByteArray? = ByteArray(8)
//        var kekD2: ByteArray? = ByteArray(8)
//        var kvcx: ByteArray? = ByteArray(4)
//
//        kekD1 = Misc.asc2hex(keyb, 0, 16, 0)
//        kekD2 = Misc.asc2hex(keyb, 16, 16, 0)
//        kvc = Misc.asc2hex(keyb, 32, 8, 0)
//
//        Misc.traceHex(TAG, "updateMainKey kekD1", kekD1)
//        Misc.traceHex(TAG, "updateMainKey kekD2", kekD2)
//        Misc.traceHex(TAG, "updateMainKey kvc", kvcx)
//
//        val resulty = Controler.LoadMainKey(CommEnum.MAINKEYENCRYPT.PLAINTEXT,
//                KEYINDEX.INDEX0,
//                CommEnum.MAINKEYTYPE.DOUBLE,
//                kekD1, kekD2, kvc)
//
//        //Load Master Key Ends
//        SweetDialogUtils.showSuccess(this@MainActivity, resulty.loadResult.toString())
//        Log.e("LoadKek", resulty.loadResult.toString())
//
//        //Load WorkKeys: pinkey, mackey & tdkKey
//        val pinKey = "B7C60530D82A361516E938B5343D2F774C82B0AF"
//        val macKey = "B7C60530D82A361516E938B5343D2F774C82B0AF"
//        val tdkKey = "B7C60530D82A361516E938B5343D2F774C82B0AF"
//
//        val keyx = pinKey + macKey + tdkKey
//
//        val mainKeyIndex = KEYINDEX.INDEX0
//
//        val keyArrays = Misc.asc2hex(keyx)
//        Log.d(TAG, "updateWorkingKey key:$keyx")
//        Misc.traceHex(TAG, "updateWorkingKey keyArrays", keyArrays)
//
//        binding.cardResult.text = MyMisc.parseByte2HexStr("0000831639539028".toByteArray())
//
//        val resultx = Controler.LoadWorkKey(mainKeyIndex, CommEnum.WORKKEYTYPE.DOUBLEMAG, keyArrays, keyArrays.size)
//        showResult(MyMisc.parseByte2HexStr("0000831639539028".toByteArray()).toString())
//
//        SweetDialogUtils.showSuccess(this@MainActivity, resultx.loadResult.toString())
    }

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
                SweetDialogUtils.showError(this@MainActivity, "No Device connected.")
            }

            runOnUiThread {
                var errorMessage: String? = null

                if (cardResult?.cardType == 0) {
                    //user cancelled operation
                    errorMessage = "User cancelled operation"
                    binding.cardResult.text = errorMessage
                    SweetDialogUtils.showError(this@MainActivity, errorMessage)
                } else if (cardResult?.cardType == 4) {
                    //timeout
                    errorMessage = "Operation timeout"
                    binding.cardResult.text = errorMessage
                    SweetDialogUtils.showError(this@MainActivity, errorMessage)
                } else if (cardResult?.cardType == 5) {
                    //read card error
                    errorMessage = "Read card error"
                    binding.cardResult.text = errorMessage
                    SweetDialogUtils.showError(this@MainActivity, errorMessage)
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
        //downloadKeysFromServer()
        val sb = StringBuilder()
        if(esk != null && skcv != null) {
            //download parameters
            //downloadParamsFromServer()


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

            sb.append(
                """
    
    
    pan:${ret.pan}
    """.trimIndent()
            )
            sb.append(
                """
    
    pansn:${ret.pansn}
    """.trimIndent()
            )
            sb.append(
                """
    
    pinBlock:${ret.pinblock}
    """.trimIndent()
            )
            sb.append(
                """
    
    track2:${ret.track2}
    """.trimIndent()
            )
            sb.append(
                """
    
    track3:${ret.track3}
    """.trimIndent()
            )
            sb.append(
                """
    
    icData:${ret.icData}
    """.trimIndent()
            )
            sb.append(
                """
    
    expData:${ret.expData}
    """.trimIndent()
            )
            sb.append(
                """
    
    ksn:${ret.ksn}
    """.trimIndent()
            )
            sb.append(
                """
    
    mac_ksn:${ret.mac_ksn}
    """.trimIndent()
            )
            sb.append(
                """
    
    mag_ksn:${ret.mag_ksn}
    """.trimIndent()
            )
            sb.append(
                """
    
    pin_ksn:${ret.pin_ksn}
    """.trimIndent()
            )

            var decryptedTrack2 = ""
            var decryptedPINBlock = ""
            //Decrypt pin block
            if (ret.pin_ksn != null) {
                decryptedPINBlock = DUKPK2009_CBC.getDate(
                    ret.pin_ksn,
                    ret.pinblock,
                    DUKPK2009_CBC.Enum_key.PIN,
                    DUKPK2009_CBC.Enum_mode.CBC
                )
                val parsCarN = "0000" + ret.pan.substring(ret.pan.length - 13, ret.pan.length - 1)
                val s: String = DUKPK2009_CBC.xor(parsCarN, decryptedPINBlock)
                println("PIN: $s\n")
                sb.append("\nDecrypted PIN: $s")
            }

            //Decrypt Track2 DATA
            if (ret.mag_ksn != null) {
                decryptedTrack2 = DUKPK2009_CBC.getDate(
                    ret.mag_ksn,
                    ret.track2,
                    DUKPK2009_CBC.Enum_key.DATA,
                    DUKPK2009_CBC.Enum_mode.CBC
                )
                println("Track 2: $decryptedTrack2\n")

                sb.append("\nDecrypted Track 2: $decryptedTrack2")
            }

            if(ret.expData == null) {
                //Toast.makeText(this@MainActivity, "Error Reading Card", Toast.LENGTH_SHORT)
                return sb
            }

            //prepare data for server
            val card = Card(
                esk,
                skcv,
                epk,
                pkcv,
                ServerUtil.generateRandomNumber(6),
                Constants.TerminalID,
                "001000",
                amount.toString(),
                ret.expData,
                serverParams.MT,
                "051",
                ret.pansn,
                "00",
                "06",
                "111111",
                ret.track2,
//                DUKPK2009_CBC.parseByte2HexStr(
//                    DUKPK2009_CBC.TriDesEncryption(
//                        DUKPK2009_CBC.parseHexStr2Byte(
//                            sessionKey.SEK
//                        ), DUKPK2009_CBC.parseHexStr2Byte(decryptedTrack2)
//                    )
//                ),
                ret.mag_ksn,
                ret.track2Len.toString(),
                ret.pinblock,
//                DUKPK2009_CBC.parseByte2HexStr(
//                    DUKPK2009_CBC.TriDesEncryption(
//                        DUKPK2009_CBC.parseHexStr2Byte(
//                            pinKey.PKEK
//                        ), DUKPK2009_CBC.parseHexStr2Byte(decryptedPINBlock)
//                    )
//                ),
                ret.pin_ksn,
                ServerUtil.generateRandomNumber(12),
                serverParams.CA,
                serverParams.CAL,
                "566",
                ret.icData,
                "510101513344101",
                "rubies"
            )
            sendTransactionToServer(card)
            Log.e(TAG, sb.toString());
            return sb
        } else {
            sb.append("Necessary Server Keys Are Missing")
            Log.e(TAG, sb.toString());
            return sb
        }
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

    private fun readAIDsFromDevice(): Int {
        val aidList: MutableList<String> = ArrayList()
        val aidResult = Controler.ICAidManage(CommEnum.ICAIDACTION.READLIST, null)
        if (aidResult.commResult == CommEnum.COMMRET.NOERROR) {
            if (aidResult.aidLen > 0) {
                var aid = Misc.hex2asc(aidResult.aid)
                val oneLen = 32
                var len = aid.length
                while (len >= oneLen) {
                    val tmp = aid.substring(0, oneLen)
                    aidList.add(tmp)
                    aid = aid.substring(oneLen)
                    len = aid.length
                }
            }
            if (aidList.size > 0) {
                val builder = java.lang.StringBuilder()
                for (i in aidList.indices) {
                    val aid = Controler.ICAidManage(
                        CommEnum.ICAIDACTION.READAPPOINT,
                        Misc.asc2hex("9F0610" + aidList[0])
                    )
                    builder.append("aid[").append(i).append("]").append(Misc.hex2asc(aid.aid))
                }
                Log.e(TAG, "AID List: ${builder.toString()}")
            }
        }
        return aidList.size
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
    }
}