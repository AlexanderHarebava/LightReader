package org.readium.r2.testapp.ai

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.readium.r2.testapp.R

class ChatHistoryAdapter(
    private val onChatClick: (ChatSession) -> Unit,
    private val onRenameClick: (ChatSession) -> Unit,
    private val onDeleteClick: (ChatSession) -> Unit,
    private val onExportClick: (ChatSession) -> Unit,
    private val onShareClick: (ChatSession) -> Unit,
) : ListAdapter<ChatSession, ChatHistoryAdapter.ChatViewHolder>(ChatDiff) {

    private var activeChatId: String? = null

    fun setActiveChat(id: String?) {
        if (activeChatId == id) return
        activeChatId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_history, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvChatItemTitle)
        private val meta: TextView = itemView.findViewById(R.id.tvChatItemMeta)
        private val menuBtn: ImageView = itemView.findViewById(R.id.btnChatItemMenu)

        fun bind(chat: ChatSession) {
            val context = itemView.context
            title.text = chat.title
            meta.text = context.getString(
                R.string.chat_item_meta,
                formatDate(chat.updatedAt),
                chat.messages.size
            )

            // Подсветка активного чата
            val isActive = chat.id == activeChatId
            itemView.setBackgroundColor(
                if (isActive) ContextCompat.getColor(context, R.color.md_theme_primaryContainer)
                else Color.TRANSPARENT
            )
            title.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isActive) R.color.md_theme_onPrimaryContainer else R.color.textColorPrimary
                )
            )

            itemView.setOnClickListener { onChatClick(chat) }
            menuBtn.setOnClickListener { anchor ->
                val popup = PopupMenu(context, anchor)
                popup.menuInflater.inflate(R.menu.menu_chat_item, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_rename_chat -> { onRenameClick(chat); true }
                        R.id.action_download_chat -> { onExportClick(chat); true }
                        R.id.action_share_chat -> { onShareClick(chat); true }
                        R.id.action_delete_chat -> { onDeleteClick(chat); true }
                        else -> false
                    }
                }
                popup.show()
            }
        }

        private fun formatDate(timestamp: Long): String {
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = timestamp }
            return when {
                now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) ->
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                isYesterday(now, then) ->
                    itemView.context.getString(R.string.chat_date_yesterday)
                else ->
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }

        private fun isYesterday(now: Calendar, then: Calendar): Boolean {
            val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            return yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        }
    }

    private object ChatDiff : DiffUtil.ItemCallback<ChatSession>() {
        override fun areItemsTheSame(oldItem: ChatSession, newItem: ChatSession) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatSession, newItem: ChatSession) =
            oldItem.id == newItem.id &&
                oldItem.title == newItem.title &&
                oldItem.updatedAt == newItem.updatedAt &&
                oldItem.messages.size == newItem.messages.size
    }
}