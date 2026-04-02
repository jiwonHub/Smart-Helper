package com.scchyodol.smarthelper.presentation.home.carerecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.scchyodol.smarthelper.data.remote.repository.CareRecordRepository

class CareRecordViewModelFactory(
    private val repository: CareRecordRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CareRecordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CareRecordViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
