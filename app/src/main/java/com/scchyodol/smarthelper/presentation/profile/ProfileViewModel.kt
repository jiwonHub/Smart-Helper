package com.scchyodol.smarthelper.presentation.profile

import android.content.Context
import androidx.lifecycle.AndroidViewModel  // ← ViewModel 대신 AndroidViewModel 사용
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.scchyodol.smarthelper.R

class ProfileViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()

    private val _user = MutableLiveData<FirebaseUser?>()
    val user: LiveData<FirebaseUser?> = _user

    private val _logoutEvent = MutableLiveData<Boolean>()
    val logoutEvent: LiveData<Boolean> = _logoutEvent

    init {
        _user.value = auth.currentUser
    }

    fun logout() {
        val context = getApplication<android.app.Application>().applicationContext

        // 1️⃣ Firebase 로그아웃
        auth.signOut()

        // 2️⃣ Google Sign-In 세션도 끊기
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
            // 완전히 끊긴 후 로그아웃 이벤트 발행
            _logoutEvent.value = true
        }
    }
}

