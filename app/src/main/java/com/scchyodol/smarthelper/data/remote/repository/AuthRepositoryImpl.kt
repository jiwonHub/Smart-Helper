package com.scchyodol.smarthelper.data.remote.repository

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.scchyodol.smarthelper.domain.repository.AuthRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    // 구글 계정 → Firebase 인증
    override suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            firebaseAuth.signInWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 로그아웃
    override fun signOut() {
        firebaseAuth.signOut()
    }

    // 로그인 여부 확인
    override fun isLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }
}
