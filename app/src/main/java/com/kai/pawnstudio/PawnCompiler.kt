package com.kai.pawnstudio

import android.content.Context
import java.io.File

object PawnCompiler {

    fun compile(context: Context, source: File): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val pawncc = File(nativeDir, "libpawncc.so")
        if (!pawncc.exists()) return "Error: libpawncc.so not found"

        val includeDir = File(context.filesDir, "workspace/include").apply { mkdirs() }
        val outFile = File(source.parentFile, source.nameWithoutExtension + ".amx")

        val cmd = listOf(
            pawncc.absolutePath,
            source.absolutePath,
            "-o${outFile.absolutePath}",
            "-i${includeDir.absolutePath}",
            "-;+", "-(+", "-d3"
        )

        return try {
            val pb = ProcessBuilder(cmd)
                .directory(source.parentFile)
                .redirectErrorStream(true)
            pb.environment()["LD_LIBRARY_PATH"] = nativeDir

            val p = pb.start()
            val text = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            text + if (code == 0) "\nDone: ${outFile.name}" else "\nFailed (exit $code)"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
