package com.rslnabk.aivideotest

import com.rslnabk.aivideotest.data.settings.PreviewCache
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PreviewCacheTest {
    @Test fun `clearing previews preserves exports camera and durable references`() {
        val root = Files.createTempDirectory("preview-test").toFile().canonicalFile
        try {
            val cache = File(root, "cache").apply { mkdirs() }
            fun file(path: String) = File(root, path).apply { parentFile!!.mkdirs(); writeText("keep") }
            val preview = file("cache/previews/sub/a"); val export = file("cache/exports/a"); val camera = file("cache/camera/a"); val photo = file("files/reference_photos/a")
            val c = PreviewCache(cache); assertEquals(4L, c.bytes()); c.clear(); assertEquals(0L, c.bytes())
            assertFalse(preview.exists()); listOf(export, camera, photo).forEach { assertEquals("keep", it.readText()) }
        } finally { root.deleteRecursively() }
    }
    @Test fun `root and nested symlinks never clear protected shared payloads`() {
        val root = Files.createTempDirectory("preview-link").toFile().canonicalFile
        try {
            val protected = File(root, "exports").apply { mkdirs() }; val payload = File(protected, "payload").apply { writeText("keep") }
            val preview = File(root, "previews")
            Files.createSymbolicLink(preview.toPath(), protected.toPath())
            val cache = PreviewCache(root); assertEquals(0L, cache.bytes()); cache.clear(); assertTrue(payload.exists())
            Files.delete(preview.toPath()); preview.mkdirs()
            Files.createSymbolicLink(File(preview, "linked").toPath(), protected.toPath())
            Files.createSymbolicLink(File(preview, "cycle").toPath(), preview.toPath())
            assertEquals(0L, cache.bytes()); cache.clear(); assertEquals("keep", payload.readText())
        } finally { root.deleteRecursively() }
    }
}
