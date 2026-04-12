package com.androidapp.pomodorotimer.ui.list

import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.androidapp.pomodorotimer.App
import com.androidapp.pomodorotimer.R
import com.androidapp.pomodorotimer.databinding.FragmentPresetListBinding
import kotlinx.coroutines.launch

class PresetListFragment : Fragment() {

    private var _binding: FragmentPresetListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PresetListViewModel by viewModels {
        PresetListViewModel.Factory((requireActivity().application as App).presetRepository)
    }

    private lateinit var touchHelper: ItemTouchHelper
    private var dragFromIndex = -1
    private var dragToIndex = -1

    private lateinit var adapter: PresetAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPresetListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                viewModel.cancelChanges()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        adapter = PresetAdapter(
            onTap = { preset ->
                if (!viewModel.uiState.value.isSelectionMode) {
                    findNavController().navigate(
                        R.id.action_list_to_timerRun,
                        Bundle().apply { putInt("presetId", preset.id) }
                    )
                }
            },
            onLongPress = { preset ->
                viewModel.enterSelectionMode(preset.id)
            },
            onEditSwipe = { preset ->
                findNavController().navigate(
                    R.id.action_list_to_presetEdit,
                    Bundle().apply { putInt("presetId", preset.id) }
                )
            },
            onDeleteSwipe = { preset ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_delete_title)
                    .setMessage(getString(R.string.dialog_delete_message, preset.name))
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        viewModel.deletePreset(preset)
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            },
            onCheckToggle = { preset ->
                viewModel.toggleSelection(preset.id)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // スクロール時に開いているスワイプを閉じる
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    closeAllSwipes()
                }
            }
        })

        setupTouchHelper()
        adapter.itemTouchHelper = touchHelper
        touchHelper.attachToRecyclerView(binding.recyclerView)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                if (viewModel.uiState.value.isSelectionMode) {
                    menuInflater.inflate(R.menu.menu_preset_selection, menu)
                }
            }

            override fun onPrepareMenu(menu: Menu) {
                val state = viewModel.uiState.value
                if (!state.isSelectionMode) {
                    menu.clear()
                    return
                }
                if (menu.findItem(R.id.action_select_all) == null) {
                    requireActivity().menuInflater.inflate(R.menu.menu_preset_selection, menu)
                }
                menu.findItem(R.id.action_select_all)?.apply {
                    if (state.allSelected) {
                        setIcon(R.drawable.ic_deselect)
                        setTitle(R.string.action_deselect_all)
                    } else {
                        setIcon(R.drawable.ic_select_all)
                        setTitle(R.string.action_select_all)
                    }
                }
                val hasSelection = state.selectedCount > 0
                menu.findItem(R.id.action_copy)?.isEnabled = hasSelection
                menu.findItem(R.id.action_delete_selected)?.isEnabled = hasSelection
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_select_all -> {
                        if (viewModel.uiState.value.allSelected) viewModel.clearSelection()
                        else viewModel.selectAll()
                        requireActivity().invalidateOptionsMenu()
                        true
                    }
                    R.id.action_copy -> {
                        viewModel.copySelected()
                        true
                    }
                    R.id.action_delete_selected -> {
                        val count = viewModel.uiState.value.selectedCount
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.dialog_delete_title)
                            .setMessage(getString(R.string.dialog_delete_selected_message, count))
                            .setPositiveButton(R.string.action_delete) { _, _ ->
                                viewModel.deleteSelected()
                            }
                            .setNegativeButton(R.string.action_cancel, null)
                            .show()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.buttonSelectionCancel.setOnClickListener {
            viewModel.cancelChanges()
        }
        binding.buttonSelectionSave.setOnClickListener {
            viewModel.commitChanges()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                val prevSelectionMode = adapter.isSelectionMode
                val prevSelectedIds = adapter.selectedIds
                adapter.isSelectionMode = state.isSelectionMode
                adapter.selectedIds = state.selectedIds
                adapter.submitList(state.presets)

                if (prevSelectionMode != state.isSelectionMode
                    || prevSelectedIds != state.selectedIds) {
                    adapter.notifyDataSetChanged()
                }

                // 選択モード突入時はスワイプを全部閉じる
                if (state.isSelectionMode && !prevSelectionMode) {
                    closeAllSwipes()
                }

                backCallback.isEnabled = state.isSelectionMode

                val activity = requireActivity() as AppCompatActivity
                if (state.isSelectionMode) {
                    activity.supportActionBar?.title =
                        getString(R.string.selection_count, state.selectedCount)
                    binding.fabAddPreset.hide()
                    binding.selectionActionBar.visibility = View.VISIBLE
                } else {
                    activity.supportActionBar?.title = getString(R.string.screen_preset_list)
                    binding.fabAddPreset.show()
                    binding.selectionActionBar.visibility = View.GONE
                }

                requireActivity().invalidateOptionsMenu()
            }
        }

        binding.fabAddPreset.setOnClickListener {
            findNavController().navigate(
                R.id.action_list_to_presetEdit,
                Bundle().apply { putInt("presetId", -1) }
            )
        }
    }

    private fun closeAllSwipes() {
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        for (i in first..last) {
            val vh = binding.recyclerView.findViewHolderForAdapterPosition(i)
            if (vh is PresetAdapter.ViewHolder) vh.resetSwipe()
        }
    }

    private fun setupTouchHelper() {
        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                return if (adapter.isSelectionMode)
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                else
                    makeMovementFlags(0, 0)
            }

            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.adapterPosition
                val to = target.adapterPosition
                if (from < 0 || to < 0) return false
                if (dragFromIndex == -1) dragFromIndex = from
                dragToIndex = to
                adapter.onItemMoved(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled() = false

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (isCurrentlyActive) vh.itemView.alpha = 0.7f
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                vh.itemView.alpha = 1f
                val from = dragFromIndex
                val to = dragToIndex
                dragFromIndex = -1
                dragToIndex = -1
                if (from != -1 && to != -1 && from != to) {
                    viewModel.movePreset(from, to)
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}