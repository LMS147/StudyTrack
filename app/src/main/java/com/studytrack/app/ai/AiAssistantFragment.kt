package com.studytrack.app.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.studytrack.app.databinding.FragmentAiAssistantBinding

/**
 * Placeholder screen — implemented in a later build step.
 */
class AiAssistantFragment : Fragment() {

    private var _binding: FragmentAiAssistantBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
