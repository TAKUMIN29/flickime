package com.example.flickime.dict

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.flickime.R

/** ユーザー辞書の一覧・追加・削除。 */
class UserDictActivity : AppCompatActivity() {

    private lateinit var dictionary: UserDictionary
    private lateinit var adapter: DictAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_dict)
        setTitle(R.string.dict_title)
        dictionary = UserDictionary.get(this)

        val reading = findViewById<EditText>(R.id.input_reading)
        val word = findViewById<EditText>(R.id.input_word)
        emptyView = findViewById(R.id.dict_empty)

        adapter = DictAdapter { entry ->
            dictionary.remove(entry)
            refresh()
        }
        findViewById<RecyclerView>(R.id.dict_list).apply {
            layoutManager = LinearLayoutManager(this@UserDictActivity)
            this.adapter = this@UserDictActivity.adapter
        }

        findViewById<Button>(R.id.btn_dict_add).setOnClickListener {
            val added = dictionary.add(reading.text.toString(), word.text.toString())
            if (added) {
                reading.text.clear()
                word.text.clear()
                refresh()
            } else {
                Toast.makeText(this, R.string.dict_add_failed, Toast.LENGTH_SHORT).show()
            }
        }

        refresh()
    }

    private fun refresh() {
        val entries = dictionary.all()
        adapter.submit(entries)
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private class DictAdapter(
        private val onDelete: (DictEntry) -> Unit,
    ) : RecyclerView.Adapter<DictAdapter.Holder>() {

        private var entries: List<DictEntry> = emptyList()

        fun submit(newEntries: List<DictEntry>) {
            entries = newEntries
            notifyDataSetChanged()
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val reading: TextView = view.findViewById(R.id.dict_reading)
            val word: TextView = view.findViewById(R.id.dict_word)
            val delete: TextView = view.findViewById(R.id.dict_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dict, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = entries[position]
            holder.reading.text = entry.reading
            holder.word.text = entry.word
            holder.delete.setOnClickListener { onDelete(entry) }
        }

        override fun getItemCount(): Int = entries.size
    }
}
