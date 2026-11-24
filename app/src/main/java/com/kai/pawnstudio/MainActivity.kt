package com.kai.pawnstudio

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var editor: CodeEditor
    private lateinit var output: TextView
    private lateinit var tabs: TabLayout
    private lateinit var drawer: DrawerLayout
    private lateinit var openEditors: LinearLayout
    private lateinit var openHeader: TextView
    private lateinit var adapter: ExplorerAdapter
    private lateinit var root: File

    private val openFiles = mutableListOf<File>()
    private val buffers = mutableMapOf<File, String>()
    private var current: File? = null
    private var ignoreTabEvents = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editor = findViewById(R.id.editor)
        output = findViewById(R.id.output)
        tabs = findViewById(R.id.tabs)
        drawer = findViewById(R.id.drawer)
        openEditors = findViewById(R.id.openEditors)
        openHeader = findViewById(R.id.openHeader)

        root = File(filesDir, "workspace").apply { mkdirs() }
        File(root, "filterscripts").mkdirs()
        IncludeInstaller.install(this)

        val main = File(root, "gamemodes/main.pwn")
        if (!main.exists()) {
            main.parentFile?.mkdirs()
            main.writeText("#include <open.mp>\n\nmain()\n{\n    print(\"Hello Pawn!\");\n}\n")
        }

        setupEditor()
        setupToolbar()
        setupTabs()
        setupExplorer()
        adapter.expand(File(root, "gamemodes"))
        openFile(main)
    }

    // ---------- Editor ----------
    private fun setupEditor() {
        FileProviderRegistry.getInstance()
            .addFileProvider(AssetsFileResolver(applicationContext.assets))

        val themeStream = FileProviderRegistry.getInstance()
            .tryGetInputStream("textmate/theme.json")
        ThemeRegistry.getInstance().loadTheme(
            ThemeModel(IThemeSource.fromInputStream(themeStream, "theme.json", null), "pawn_dark")
        )
        ThemeRegistry.getInstance().setTheme("pawn_dark")
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        editor.setEditorLanguage(TextMateLanguage.create("source.pawn", false))
        editor.setTextSize(14f)
        editor.typefaceText = android.graphics.Typeface.MONOSPACE
    }

    // ---------- Toolbar ----------
    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size)
        toolbar.setNavigationOnClickListener { drawer.openDrawer(GravityCompat.START) }
        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_save -> { save(); true }
                R.id.action_run -> { save(); compile(); true }
                R.id.action_new -> { createDialog(targetDir(), false); true }
                R.id.action_close -> { current?.let { f -> closeTab(f) }; true }
                else -> false
            }
        }
    }

    // ---------- Tabs ----------
    private fun setupTabs() {
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (ignoreTabEvents) return
                (tab.tag as? File)?.let {
                    showFile(it)
                    adapter.select(it)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun isBinary(f: File): Boolean {
        f.inputStream().use { s ->
            val buf = ByteArray(4096)
            val n = s.read(buf)
            for (i in 0 until n) if (buf[i] == 0.toByte()) return true
        }
        return false
    }

    private fun openFile(file: File) {
        if (file.length() > 2_000_000 || isBinary(file)) {
            Toast.makeText(this, "Can't open ${file.name} (binary or too large)", Toast.LENGTH_SHORT).show()
            return
        }
        val idx = openFiles.indexOf(file)
        if (idx == -1) {
            openFiles.add(file)
            ignoreTabEvents = true
            tabs.addTab(tabs.newTab().setText(file.name).setTag(file), true)
            ignoreTabEvents = false
            showFile(file)
        } else {
            tabs.getTabAt(idx)?.select()
        }
        adapter.select(file)
    }

    private fun showFile(file: File) {
        if (current == file) return
        current?.let { buffers[it] = editor.text.toString() }
        current = file
        editor.setText(buffers[file] ?: file.readText())
        refreshOpenEditors()
    }

    private fun closeTab(file: File, save: Boolean = true) {
        val idx = openFiles.indexOf(file)
        if (idx == -1) return

        if (save) {
            if (file == current) file.writeText(editor.text.toString())
            else buffers[file]?.let { file.writeText(it) }
        }
        openFiles.removeAt(idx)
        buffers.remove(file)

        ignoreTabEvents = true
        tabs.removeTabAt(idx)
        ignoreTabEvents = false

        if (file == current) {
            current = null
            if (openFiles.isEmpty()) {
                editor.setText("")
            } else {
                val next = minOf(idx, openFiles.size - 1)
                ignoreTabEvents = true
                tabs.getTabAt(next)?.select()
                ignoreTabEvents = false
                showFile(openFiles[next])
            }
        }
        refreshOpenEditors()
    }

    private fun closeTabsUnder(target: File, save: Boolean) {
        openFiles
            .filter { it.path == target.path || it.path.startsWith(target.path + File.separator) }
            .forEach { closeTab(it, save) }
    }

    // ---------- Open editors (drawer) ----------
    private fun refreshOpenEditors() {
        openEditors.removeAllViews()
        for (f in openFiles) {
            val row = layoutInflater.inflate(R.layout.item_open_editor, openEditors, false)
            row.findViewById<TextView>(R.id.oeName).text = f.name
            row.setBackgroundColor(if (f == current) 0xFF37373D.toInt() else 0)
            row.findViewById<View>(R.id.oeClose).setOnClickListener { closeTab(f) }
            row.setOnClickListener {
                openFile(f)
                drawer.closeDrawer(GravityCompat.START)
            }
            openEditors.addView(row)
        }
        openHeader.visibility = if (openFiles.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---------- Explorer ----------
    private fun setupExplorer() {
        adapter = ExplorerAdapter(
            onFileClick = { f ->
                openFile(f)
                drawer.closeDrawer(GravityCompat.START)
            },
            onLongPress = { f -> showItemMenu(f) }
        )
        adapter.load(root)

        val tree = findViewById<RecyclerView>(R.id.tree)
        tree.layoutManager = LinearLayoutManager(this)
        tree.adapter = adapter

        findViewById<View>(R.id.btnNewFile).setOnClickListener { createDialog(targetDir(), false) }
        findViewById<View>(R.id.btnNewFolder).setOnClickListener { createDialog(targetDir(), true) }
        findViewById<View>(R.id.btnRefresh).setOnClickListener { adapter.rebuild() }
        findViewById<View>(R.id.btnCollapse).setOnClickListener { adapter.collapseAll() }
    }

    private fun targetDir(): File {
        val s = adapter.selected ?: return root
        return if (s.isDirectory) s else (s.parentFile ?: root)
    }

    private fun createDialog(parent: File, isDir: Boolean) {
        val input = EditText(this).apply {
            hint = if (isDir) "folder name" else "filename.pwn"
        }
        AlertDialog.Builder(this)
            .setTitle(if (isDir) "New folder" else "New file")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                var name = input.text.toString().trim()
                if (name.isEmpty() || name.contains("/")) return@setPositiveButton
                if (!isDir && !name.contains(".")) name += ".pwn"
                val f = File(parent, name)
                if (f.exists()) {
                    Toast.makeText(this, "Already exists", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (isDir) f.mkdirs() else f.writeText("")
                adapter.expand(parent)
                if (!isDir) openFile(f)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showItemMenu(f: File) {
        val items = if (f.isDirectory)
            arrayOf("New file", "New folder", "Rename", "Delete")
        else
            arrayOf("Rename", "Delete")

        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(items) { _, which ->
                when (items[which]) {
                    "New file" -> createDialog(f, false)
                    "New folder" -> createDialog(f, true)
                    "Rename" -> renameDialog(f)
                    "Delete" -> confirmDelete(f)
                }
            }
            .show()
    }

    private fun renameDialog(f: File) {
        val input = EditText(this).apply { setText(f.name) }
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty() || name.contains("/")) return@setPositiveButton
                val target = File(f.parentFile, name)
                if (target.exists()) {
                    Toast.makeText(this, "Already exists", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val wasOpen = f.isFile && openFiles.contains(f)
                closeTabsUnder(f, save = true)
                f.renameTo(target)
                adapter.rebuild()
                if (wasOpen) openFile(target)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${f.name}?")
            .setMessage(
                if (f.isDirectory) "This folder and everything inside it will be deleted."
                else "This can't be undone."
            )
            .setPositiveButton("Delete") { _, _ ->
                closeTabsUnder(f, save = false)
                f.deleteRecursively()
                if (adapter.selected?.path?.startsWith(f.path) == true) adapter.selected = null
                adapter.rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Save + Compile ----------
    private fun save() {
        val file = current ?: return
        file.writeText(editor.text.toString())
        buffers[file] = editor.text.toString()
        Toast.makeText(this, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
    }

    private fun compile() {
        val file = current ?: return
        output.text = "Compiling ${file.name}..."
        Thread {
            val result = PawnCompiler.compile(this, file)
            runOnUiThread { output.text = result }
        }.start()
    }
}
