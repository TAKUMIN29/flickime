package com.example.flickime.clip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.flickime.R

/** クリップボード履歴の一覧。タップで貼り付け、☆で固定、✕で削除。 */
class ClipAdapter(
    private val onPaste: (ClipItem) -> Unit,
    private val onTogglePin: (ClipItem) -> Unit,
    private val onDelete: (ClipItem) -> Unit,
) : RecyclerView.Adapter<ClipAdapter.Holder>() {

    private var items: List<ClipItem> = emptyList()

    fun submit(newItems: List<ClipItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.clip_text)
        val pin: TextView = view.findViewById(R.id.clip_pin)
        val delete: TextView = view.findViewById(R.id.clip_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clip, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        // 履歴一覧では改行を可視化して1〜2行に収める
        holder.text.text = item.text.replace("\n", " ⏎ ")
        holder.pin.text = if (item.pinned) "★" else "☆"
        holder.itemView.setOnClickListener { onPaste(item) }
        holder.pin.setOnClickListener { onTogglePin(item) }
        holder.delete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size
}
