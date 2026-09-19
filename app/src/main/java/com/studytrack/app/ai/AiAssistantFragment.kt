package com.studytrack.app.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.studytrack.app.R
import com.studytrack.app.data.model.TaskSuggestion
import com.studytrack.app.data.remote.RetrofitClient
import com.studytrack.app.databinding.FragmentAiAssistantBinding
import com.studytrack.app.util.DateTimeUtils
import com.studytrack.app.util.NavResultKeys
import com.studytrack.app.util.hideKeyboard
import com.studytrack.app.util.toast
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
                savedStateHandle.remove(NavResultKeys.AI_SUGGESTION_CREATED)
            }

        viewModel.start(
            welcomeText = getString(R.string.ai_welcome_message),
            emptyReplyText = getString(R.string.ai_empty_reply),
            addedViaEditorText = getString(R.string.chat_added_via_editor),
            confirmationTemplate = getString(R.string.chat_added_confirmation),
            confirmationWithSubtasksTemplate = getString(R.string.chat_added_with_subtasks),
            initialPrompt = args.initialPrompt,
            contextTaskId = args.contextTaskId,
        )
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
            toast(getString(R.string.suggestion_needs_date))
            return
        }
        viewModel.acceptSuggestion(item.itemId)
    }

    override fun onEditSuggestion(item: ChatItem.Suggestion) {
        // Carry the effective date (backend-resolved or user-picked) into the editor.
        val prefill = item.suggestion.copy(dueDate = item.effectiveDueDate)
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

    override fun onPickSuggestionDate(item: ChatItem.Suggestion) {
        val current = DateTimeUtils.parseDate(item.effectiveDueDate) ?: DateTimeUtils.today()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.select_due_date)
            .setSelection(DateTimeUtils.toUtcMillis(current))
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val date = DateTimeUtils.fromUtcMillis(millis)
            viewModel.setSuggestionDate(item.itemId, viewModel.userPickedIsoDate(date))
        }
        picker.show(childFragmentManager, "suggestion_date")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
