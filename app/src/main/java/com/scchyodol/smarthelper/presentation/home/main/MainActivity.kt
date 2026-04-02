package com.scchyodol.smarthelper.presentation.home.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            // ✅ 권한 받으면 여기서 fetchWeather 호출
            viewModel.fetchWeather()
        } else {
            Snackbar.make(
                binding.root,
                "위치 권한이 필요합니다. 날씨 정보를 불러올 수 없습니다.",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBottomNavigation()
        observeUser()
        checkAndRequestLocationPermission()

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
                R.id.nav_love_message -> loadFragment(loveMessageFragment)
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
        }
    }

    fun navigateToTab(itemId: Int) {
        binding.bottomNavigation.selectedItemId = itemId
    }
}
