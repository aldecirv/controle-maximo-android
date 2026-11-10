package com.controlemaximo.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.controlemaximo.app.databinding.SheetMoreBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MoreSheet : BottomSheetDialogFragment() {
    private var _binding: SheetMoreBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetMoreBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.close.setOnClickListener { dismiss() }
        binding.vitrineCard.setOnClickListener { open(ShowcaseActivity::class.java) }
        binding.reportsCard.setOnClickListener { open(ReportsActivity::class.java) }
        binding.securityCard.setOnClickListener { open(SecurityActivity::class.java) }
        binding.settingsCard.setOnClickListener { open(SettingsActivity::class.java) }
    }
    private fun open(cls: Class<*>) { startActivity(Intent(requireContext(), cls)); dismiss() }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
