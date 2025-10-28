package com.antago30.acdoctor.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.antago30.acdoctor.data.ble.BleManager
import com.antago30.acdoctor.data.repository.BleRepository

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val repository: BleRepository by lazy {
        BleRepository(BleManager()).apply {
            onConnectionProcessFinished = { foundAny ->
                _isLoading.postValue(false)
                if (!foundAny) {
                    _toastMessage.postValue("Устройства не найдены")
                }
            }
        }
    }

    val connectedDevices = repository.connectedDevices
    fun disconnectAllAndConnect(maxDevices: Int = 5) {
        _isLoading.value = true
        repository.disconnectAllAndConnect(maxDevices)
    }

    fun disconnectAll() {
        repository.disconnectAll()
    }

    override fun onCleared() {
        repository.clear()
        super.onCleared()
    }
}