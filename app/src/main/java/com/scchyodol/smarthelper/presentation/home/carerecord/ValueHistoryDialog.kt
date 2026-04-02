package com.scchyodol.smarthelper.presentation.home.carerecord

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.scchyodol.smarthelper.databinding.DialogValueHistoryBinding
import kotlinx.coroutines.launch

class ValueHistoryDialog(
    private val category     : String,
    private val currentDefault: String,
    private val accentColor  : Int,
    private val historyFlow  : kotlinx.coroutines.flow.Flow<List<String>>,
    private val onConfirm    : (String) -> Unit
) : DialogFragment() {

    private var _binding: DialogValueHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ValueHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogValueHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDialogAppearance()
        setupRecyclerView()
        observeHistory()
        setupButtons()
    }

    private fun setupDialogAppearance() {
        // ✅ 다이얼로그 배경 투명 + 둥근 모양 살리기
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        // ✅ 확인 버튼 색상 → 카테고리 accentColor
        binding.btnConfirm.backgroundTintList =
            android.content.res.ColorStateList.valueOf(accentColor)
    }

    private fun setupRecyclerView() {
        // ✅ 초기 선택값 = 현재 기본값
        adapter = ValueHistoryAdapter(
            selectedValue  = currentDefault,
            onItemSelected = { /* 어댑터 내부에서 selectedValue 갱신 */ }
        )

        binding.rvValueHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter  = this@ValueHistoryDialog.adapter
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )
        }
    }

    private fun observeHistory() {
        Log.d("ValueHistoryDialog", "observeHistory 시작 - category: '$category'")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                historyFlow.collect { dbValues ->
                    Log.d("ValueHistoryDialog", "Flow에서 수신된 값들: $dbValues")

                    val merged = buildMergedList(dbValues)
                    Log.d("ValueHistoryDialog", "최종 어댑터에 전달할 리스트:")
                    merged.forEachIndexed { index, item ->
                        Log.d("ValueHistoryDialog", "  [$index] value='${item.value}', isDefault=${item.isCurrentDefault}")
                    }

                    adapter.submitList(merged)
                }
            }
        }
    }

    private fun buildMergedList(dbValues: List<String>): List<ValueHistoryItem> {
        val result = mutableListOf<ValueHistoryItem>()

        // ① 기본값 항목 (항상 포함)
        result.add(
            ValueHistoryItem(
                value            = currentDefault,
                isCurrentDefault = true
            )
        )

        // ② DB 기록 (currentDefault 중복 제거)
        dbValues
            .filter { it != currentDefault }
            .forEach { dbVal ->
                result.add(
                    ValueHistoryItem(
                        value            = dbVal,
                        isCurrentDefault = false
                    )
                )
            }

        return result
    }

    private fun setupButtons() {
        // ✅ 닫기
        binding.btnDialogClose.setOnClickListener { dismiss() }

        // ✅ 확인 → 선택된 값 콜백
        binding.btnConfirm.setOnClickListener {
            val selected = adapter.getSelectedValue()
            onConfirm(selected)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
