package com.scchyodol.smarthelper.presentation.home.main

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.material.snackbar.Snackbar
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.databinding.ActivityMainBinding
import com.scchyodol.smarthelper.presentation.home.fragment.carelog.CareLogFragment
import com.scchyodol.smarthelper.presentation.home.fragment.calendar.CalendarFragment
import com.scchyodol.smarthelper.presentation.home.fragment.home.HomeFragment
import com.scchyodol.smarthelper.presentation.home.fragment.lovemessage.LoveMessageFragment
import com.scchyodol.smarthelper.presentation.home.fragment.selfcare.SelfCareFragment
import com.scchyodol.smarthelper.presentation.profile.ProfileActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val homeFragment = HomeFragment()
    private val careLogFragment = CareLogFragment()
    private val selfCareFragment = SelfCareFragment()
    private val calendarFragment = CalendarFragment()
    private val loveMessageFragment = LoveMessageFragment()

    // 위치 권한
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            viewModel.fetchWeather()
        } else {
            Snackbar.make(
                binding.root,
                "위치 권한이 필요합니다. 날씨 정보를 불러올 수 없습니다.",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    // 알림 권한 (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "알림 권한 승인됨")
            checkExactAlarmPermission()
        } else {
            showPermissionDialog("알림 권한이 거부되었습니다. 일정 알림을 받을 수 없습니다.")
        }
    }

    // 정확한 알람 권한 설정 화면 결과
    private val exactAlarmPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (canScheduleExactAlarms()) {
            Log.d("MainActivity", "정확한 알람 권한 승인됨")
            scheduleReminders()
        } else {
            showPermissionDialog("정확한 알람 권한이 필요합니다. 정시 알림이 작동하지 않을 수 있습니다.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBottomNavigation()
        observeUser()

        // 권한 체크 시작
        checkAndRequestAllPermissions()

        if (savedInstanceState == null) {
            loadFragment(homeFragment)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.ivSettings.setOnClickListener {
            // TODO: 설정 화면으로 이동
        }

        binding.ivProfilePhoto.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> loadFragment(homeFragment)
                R.id.nav_care_log -> loadFragment(careLogFragment)
                R.id.calendar -> loadFragment(calendarFragment)
                R.id.nav_self_care -> loadFragment(selfCareFragment)
//                R.id.nav_love_message -> loadFragment(loveMessageFragment)
            }
            true
        }
    }

    private fun observeUser() {
        viewModel.user.observe(this) { user ->
            if (user != null && user.photoUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(user.photoUrl)
                    .transform(CircleCrop())
                    .placeholder(R.drawable.user)
                    .error(R.drawable.user)
                    .into(binding.ivProfilePhoto)
            } else {
                binding.ivProfilePhoto.setImageResource(R.drawable.user)
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    // ═══ 권한 체크 시작점 ═══
    private fun checkAndRequestAllPermissions() {
        // 1. 위치 권한 먼저 체크
        checkAndRequestLocationPermission()

        // 2. 알림 권한 체크 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkNotificationPermission()
        } else {
            // 13 미만은 바로 정확한 알람 권한으로
            checkExactAlarmPermission()
        }
    }

    private fun checkAndRequestLocationPermission() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.fetchWeather()
        }
    }

    // ═══ 알림 관련 권한들 ═══
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkExactAlarmPermission()
            }
        }
    }

    private fun checkExactAlarmPermission() {
        if (!canScheduleExactAlarms()) {
            AlertDialog.Builder(this)
                .setTitle("정확한 알람 권한 필요")
                .setMessage("일정을 정시에 알림받으려면 '정확한 알람 및 리마인더' 권한이 필요합니다.\n\n설정으로 이동하시겠습니까?")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    requestExactAlarmPermission()
                }
                .setNegativeButton("나중에") { _, _ ->
                    Log.w("MainActivity", "정확한 알람 권한 거부됨")
                }
                .show()
        } else {
            // 모든 권한 OK → 알림 스케줄링
            scheduleReminders()
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Android 12 미만은 권한 불필요
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            exactAlarmPermissionLauncher.launch(intent)
        }
    }

    private fun scheduleReminders() {
        Log.d("MainActivity", "모든 권한 확보됨 - 알림 스케줄링 시작")
        viewModel.scheduleNearestReminder(applicationContext)
    }

    private fun showPermissionDialog(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

}
