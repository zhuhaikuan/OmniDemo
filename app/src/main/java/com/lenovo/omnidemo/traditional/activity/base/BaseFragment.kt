package com.lenovo.omnidemo.traditional.activity.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding


/**
 * @date 2025/5/29 10:33
 * @author zhk
 */
abstract class BaseFragment<VB : ViewBinding>:  Fragment() {
    lateinit var binding: VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = initViewBinding()
        initData()
        return binding.root
    }

    abstract fun initViewBinding(): VB

    abstract fun initData()
}