package org.readium.r2.testapp.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.readium.r2.testapp.AINEW.AiProviderType
import org.readium.r2.testapp.Application
import org.readium.r2.testapp.R
import org.readium.r2.testapp.databinding.FragmentApiKeySetupBinding

class ApiKeySetupFragment : Fragment() {

    private var _binding: FragmentApiKeySetupBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { requireActivity().application as Application }

    private val providerNames = listOf(
        "OpenRouter",
        "Google Gemini",
        "OpenAI",
        "DeepSeek"
    )

    private var selectedProvider: AiProviderType? = null
    private var userManuallySelectedProvider = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiKeySetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupProviderDropdown()
        setupTextWatchers()
        setupClickListeners()
        loadCurrentSettings()
    }

    private fun setupProviderDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            providerNames
        )

        binding.editProvider.setAdapter(adapter)

        binding.editProvider.setOnItemClickListener { _, _, position, _ ->
            val providerName = providerNames[position]
            selectedProvider = providerFromUiName(providerName)
            userManuallySelectedProvider = true
            updateModelHints()
        }
    }

    private fun setupTextWatchers() {
        binding.editApiKey.doAfterTextChanged { text ->
            val key = text?.toString()?.trim().orEmpty()

            if (key.isBlank()) return@doAfterTextChanged

            if (!userManuallySelectedProvider) {
                val detected = AiProviderType.fromApiKey(key)

                if (detected != AiProviderType.UNKNOWN) {
                    selectedProvider = detected
                    binding.editProvider.setText(providerUiName(detected), false)
                    updateModelHints()
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            saveCredentials()
        }

        binding.btnDelete.setOnClickListener {
            deleteCredentials()
        }
    }

    private fun loadCurrentSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (app.hasAiApiKey()) {
                val key = app.getAiApiKey()
                val provider = app.getAiProviderType()
                val customModel = app.getAiCustomModel()
                val customEmbeddingModel = app.getAiCustomEmbeddingModel()

                binding.editApiKey.setText(key.orEmpty())

                selectedProvider = provider
                userManuallySelectedProvider = true

                if (provider != AiProviderType.UNKNOWN) {
                    binding.editProvider.setText(providerUiName(provider), false)
                }

                binding.editModel.setText(customModel.orEmpty())
                binding.editEmbeddingModel.setText(customEmbeddingModel.orEmpty())

                binding.tvInstruction.text = getString(
                    R.string.api_key_already_saved,
                    providerUiName(provider)
                )

                updateModelHints()
            } else {
                selectedProvider = AiProviderType.GOOGLE_GEMINI
                userManuallySelectedProvider = false
                binding.editProvider.setText(
                    providerUiName(AiProviderType.GOOGLE_GEMINI),
                    false
                )
                updateModelHints()
            }
        }
    }

    private fun saveCredentials() {
        val key = binding.editApiKey.text.toString().trim()

        if (key.isEmpty()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.api_key_hint_empty),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val provider = selectedProvider ?: AiProviderType.fromApiKey(key)

        if (provider == AiProviderType.UNKNOWN) {
            Toast.makeText(
                requireContext(),
                getString(R.string.api_key_format_unknown),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val chatModel = binding.editModel.text.toString().trim()
        val embeddingModel = binding.editEmbeddingModel.text.toString().trim()

        viewLifecycleOwner.lifecycleScope.launch {
            app.saveAiCredentials(
                key = key,
                providerType = provider,
                chatModel = chatModel,
                embeddingModel = embeddingModel
            )

            val effectiveModel = chatModel.ifBlank {
                defaultModelFor(provider)
            }

            Toast.makeText(
                requireContext(),
                getString(
                    R.string.ai_setup_save_success,
                    providerUiName(provider),
                    effectiveModel
                ),
                Toast.LENGTH_SHORT
            ).show()

            activity?.finish()
        }
    }

    private fun deleteCredentials() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.saveAiCredentials(
                key = "",
                providerType = AiProviderType.UNKNOWN,
                chatModel = "",
                embeddingModel = ""
            )

            binding.editApiKey.text?.clear()
            binding.editModel.text?.clear()
            binding.editEmbeddingModel.text?.clear()
            binding.editProvider.setText("", false)

            selectedProvider = null
            userManuallySelectedProvider = false

            binding.tvInstruction.text = getString(R.string.keydelleted)

            Toast.makeText(
                requireContext(),
                R.string.keydel,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateModelHints() {
        val provider = selectedProvider ?: return

        binding.modelInputLayout.helperText = getString(
            R.string.ai_setup_default_model,
            defaultModelFor(provider)
        )

        binding.embeddingInputLayout.helperText = getString(
            R.string.ai_setup_default_model,
            defaultEmbeddingModelFor(provider)
        )
    }

    private fun providerUiName(type: AiProviderType): String {
        return when (type) {
            AiProviderType.OPENROUTER -> "OpenRouter"
            AiProviderType.GOOGLE_GEMINI -> "Google Gemini"
            AiProviderType.OPENAI -> "OpenAI"
            AiProviderType.DEEPSEEK -> "DeepSeek"
            AiProviderType.UNKNOWN -> ""
        }
    }

    private fun providerFromUiName(name: String): AiProviderType {
        return when (name) {
            "OpenRouter" -> AiProviderType.OPENROUTER
            "Google Gemini" -> AiProviderType.GOOGLE_GEMINI
            "OpenAI" -> AiProviderType.OPENAI
            "DeepSeek" -> AiProviderType.DEEPSEEK
            else -> AiProviderType.UNKNOWN
        }
    }

    private fun defaultModelFor(provider: AiProviderType): String {
        return when (provider) {
            AiProviderType.OPENROUTER -> "deepseek/deepseek-chat"
            AiProviderType.GOOGLE_GEMINI -> "gemini-3.6-flash"
            AiProviderType.OPENAI -> "gpt-4o-mini"
            AiProviderType.DEEPSEEK -> "deepseek-chat"
            AiProviderType.UNKNOWN -> ""
        }
    }

    private fun defaultEmbeddingModelFor(provider: AiProviderType): String {
        return when (provider) {
            AiProviderType.OPENROUTER -> "openai/text-embedding-3-small"
            AiProviderType.GOOGLE_GEMINI -> "gemini-embedding-001"
            AiProviderType.OPENAI -> "text-embedding-3-small"
            AiProviderType.DEEPSEEK -> "deepseek-embedding"
            AiProviderType.UNKNOWN -> ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}