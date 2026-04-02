package com.scchyodol.smarthelper.presentation.home.fragment.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.data.model.CareCategory
import com.scchyodol.smarthelper.presentation.home.main.MainViewModel
import com.scchyodol.smarthelper.util.Result
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
    }

    // ✅ MainViewModel 하나로 전부 해결
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        observeWeather(view)
        observeNextTask(view)
        return view
    }

    private fun observeWeather(view: View) {
        val tvTemp  = view.findViewById<TextView>(R.id.tvWeatherSummary)
        val tvHumid = view.findViewById<TextView>(R.id.tvHumiditySummary)

        tvTemp.text  = "--"
        tvHumid.text = "--"

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.weatherState.collect { result ->
                    when (result) {
                        is Result.Loading -> Log.d(TAG, "날씨 로딩 중...")
                        is Result.Success -> {
                            val data = result.data
                            tvTemp.text  = "${data.current.temperature}${data.currentUnits.temperatureUnit}"
                            tvHumid.text = "${data.current.humidity}${data.currentUnits.humidityUnit}"
                        }
                        is Result.Error -> {
                            tvTemp.text  = "--"
                            tvHumid.text = "--"
                            Log.e(TAG, "날씨 오류: ${result.message}")
                        }
                    }
                }
            }
        }
    }

    private fun observeNextTask(view: View) {
        val tvNextTaskLabel = view.findViewById<TextView>(R.id.tvNextTaskLabel)
        val tvCountdown     = view.findViewById<TextView>(R.id.tvCountdown)

        tvNextTaskLabel.text = "불러오는 중..."
        tvCountdown.text     = "--"

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.nextTask.collect { record ->
                    if (record != null) {
                        val categoryStr = getCategoryLabel(record.category)

                        val timeStr = SimpleDateFormat("M월 d일 a hh:mm", Locale.KOREA).format(Date(record.timestamp))

                        tvNextTaskLabel.text = "$timeStr · $categoryStr"
                        Log.d(TAG, "다음 할 일: $timeStr / $categoryStr")
                    } else {
                        tvNextTaskLabel.text = "📅 다음 스케줄이 없습니다"
                        tvCountdown.text     = "✏️ 새로운 일정을 등록해주세요!"
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.countdown.collect { text ->
                    tvCountdown.text = text
                }
            }
        }
    }

    private fun getCategoryLabel(category: CareCategory): String = category.displayName

}
