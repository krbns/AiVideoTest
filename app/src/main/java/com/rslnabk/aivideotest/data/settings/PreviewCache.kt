package com.rslnabk.aivideotest.data.settings

import java.io.File

/** Only regenerable previews belong here. Reference photos, camera and shared payloads are excluded. */
class PreviewCache(private val cacheRoot: File, private val directories: List<String> = listOf("previews")) {
    init { require(directories.all { it in setOf("previews", "backend_read") }) }
    private fun files(): Sequence<File> = directories.asSequence().flatMap { directory ->
        val root = File(cacheRoot.canonicalFile, directory)
        // Canonical equality rejects directory links, including links to protected exports.
        if (!root.exists() || root.canonicalFile != root.absoluteFile) return@flatMap emptySequence()
        root.walkTopDown().onFail { _, failure -> throw failure }.onEnter { it.canonicalFile == it.absoluteFile }
            .filter { it != root && it.canonicalFile == it.absoluteFile }
    }
    fun bytes() = files().filter(File::isFile).sumOf(File::length)
    fun clear() {
        // Validate traversal before deletion, so a symlink never leads into durable app storage.
        files().toList().sortedByDescending { it.path.length }.forEach { check(it.delete() || !it.exists()) { "Could not clear preview" } }
    }
}
