package org.readium.r2.testapp.ai

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.testapp.Application
import org.readium.r2.testapp.R
import org.readium.r2.testapp.bookshelf.BookshelfAdapter
import org.readium.r2.testapp.data.model.Book
import org.readium.r2.testapp.databinding.DialogBookPickerBinding
import org.readium.r2.testapp.databinding.FragmentAiChatBinding
import org.readium.r2.testapp.utils.CoverLoader
import timber.log.Timber

class AiChatFragment : Fragment() {

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var app: Application
    private var selectedBook: Book? = null
    private val viewModel: AiChatViewModel by viewModels()
    private lateinit var adapter: MessageAdapter
    private lateinit var historyAdapter: ChatHistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = requireActivity().application as Application
        setupRecyclerView()
        setupClickListeners()
        setupChatHistoryDrawer()
        observeMessages()
        observeLoading()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val key = app.getAiApiKey()
            if (!key.isNullOrBlank()) {
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        binding.recyclerMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.recyclerMessages.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            val text = binding.editMessage.text.toString().trim()
            if (text.isNotEmpty() || selectedBook != null) {
                sendMessage(text)
                binding.editMessage.text.clear()
                selectedBook = null
                updateSelectedBookUI()
            }
        }
        binding.btnAttach.setOnClickListener { showBookSelectionDialog() }
        binding.btnClearSelectedBook.setOnClickListener {
            selectedBook = null
            updateSelectedBookUI()
        }
        binding.btnStop.setOnClickListener { viewModel.cancelActiveOperation() }
        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clean_chatai)
                .setMessage(R.string.clean_allchatai)
                .setPositiveButton(R.string.clean) { _, _ -> viewModel.clearMessages() }
                .setNegativeButton(R.string.cancelai, null)
                .show()
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), AiSetupActivity::class.java))
        }

        // === Сайдбар истории ===
        binding.btnHistory.setOnClickListener { binding.chatDrawer.openDrawer(binding.drawerPanel) }
        binding.btnNewChat.setOnClickListener { viewModel.startNewChat() }
        binding.btnNewChatSide.setOnClickListener {
            viewModel.startNewChat()
            binding.chatDrawer.closeDrawers()
        }
    }

    // ================= История чатов =================

    private fun setupChatHistoryDrawer() {
        historyAdapter = ChatHistoryAdapter(
            onChatClick = { chat ->
                viewModel.openChat(chat.id)
                binding.chatDrawer.closeDrawers()
            },
            onRenameClick = { showRenameDialog(it) },
            onDeleteClick = { showDeleteChatDialog(it) },
            onExportClick = { exportChat(it) },
            onShareClick = { shareChat(it) }
        )
        binding.recyclerChats.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerChats.adapter = historyAdapter

        viewModel.chatList.observe(viewLifecycleOwner) { list ->
            historyAdapter.submitList(list)
        }
        viewModel.currentChat.observe(viewLifecycleOwner) { chat ->
            binding.tvChatTitle.text = chat?.title ?: getString(R.string.chat_default_title)
            historyAdapter.setActiveChat(chat?.id)
        }

        // Back сначала закрывает сайдбар
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.chatDrawer.isDrawerOpen(binding.drawerPanel)) {
                        binding.chatDrawer.closeDrawers()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun showRenameDialog(chat: ChatSession) {
        val input = EditText(requireContext()).apply {
            setText(chat.title)
            setSelection(chat.title.length)
            setPadding(56, 40, 56, 16)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename_chat)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) viewModel.renameChat(chat.id, newTitle)
            }
            .setNegativeButton(R.string.cancelai, null)
            .show()
    }

    private fun showDeleteChatDialog(chat: ChatSession) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_chat)
            .setMessage(getString(R.string.delete_chat_confirm, chat.title))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteChat(chat.id) }
            .setNegativeButton(R.string.cancelai, null)
            .show()
    }

    /** Скачивание чата как .txt в системную папку «Загрузки». */
    private fun exportChat(chat: ChatSession) {
        val text = viewModel.getChatExportText(chat.id)
        if (text.isBlank()) return
        val fileName = "${sanitizeFileName(chat.title)}.txt"
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = requireContext().contentResolver
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("MediaStore returned null URI")
                    requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Cannot open output stream")
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    File(downloadsDir, fileName).writeText(text, Charsets.UTF_8)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.chat_export_saved, fileName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Chat export failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.chat_export_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** «Поделиться» — системный chooser с текстом чата. */
    private fun shareChat(chat: ChatSession) {
        val text = viewModel.getChatExportText(chat.id)
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, chat.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chat)))
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "chat" }

    // ================= Остальное (без изменений) =================

    @SuppressLint("StringFormatInvalid")
    private fun updateSelectedBookUI() {
        val book = selectedBook
        if (book != null) {
            binding.selectedBookContainer.visibility = View.VISIBLE
            binding.tvSelectedBook.text = getString(R.string.Attached, book.title)
            CoverLoader.load(book.cover, book.title, binding.ivSelectedCover)
        } else {
            binding.selectedBookContainer.visibility = View.GONE
            binding.tvSelectedBook.text = ""
            binding.ivSelectedCover.setImageBitmap(null)
        }
    }

    private fun showBookSelectionDialog() {
        val context = requireContext()
        val bottomSheetDialog = BottomSheetDialog(context)
        val picker = DialogBookPickerBinding.inflate(layoutInflater)

        val adapter = BookshelfAdapter(
            context = context,
            onBookClick = { book ->
                selectedBook = book
                updateSelectedBookUI()
                bottomSheetDialog.dismiss()
                viewModel.prepareBookContext(book, app.aiManager)
            },
            onBookLongClick = {}
        )
        val layoutManager = LinearLayoutManager(context)
        picker.recyclerPickerBooks.layoutManager = layoutManager
        picker.recyclerPickerBooks.adapter = adapter

        var allBooks: List<Book> = emptyList()
        var filteredBooks: List<Book> = emptyList()
        var booksLoaded = false
        var visibleCount = PICKER_PAGE_SIZE

        fun submitVisible() {
            adapter.submitList(filteredBooks.take(visibleCount))
            picker.tvPickerCount.text =
                if (filteredBooks.size == allBooks.size) "${allBooks.size}"
                else getString(R.string.book_picker_count_format, filteredBooks.size, allBooks.size)
            picker.tvPickerEmpty.visibility =
                if (booksLoaded && filteredBooks.isEmpty()) View.VISIBLE else View.GONE
        }

        fun applyFilter(query: String) {
            val q = query.trim()
            filteredBooks = if (q.isEmpty()) {
                allBooks
            } else {
                allBooks.filter { book ->
                    book.title?.contains(q, ignoreCase = true) == true ||
                        book.author?.contains(q, ignoreCase = true) == true ||
                        book.href.substringAfterLast('/').contains(q, ignoreCase = true)
                }
            }
            visibleCount = PICKER_PAGE_SIZE
            submitVisible()
        }

        picker.recyclerPickerBooks.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || visibleCount >= filteredBooks.size) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - 3) {
                    visibleCount = (visibleCount + PICKER_PAGE_SIZE).coerceAtMost(filteredBooks.size)
                    submitVisible()
                }
            }
        })

        var searchJob: Job? = null
        picker.editSearchBook.doAfterTextChanged { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(250)
                applyFilter(text?.toString().orEmpty())
            }
        }
        picker.editSearchBook.setOnEditorActionListener { v, _, _ ->
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(v.windowToken, 0)
            true
        }

        lifecycleScope.launch {
            allBooks = app.bookRepository.books().first()
            booksLoaded = true
            applyFilter(picker.editSearchBook.text?.toString().orEmpty())
        }

        bottomSheetDialog.setContentView(picker.root)
        bottomSheetDialog.show()
        bottomSheetDialog.behavior.skipCollapsed = true
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    companion object {
        private const val PICKER_PAGE_SIZE = 30
    }

    private fun observeMessages() {
        viewModel.messages.observe(viewLifecycleOwner) { messageList ->
            adapter.submitList(messageList)
            if (messageList.isNotEmpty()) {
                binding.recyclerMessages.post {
                    binding.recyclerMessages.scrollToPosition(messageList.size - 1)
                }
            }
        }
    }

    private fun checkApiKey() {
        if (!app.hasAiApiKey()) {
            startActivity(Intent(requireContext(), AiSetupActivity::class.java))
        }
    }

    private fun observeLoading() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnStop.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSend.isEnabled = !isLoading
            binding.btnAttach.isEnabled = !isLoading
            binding.btnClear.isEnabled = !isLoading
            binding.btnSettings.isEnabled = !isLoading
        }
    }

    private fun sendMessage(userText: String) {
        viewModel.sendMessageToAI(userText, app.aiManager, selectedBook)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}