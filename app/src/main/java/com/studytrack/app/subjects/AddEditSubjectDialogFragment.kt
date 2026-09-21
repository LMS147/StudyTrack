package com.studytrack.app.subjects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.studytrack.app.R
import com.studytrack.app.data.model.Subject
import com.studytrack.app.databinding.DialogAddEditSubjectBinding
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.launch

/**
 * Add/Edit subject dialog. Both actions go through [SubjectsViewModel] into
 * the SubjectRepository; the Subjects list screen updates automatically by
 * observing the repository's subjects StateFlow.
 */
class AddEditSubjectDialogFragment : DialogFragment() {

    private var _binding: DialogAddEditSubjectBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SubjectsViewModel by viewModels { SubjectsViewModel.FACTORY }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddEditSubjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        val subjectId = args.getString(ARG_SUBJECT_ID)

        binding.dialogTitle.text = getString(
            if (subjectId == null) R.string.dialog_subject_new_title
            else R.string.dialog_subject_edit_title
        )
        binding.nameField.setText(args.getString(ARG_SUBJECT_NAME).orEmpty())
        binding.descriptionField.setText(args.getString(ARG_SUBJECT_DESCRIPTION).orEmpty())

        binding.cancelButton.setOnClickListener { dismiss() }
        binding.saveButton.setOnClickListener { save(subjectId) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveState.collect { state ->
                    val saving = state is ApiResult.Loading
                    binding.saveProgress.isVisible = saving
                    binding.saveButton.isEnabled = !saving
                    binding.nameField.isEnabled = !saving
                    binding.descriptionField.isEnabled = !saving

                    if (state is ApiResult.Error) {
                        binding.errorText.text = state.message
                        binding.errorText.isVisible = true
                    } else {
                        binding.errorText.isVisible = false
                    }
                    if (state is ApiResult.Success) {
                        dismiss()
                    }
                }
            }
        }
    }

    private fun save(subjectId: String?) {
        val name = binding.nameField.text.toString().trim()
        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.error_subject_name_required)
            return
        }
        binding.nameLayout.error = null
        val description = binding.descriptionField.text.toString().trim().ifEmpty { null }

        if (subjectId == null) {
            viewModel.createSubject(name, description)
        } else {
            viewModel.updateSubject(subjectId, name, description)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddEditSubjectDialog"

        private const val ARG_SUBJECT_ID = "subject_id"
        private const val ARG_SUBJECT_NAME = "subject_name"
        private const val ARG_SUBJECT_DESCRIPTION = "subject_description"

        fun newInstance(subject: Subject? = null): AddEditSubjectDialogFragment =
            AddEditSubjectDialogFragment().apply {
                arguments = bundleOf(
                    ARG_SUBJECT_ID to subject?.subjectId,
                    ARG_SUBJECT_NAME to subject?.subjectName,
                    ARG_SUBJECT_DESCRIPTION to subject?.description,
                )
            }
    }
}
