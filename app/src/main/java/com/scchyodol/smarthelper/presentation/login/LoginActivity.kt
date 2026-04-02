package com.scchyodol.smarthelper.presentation.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.presentation.home.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    // ViewModel 주입
    private val viewModel: LoginViewModel by viewModels()

    // Google Sign-In 클라이언트
    private lateinit var googleSignInClient: GoogleSignInClient

    // UI 요소
    private lateinit var btnGoogleLogin: LinearLayout
    private lateinit var progressBar: ProgressBar  // 로딩 인디케이터 (선택)

    // 구글 로그인 결과 콜백
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                // ViewModel에 구글 계정 전달 → Firebase 인증
                viewModel.signInWithGoogle(account)
            } catch (e: ApiException) {
                viewModel.resetState()
                Toast.makeText(
                    this,
                    "Google 로그인 실패: ${e.statusCode}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("GoogleLogin", "❌ 로그인 실패!")
                Log.e("GoogleLogin", "❌ StatusCode: ${e.statusCode}")
                Log.e("GoogleLogin", "❌ Message: ${e.message}")
                Log.e("GoogleLogin", "❌ Status: ${e.status}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 이미 로그인된 유저면 바로 메인으로
        if (viewModel.checkLoginStatus()) {
            navigateToMain()
            return
        }

        initGoogleSignIn()
        initViews()
        observeUiState()
    }

    // Google Sign-In 초기화
    private fun initGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Firebase에서 자동 생성
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    // View 초기화 및 클릭 이벤트
    private fun initViews() {
        btnGoogleLogin = findViewById(R.id.btn_google_login)
        // progressBar = findViewById(R.id.loading_progress) // 필요 시 사용

        btnGoogleLogin.setOnClickListener {
            startGoogleSignIn()
        }
    }

    // 구글 로그인 화면 실행
    private fun startGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    // UI 상태 관찰 (Lifecycle-aware)
    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is LoginUiState.Idle -> {
                            setLoadingVisible(false)
                        }
                        is LoginUiState.Loading -> {
                            setLoadingVisible(true)
                        }
                        is LoginUiState.Success -> {
                            setLoadingVisible(false)
                            navigateToMain()
                        }
                        is LoginUiState.Error -> {
                            setLoadingVisible(false)
                            Toast.makeText(
                                this@LoginActivity,
                                state.message,
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.resetState()
                        }
                    }
                }
            }
        }
    }

    // 로딩 상태 UI 처리
    private fun setLoadingVisible(isLoading: Boolean) {
        btnGoogleLogin.isEnabled = !isLoading
        btnGoogleLogin.alpha = if (isLoading) 0.6f else 1.0f
        // progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    // 메인 화면으로 이동
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
