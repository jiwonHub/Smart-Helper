package com.scchyodol.smarthelper.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.presentation.login.LoginActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)

        // 2초 동안 프로그레스바 채우기 → 로그인 화면으로 이동
        lifecycleScope.launch {
            val totalDuration = 2000L  // 2초
            val steps = 100            // 0 ~ 100
            val stepDelay = totalDuration / steps

            for (i in 0..steps) {
                progressBar.progress = i
                delay(stepDelay)
            }

            // 로그인 화면으로 이동
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            finish() // 스플래쉬는 백스택에서 제거
        }
    }
}
