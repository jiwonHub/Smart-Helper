package com.scchyodol.smarthelper.presentation.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseUser
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.databinding.ActivityProfileBinding
import com.scchyodol.smarthelper.presentation.login.LoginActivity
import com.scchyodol.smarthelper.presentation.profile.dialog.LogoutDialog

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    private val viewModel: ProfileViewModel by viewModels {
        ProfileViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeViewModel()
        setupBack()
        setupLogout()
    }

    private fun observeViewModel() {

        // 유저 정보 업데이트
        viewModel.user.observe(this) { user ->
            user?.let { bindUserInfo(it) }
        }

        // 로그아웃 완료 이벤트 → LoginActivity로 이동
        viewModel.logoutEvent.observe(this) { isLoggedOut ->
            if (isLoggedOut) {
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }
    }

    private fun bindUserInfo(user: FirebaseUser) {

        val displayName = user.displayName ?: "이름 없음"
        val email       = user.email       ?: "이메일 없음"

        binding.tvProfileName.text  = displayName
        binding.tvInfoName.text     = displayName
        binding.tvProfileEmail.text = email
        binding.tvInfoEmail.text    = email

        // 프로필 사진
        user.photoUrl?.let { uri ->
            Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.user)
                .into(binding.ivProfilePhoto)
        }
    }

    private fun setupLogout() {
        binding.btnLogout.setOnClickListener {
            LogoutDialog(this) {
                viewModel.logout()
            }.show()
        }
    }

    private fun setupBack() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
