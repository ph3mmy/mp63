package ng.com.plustech.mp63kotlin.repository

import android.util.Log
import androidx.lifecycle.LiveData
import ng.com.plustech.mp63kotlin.interfaces.RetrofitInterface
import ng.com.plustech.mp63kotlin.models.*
import ng.com.plustech.mp63kotlin.utils.Constants

class ServerRepository(val retrofitInterface: RetrofitInterface) {
    private val masterKey: LiveData<MasterKey>? = null
    private val sessionKey: LiveData<SessionKey>? = null
    private val pinKey: LiveData<PinKey>? = null
    private val terminal: Terminal = Terminal(Constants.TerminalID)

    private var instance: ServerRepository? = null

    fun getInstance(): ServerRepository? {
        if (instance == null) {
            instance = ServerRepository(retrofitInterface)
        }
        return instance
    }

    suspend fun getMasterKey(): MasterKey? {
        var mkey: MasterKey? = null
        try {
            var result = retrofitInterface.getMasterKey(terminal)
            //withContext(Dispatchers.Main) {
                mkey = result
                Log.e("Result", result.toString())
                return mkey
            //}
        } catch (e: Exception) {
            Log.e("Error", e.toString())
            return null
        }
    }

    suspend fun getSessionKey(): SessionKey? {
        var mkey: SessionKey? = null
        try {
            var result = retrofitInterface.getSessionKey(terminal)
            //withContext(Dispatchers.Main) {
            mkey = result
            Log.e("Result", result.toString())
            return mkey
            //}
        } catch (e: Exception) {
            Log.e("Error", e.toString())
            return null
        }
    }

    suspend fun getPinKey(): PinKey? {
        var mkey: PinKey? = null
        try {
            var result = retrofitInterface.getPinKey(terminal)
            //withContext(Dispatchers.Main) {
            mkey = result
            Log.e("Result", result.toString())
            return mkey
            //}
        } catch (e: Exception) {
            Log.e("Error", e.toString())
            return null
        }
    }

    suspend fun getParameter(terminalId: String, encryptedSessionKey: String, sessionKeyCheckValue: String): ParameterResponse? {
        val parameter = Parameter(terminalId, encryptedSessionKey, sessionKeyCheckValue)
        var mkey: ParameterResponse? = null
        try {
            var result = retrofitInterface.getParameter(parameter)
            //withContext(Dispatchers.Main) {
            mkey = result
            Log.e("Result", result.toString())
            return mkey
            //}
        } catch (e: Exception) {
            Log.e("Error", e.toString())
            return null
        }
    }

    suspend fun sendTransactionData(url: String, data: Card): TransactionResponse? {
        var ret: TransactionResponse? = null
        try {
            var result = retrofitInterface.sendTransactionData(url, data)
            ret = result
            Log.e("Send Transaction Result", result.toString())
            return ret
        } catch (e: Exception) {
            Log.e("Error", e.toString())
            return null
        }
    }

//    fun getMasterKey(): MutableLiveData<MasterKey> {
//        var mkey: MutableLiveData<MasterKey> = MutableLiveData<MasterKey>()
//        GlobalScope.launch {
//            try {
//                val response = retrofitInterface.getMasterKey(terminal).awaitResponse()
//                if(response.isSuccessful) {
//                    val data = response.body()!!
//                    withContext(Dispatchers.Main) {
//                        Log.e("MasterKey", data.toString())
//                        mkey.value = data
//                    }
//                } else {
//                    Log.e("MasterKey Error", response.errorBody().toString())
//                }
//            } catch (e: Exception) {
//                Log.e("Error", e.toString())
//            }
//        }
//        return mkey
//    }

}