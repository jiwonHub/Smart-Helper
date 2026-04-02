package com.scchyodol.smarthelper.presentation.profile.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.TextView
import com.scchyodol.smarthelper.R

class LogoutDialog(
    context: Context,
    private val onConfirm: () -> Unit   // 로그아웃 확인 콜백
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 타이틀바 제거 + 배경 투명
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_logout)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 바깥 터치 시 닫기
        setCanceledOnTouchOutside(true)

        // 취소 버튼
        findViewById<TextView>(R.id.btnCancel).setOnClickListener {
            dismiss()
        }

        // 로그아웃 확인 버튼
        findViewById<TextView>(R.id.btnConfirmLogout).setOnClickListener {
            dismiss()
            onConfirm()   // ViewModel.logout() 호출
        }
    }
}
