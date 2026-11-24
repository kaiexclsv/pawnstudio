package com.kai.pawnstudio

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class ExplorerAdapter(
    private val onFileClick: (File) -> Unit,
    private val onLongPress: (File) -> Unit
) : RecyclerView.Adapter<ExplorerAdapter.VH>() {

    private class Row(val file: File, val depth: Int)

    private val rows = mutableListOf<Row>()
    private val expanded = mutableSetOf<String>()
    private lateinit var root: File
    var selected: File? = null

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val arrow: ImageView = v.findViewById(R.id.arrow)
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
    }

    fun load(root: File) {
        this.root = root
        rebuild()
    }

    fun rebuild() {
        rows.clear()
        addChildren(root, 0)
        notifyDataSetChanged()
    }

    private fun addChildren(dir: File, depth: Int) {
        val kids = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: return
        for (f in kids) {
            rows.add(Row(f, depth))
            if (f.isDirectory && f.path in expanded) addChildren(f, depth + 1)
        }
    }

    fun expand(dir: File) {
        expanded.add(dir.path)
        rebuild()
    }

    fun toggle(dir: File) {
        if (!expanded.remove(dir.path)) expanded.add(dir.path)
        rebuild()
    }

    fun collapseAll() {
        expanded.clear()
        rebuild()
    }

    fun select(file: File?) {
        selected = file
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_node, parent, false)
        return VH(v)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = rows[position]
        val f = row.file
        val d = h.itemView.resources.displayMetrics.density

        h.itemView.setPadding(((8 + row.depth * 14) * d).toInt(), 0, (8 * d).toInt(), 0)
        h.name.text = f.name

        if (f.isDirectory) {
            h.arrow.visibility = View.VISIBLE
            h.arrow.rotation = if (f.path in expanded) 90f else 0f
            h.icon.setImageResource(R.drawable.ic_folder)
        } else {
            h.arrow.visibility = View.INVISIBLE
            h.icon.setImageResource(R.drawable.ic_file)
        }
        ImageViewCompat.setImageTintList(h.icon, ColorStateList.valueOf(colorFor(f)))
        h.itemView.setBackgroundColor(if (f == selected) 0xFF04395E.toInt() else Color.TRANSPARENT)

        h.itemView.setOnClickListener {
            selected = f
            if (f.isDirectory) toggle(f) else { notifyDataSetChanged(); onFileClick(f) }
        }
        h.itemView.setOnLongClickListener { onLongPress(f); true }
    }

    private fun colorFor(f: File): Int {
        if (f.isDirectory) return when (f.name.lowercase()) {
            "gamemodes" -> 0xFFA074C4.toInt()
            "filterscripts", "include" -> 0xFF42A5F5.toInt()
            "plugins" -> 0xFF66BB6A.toInt()
            "scriptfiles" -> 0xFFFFA726.toInt()
            "npcmodes" -> 0xFFF06292.toInt()
            ".git" -> 0xFFF4511E.toInt()
            else -> 0xFFDCB67A.toInt()
        }
        return when (f.extension.lowercase()) {
            "pwn", "inc" -> 0xFFA074C4.toInt()
            "json" -> 0xFFF5C518.toInt()
            "sql" -> 0xFF42A5F5.toInt()
            else -> 0xFF8A9BA8.toInt()
        }
    }
}
