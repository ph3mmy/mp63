package ng.com.plustech.mp63kotlin.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ng.com.plustech.mp63kotlin.models.*
import ng.com.plustech.mp63kotlin.repository.ServerRepository

class MainActivityViewModel(val serverRepository: ServerRepository) : ViewModel() {

    fun masterKey(): LiveData<MasterKey?> {
        var mk: MutableLiveData<MasterKey?> = MutableLiveData<MasterKey?>()
        viewModelScope.launch {
            val result = serverRepository.getMasterKey()
            mk.value = result
        }
        return mk
    }

    fun pinKey(): LiveData<PinKey?> {
        var mk: MutableLiveData<PinKey?> = MutableLiveData<PinKey?>()
        viewModelScope.launch {
            mk.value = serverRepository.getPinKey()
        }
        return mk
    }

    fun sessionKey(): LiveData<SessionKey?> {
        var mk: MutableLiveData<SessionKey?> = MutableLiveData<SessionKey?>()
        viewModelScope.launch {
            mk.value = serverRepository.getSessionKey()
        }
        return mk
    }

    fun parameterKey(terminalId: String, encryptedSessionKey: String, sessionKeyCheckValue: String): LiveData<ParameterResponse?> {
        var mk: MutableLiveData<ParameterResponse?> = MutableLiveData<ParameterResponse?>()
        viewModelScope.launch {
            mk.value = serverRepository.getParameter(terminalId, encryptedSessionKey, sessionKeyCheckValue)
        }
        return mk
    }

    fun sendTransaction(url: String, data: Card): LiveData<TransactionResponse?> {
        var response: MutableLiveData<TransactionResponse?> = MutableLiveData<TransactionResponse?>()
        viewModelScope.launch {
            response.value = serverRepository.sendTransactionData(url, data)
        }
        return response
    }

}