package ng.com.plustech.mp63kotlin.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ng.com.plustech.mp63kotlin.repository.ServerRepository

class MainActivityViewModelFactory(val serverRepository: ServerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MainActivityViewModel(serverRepository) as T
    }
}