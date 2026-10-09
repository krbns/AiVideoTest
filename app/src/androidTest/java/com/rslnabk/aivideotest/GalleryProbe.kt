package com.rslnabk.aivideotest

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** Independent verification on old Android; the app only needs WRITE, never broad READ access. */
internal object GalleryProbe {
    private fun shell(command: String): ByteArray = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).use { it.readBytes() }

    private fun mediaUri(uri: Uri): String {
        require(uri.toString().matches(Regex("content://media/external/(images|video)/media/[0-9]+")))
        return uri.toString()
    }
    fun read(context: Context, uri: Uri): ByteArray = if (Build.VERSION.SDK_INT <= 28)
        shell("content read --uri ${mediaUri(uri)}")
    else context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    fun exists(context: Context, uri: Uri): Boolean = if (Build.VERSION.SDK_INT <= 28)
        shell("content query --uri ${mediaUri(uri)} --projection _id").toString(Charsets.UTF_8).contains("Row:")
    else context.contentResolver.query(uri, arrayOf("_id"), null, null, null)!!.use { it.count > 0 }
}
