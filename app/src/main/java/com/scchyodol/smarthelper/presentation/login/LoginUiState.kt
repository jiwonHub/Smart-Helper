package com.scchyodol.smarthelper.presentation.login

sealed class LoginUiState {
    object Idle : LoginUiState()          // 초기 상태
    object Loading : LoginUiState()       // 로딩 중
    object Success : LoginUiState()       // 로그인 성공
    data class Error(
        val message: String
    ) : LoginUiState()                    // 에러
}