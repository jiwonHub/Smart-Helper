package com.scchyodol.smarthelper.presentation.home.fragment.carelog

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.scchyodol.smarthelper.databinding.FragmentCareLogBinding
import com.scchyodol.smarthelper.presentation.home.carerecord.CareRecordActivity

class CareLogFragment : Fragment() {

    private var _binding: FragmentCareLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCareLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCardButtons()
    }

    private fun setupCardButtons() {
        // 투약
        binding.btnMedication.setOnClickListener {
            navigateToCareRecord(
                category = CareRecordActivity.CATEGORY_MEDICATION,
                title = "투약",
                subtitle = "Medication Record",
                color = "#4A90D9"
            )
        }

        // 수면
        binding.btnSleep.setOnClickListener {
            navigateToCareRecord(
                category = CareRecordActivity.CATEGORY_SLEEP,
                title = "수면",
                subtitle = "Sleep Record",
                color = "#7B52D3"
            )
        }

        // 식사
        binding.btnSiksa.setOnClickListener {
            navigateToCareRecord(
                category = CareRecordActivity.CATEGORY_MEAL,
                title = "식사",
                subtitle = "Meal Record",
                color = "#F5A623"
            )
        }

        // 배변
        binding.btnExcretion.setOnClickListener {
            navigateToCareRecord(
                category = CareRecordActivity.CATEGORY_EXCRETION,
                title = "배변",
                subtitle = "Excretion Record",
                color = "#4CAF82"
            )
        }

        // 체온/SpO2
        binding.btnVitals.setOnClickListener {
            navigateToCareRecord(
                category = CareRecordActivity.CATEGORY_TEMPERATURE,
                title = "체온/SpO2",
                subtitle = "Vitals Record",
                color = "#E84C4C"
            )
        }

        // 기타
        binding.btnOther.setOnClickListener {
            navigateToCareRecord(
                category = CareRecordActivity.CATEGORY_OTHER,
                title = "기타",
                subtitle = "Other Record",
                color = "#26A69A"
            )
        }
    }

    private fun navigateToCareRecord(
        category: String,
        title: String,
        subtitle: String,
        color: String
    ) {
        val intent = Intent(requireContext(), CareRecordActivity::class.java).apply {
            putExtra(CareRecordActivity.EXTRA_CATEGORY, category)
            putExtra(CareRecordActivity.EXTRA_TITLE, title)
            putExtra(CareRecordActivity.EXTRA_SUBTITLE, subtitle)
            putExtra(CareRecordActivity.EXTRA_COLOR, color)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
