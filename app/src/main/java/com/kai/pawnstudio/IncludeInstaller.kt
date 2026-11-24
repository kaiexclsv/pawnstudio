package com.kai.pawnstudio

import android.content.Context
import java.io.File

object IncludeInstaller {

    fun install(context: Context) {
        val dest = File(context.filesDir, "workspace/include")
        val marker = File(dest, ".installed")
        if (marker.exists()) return
        copyAssets(context, "include", dest)
        marker.writeText("1")
    }

    private fun copyAssets(context: Context, path: String, dest: File) {
        val names = context.assets.list(path) ?: return
        dest.mkdirs()
        for (name in names) {
            val sub = "$path/$name"
            val children = context.assets.list(sub)
            if (children != null && children.isNotEmpty()) {
                copyAssets(context, sub, File(dest, name))
            } else {
                context.assets.open(sub).use { input ->
                    File(dest, name).outputStream().use { input.copyTo(it) }
                }
            }
        }
    }
}
