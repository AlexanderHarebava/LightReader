package org.readium.r2.testapp.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import org.readium.r2.testapp.R
import org.readium.r2.testapp.utils.CoverLoader

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<Message>()

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.textMessageBody)
        val copyButton: ImageView? = itemView.findViewById(R.id.btnCopy)

        val bookCard: View? = itemView.findViewById(R.id.bookAttachmentCard)
        val coverImage: ImageView? = itemView.findViewById(R.id.ivBookCover)
        val coverTitle: TextView? = itemView.findViewById(R.id.tvBookTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = if (viewType == 1) {
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_user, parent, false)
        } else {
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_ai, parent, false)
        }

        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        holder.textView.text = message.text

        val hasAttachment = !message.bookTitle.isNullOrBlank() ||
            !message.bookCoverPath.isNullOrBlank()

        if (hasAttachment) {
            holder.bookCard?.visibility = View.VISIBLE
            holder.coverTitle?.text = message.bookTitle.orEmpty()

            holder.coverImage?.let { imageView ->
                CoverLoader.load(
                    message.bookCoverPath,
                    message.bookTitle,
                    imageView
                )
            }
        } else {
            holder.bookCard?.visibility = View.GONE
            holder.coverImage?.setImageBitmap(null)
            holder.coverTitle?.text = ""
        }

        holder.copyButton?.setOnClickListener { view ->
            val context = view.context
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(
                context.getString(R.string.Ai_messagecopied),
                message.text
            )
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, R.string.Ai_messagecopied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isFromUser) 1 else 0
    }

    fun submitList(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }
}