package com.scchyodol.smarthelper.presentation.home.fragment.lovemessage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R

class LoveMessageFragment : Fragment() {

    //  더미 아이템 1개
    private val dummyItems = mutableListOf(
        LoveMessageItem(
            title    = "숫자세기",
            duration = "0분 3초"
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_love_message, container, false)
        setupRecyclerView(view)
        return view
    }

    private fun setupRecyclerView(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rvMessages)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = LoveMessageAdapter(dummyItems)
        rv.isNestedScrollingEnabled = false
    }
}
