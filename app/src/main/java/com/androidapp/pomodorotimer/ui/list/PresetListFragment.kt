package com.androidapp.pomodorotimer.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidapp.pomodorotimer.App
import com.androidapp.pomodorotimer.R
import com.androidapp.pomodorotimer.databinding.FragmentPresetListBinding
import kotlinx.coroutines.launch

class PresetListFragment : Fragment() {

    private var _binding: FragmentPresetListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PresetListViewModel by viewModels {
        PresetListViewModel.Factory(
            (requireActivity().application as App).presetRepository
        )
    }

    private val adapter = PresetAdapter(
        onTap = { preset ->
            findNavController().navigate(
                R.id.action_list_to_timer,
                Bundle().apply { putInt("presetId", preset.id) }
            )
        },
        onLongPress = { preset ->
            findNavController().navigate(
                R.id.action_list_to_edit,
                Bundle().apply { putInt("presetId", preset.id) }
            )
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPresetListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.presets.collect { presets ->
                adapter.submitList(presets)
            }
        }

        binding.fabAddPreset.setOnClickListener {
            findNavController().navigate(R.id.action_list_to_create)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}