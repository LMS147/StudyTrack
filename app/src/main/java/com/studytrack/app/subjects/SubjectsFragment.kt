package com.studytrack.app.subjects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studytrack.app.R
import com.studytrack.app.data.model.Subject
import com.studytrack.app.databinding.FragmentSubjectsBinding
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.toast
import kotlinx.coroutines.launch

class SubjectsFragment : Fragment() {

    private var _binding: FragmentSubjectsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SubjectsViewModel by viewModels { SubjectsViewModel.FACTORY }

    private lateinit var adapter: SubjectAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubjectsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SubjectAdapter(
            onSubjectClick = { item ->
                findNavController().navigate(
                    SubjectsFragmentDirections
                        .actionSubjectsFragmentToSubjectDetailFragment(item.subject.subjectId)
                )
            },
            onEditSubject = { subject -> showAddEditDialog(subject) },
            onDeleteSubject = { subject -> confirmDelete(subject) },
        )
        binding.subjectsList.adapter = adapter
        binding.subjectsList.layoutManager = LinearLayoutManager(requireContext())

        binding.addSubjectFab.setOnClickListener { showAddEditDialog(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.subjectsWithProgress.collect { items ->
                        adapter.submitList(items)
                        binding.emptyState.isVisible = items.isEmpty()
                    }
                }
                launch {
                    viewModel.loadState.collect { state -> renderLoadState(state) }
                }
                launch {
                    viewModel.deleteState.collect { state ->
                        if (state is ApiResult.Error) {
                            toast(state.message)
                        }
                    }
                }
            }
        }
    }

    private fun renderLoadState(state: ApiResult<Unit>?) {
        val loading = state is ApiResult.Loading
        val isEmpty = adapter.itemCount == 0
        binding.loadingProgress.isVisible = loading && isEmpty
        binding.addSubjectFab.isEnabled = !loading

        when (state) {
            is ApiResult.Error -> {
                if (isEmpty) {
                    binding.emptyState.isVisible = true
                    binding.emptyState.text = state.message
                    binding.emptyState.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            requireContext(), R.color.overdue_red
                        )
                    )
                } else {
                    toast(state.message)
                }
            }
            is ApiResult.Success, ApiResult.Loading, null -> {
                if (isEmpty && !loading) {
                    binding.emptyState.isVisible = true
                    binding.emptyState.text = getString(R.string.empty_subjects)
                    binding.emptyState.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            requireContext(), R.color.text_secondary
                        )
                    )
                }
            }
        }
    }

    private fun showAddEditDialog(subject: Subject?) {
        AddEditSubjectDialogFragment.newInstance(subject)
            .show(childFragmentManager, AddEditSubjectDialogFragment.TAG)
    }

    private fun confirmDelete(subject: Subject) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_subject_confirm_title))
            .setMessage(getString(R.string.delete_subject_confirm_message))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteSubject(subject.subjectId)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
