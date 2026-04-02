package com.scchyodol.smarthelper.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.scchyodol.smarthelper.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    // UI 상태 Flow
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // 이미 로그인된 유저인지 확인
    fun checkLoginStatus(): Boolean {
        return authRepository.isLoggedIn()
    }

    // 구글 로그인 처리
    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            authRepository.signInWithGoogle(account)
                .onSuccess {
                    _uiState.value = LoginUiState.Success
                }
                .onFailure { exception ->
                    _uiState.value = LoginUiState.Error(
                        exception.message ?: "로그인에 실패했습니다."
                    )
                }
        }
    }

    // 상태 초기화
    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
