package com.example.flickime.mozc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.flickime.R

/**
 * 変換候補1件。
 *
 * [id] は Mozc の候補 id。誤フリックの校正候補やユーザー辞書の語は Mozc の候補ではないので
 * [LOCAL_ID] を入れ、確定方法（候補選択か、文字列の直接確定か）を呼び出し側で切り替える。
 */
data class CandidateItem(val text: String, val id: Int) {
    val isLocal: Boolean get() = id == LOCAL_ID

    companion object {
        const val LOCAL_ID = -1
    }
}

/** 候補一覧を展開表示したときのグリッド。 */
class CandidateAdapter(
    private val onSelect: (CandidateItem) -> Unit,
) : RecyclerView.Adapter<CandidateAdapter.Holder>() {

    private var items: List<CandidateItem> = emptyList()

    fun submit(newItems: List<CandidateItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.candidate_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_candidate, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.text.text = item.text
        val color = if (item.isLocal) R.color.ime_accent else R.color.ime_text
        holder.text.setTextColor(ContextCompat.getColor(holder.text.context, color))
        holder.itemView.setOnClickListener { onSelect(item) }
    }

    override fun getItemCount(): Int = items.size
}
