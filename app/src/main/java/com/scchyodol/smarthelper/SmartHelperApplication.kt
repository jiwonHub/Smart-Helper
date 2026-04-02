package com.scchyodol.smarthelper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp   // ← 이거 없으면 Hilt 자체가 초기화 안됨!
class SmartHelperApplication : Application()
