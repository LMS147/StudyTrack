package com.studytrack.app.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studytrack.app.R
import com.studytrack.app.data.model.TaskSuggestion
import com.studytrack.app.data.remote.RetrofitClient
import com.studytrack.app.databinding.FragmentAiAssistantBinding
import com.studytrack.app.util.DateTimeUtils
import com.studytrack.app.util.NavResultKeys
import com.studytrack.app.util.hideKeyboard
import kotlinx.coroutines.launch

/**
 * Chat-style AI assistant. Bubbles for user/AI text, structured suggestion
 * cards with Accept / Edit / Reject, a typing indicator while waiting, and
 * in-memory history held by the ViewModel.
 */
class AiAssistantFragment : Fragment(), ChatAdapter.Listener {

    private var _binding: FragmentAiAssistantBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AiAssistantViewModel by viewModels { AiAssistantViewModel.FACTORY }

    private lateinit var adapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = AiAssistantFragmentArgs.fromBundle(requireArguments())

        adapter = ChatAdapter(this)
        binding.chatList.adapter = adapter
        (binding.chatList.layoutManager as LinearLayoutManager).stackFromEnd = true

        binding.sendButton.setOnClickListener { sendCurrentInput() }

        // Quick prompts send their text straight into the chat.
        binding.suggestionDueWeek.setOnClickListener {
            sendText(getString(R.string.ai_suggestion_due_this_week))
        }
        binding.suggestionPrioritise.setOnClickListener {
            sendText(getString(R.string.ai_suggestion_prioritise))
        }
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput()
                true
            } else {
                false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { items ->
                        // The quick prompts are a starting point: once the
                        // conversation has begun they stop taking up space.
                        binding.suggestionsScroll.isVisible =
                            items.none { it is ChatItem.UserMessage }
                        adapter.submitList(items) {
                            if (items.isNotEmpty()) {
                                binding.chatList.scrollToPosition(items.lastIndex)
                            }
                        }
                    }
                }
                launch {
                    viewModel.awaitingReply.collect { awaiting ->
                        binding.sendButton.isEnabled = !awaiting
                        binding.messageInput.isEnabled = !awaiting
                    }
                }
            }
        }

        // Result from the task editor when a suggestion was "Edit"-ed there.
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getLiveData<String>(NavResultKeys.AI_SUGGESTION_CREATED)
            ?.observe(viewLifecycleOwner) { suggestionItemId ->
                viewModel.markAcceptedFromEditor(suggestionItemId)
                savedStateHandle.remove<Any>(NavResultKeys.AI_SUGGESTION_CREATED)
            }

        viewModel.start(
            welcomeText = getString(R.string.ai_welcome_message),
            emptyReplyText = getString(R.string.ai_empty_reply),
            addedViaEditorText = getString(R.string.chat_added_via_editor),
            confirmationTemplate = getString(R.string.chat_added_confirmation),
            confirmationWithSubtasksTemplate = getString(R.string.chat_added_with_subtasks),
            confirmationSubjectSuffix = getString(R.string.chat_added_subject_suffix),
            initialPrompt = args.initialPrompt,
            contextTaskId = args.contextTaskId,
        )
    }

    private fun sendText(text: String) {
        hideKeyboard()
        viewModel.sendMessage(text)
        binding.messageInput.setText("")
    }

    private fun sendCurrentInput() {
        val text = binding.messageInput.text.toString()
        if (text.isNotBlank()) {
            hideKeyboard()
            viewModel.sendMessage(text)
            binding.messageInput.setText("")
        }
    }

    // ------------------------------------------------- ChatAdapter.Listener

    override fun onAcceptSuggestion(item: ChatItem.Suggestion) {
        if (item.effectiveDueDate == null) {
            // No date yet: ask for one and accept the moment it is picked.
            // The client still never *guesses* a date — but the user's tap on
            // Accept is honoured instead of being refused.
            acceptAfterDatePick = item.itemId
            onPickSuggestionDate(item)
            return
        }
        viewModel.acceptSuggestion(item.itemId)
    }

    override fun onEditSuggestion(item: ChatItem.Suggestion) {
        // Carry the effective date (backend-resolved or user-picked) and the
        // resolved subject into the editor, so editing doesn't lose the
        // subject the suggestion was matched to.
        val prefill = item.suggestion.copy(
            dueDate = item.effectiveDueDate,
            subjectId = item.effectiveSubjectId,
        )
        val prefillJson = try {
            RetrofitClient.json.encodeToString(TaskSuggestion.serializer(), prefill)
        } catch (e: Exception) {
            null
        }
        findNavController().navigate(
            AiAssistantFragmentDirections.actionAiAssistantFragmentToAddEditTaskFragment(
                prefillJson = prefillJson,
                aiSuggestionId = item.itemId,
            )
        )
    }

    override fun onRejectSuggestion(item: ChatItem.Suggestion) {
        viewModel.rejectSuggestion(item.itemId)
    }

    /**
     * Set when the user pressed Accept on a suggestion that has no due date:
     * the picker it opens then files the task straight after a date is chosen.
     * Cleared whenever the picker is dismissed without a choice.
     */
    private var acceptAfterDatePick: String? = null

    override fun onPickSuggestionDate(item: ChatItem.Suggestion) {
        val current = DateTimeUtils.parseDate(item.effectiveDueDate) ?: DateTimeUtils.today()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.select_due_date)
            .setSelection(DateTimeUtils.toUtcMillis(current))
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val date = DateTimeUtils.fromUtcMillis(millis)
            viewModel.setSuggestionDate(item.itemId, viewModel.userPickedIsoDate(date))
            // Accept came via the Accept button (not the date chip): honour it
            // now that the suggestion has the date it was missing.
            if (acceptAfterDatePick == item.itemId) {
                acceptAfterDatePick = null
                viewModel.acceptSuggestion(item.itemId)
            }
        }
        picker.addOnDismissListener { acceptAfterDatePick = null }
        picker.show(childFragmentManager, "suggestion_date")
    }

    override fun onPickSuggestionSubject(item: ChatItem.Suggestion) {
        val subjects = viewModel.subjects.value
        val options = mutableListOf<String>()
        val optionIds = mutableListOf<String?>()

        // Offer "No subject" only when one is currently attached, so the user
        // can undo a match.
        if (item.effectiveSubjectId != null) {
            options += getString(R.string.suggestion_subject_none_option)
            optionIds += null
        }
        subjects.forEach { subject ->
            options += subject.subjectName
            optionIds += subject.subjectId
        }
        options += getString(R.string.suggestion_new_subject)
        optionIds += CREATE_NEW_SUBJECT

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.suggestion_choose_subject)
            .setItems(options.toTypedArray()) { _, which ->
                val chosenId = optionIds[which]
                when (chosenId) {
                    null -> viewModel.setSuggestionSubject(item.itemId, null, null)
                    CREATE_NEW_SUBJECT -> promptNewSubject(item)
                    else -> viewModel.setSuggestionSubject(
                        item.itemId,
                        chosenId,
                        subjects.firstOrNull { it.subjectId == chosenId }?.subjectName,
                    )
                }
            }
            .show()
    }

    /**
     * "New subject…" — opens a one-field dialog pre-filled with the name the AI
     * suggested (e.g. "Mathematics"), creating the subject and filing the task
     * under it in one step.
     */
    private fun promptNewSubject(item: ChatItem.Suggestion) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.hint_subject_name)
            setText(item.suggestion.subjectName.orEmpty())
            setSelection(text.length)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.suggestion_new_subject)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.createSubjectForSuggestion(item.itemId, input.text.toString())
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        /** Sentinel option id for "New subject…" (real ids are UUIDs). */
        const val CREATE_NEW_SUBJECT = "__create_new_subject__"
    }
}
