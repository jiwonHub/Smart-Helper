package com.scchyodol.smarthelper.domain.repository

import com.google.android.gms.auth.api.signin.GoogleSignInAccount

interface AuthRepository {
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<Unit>
    fun signOut()
    fun isLoggedIn(): Boolean
}
