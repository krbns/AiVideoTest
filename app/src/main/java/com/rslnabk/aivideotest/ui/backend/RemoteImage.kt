package com.rslnabk.aivideotest.ui.backend

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.backend.readBytesLimited
import com.rslnabk.aivideotest.ui.common.DsIcon
import com.rslnabk.aivideotest.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private object CoverImages {
    private val permits = Semaphore(3)
    private val images = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    suspend fun load(value: String): Bitmap? {
        images.get(value)?.let { return it }
        return permits.withPermit { withContext(Dispatchers.IO) {
            runCatching {
                var url = URL(value)
                var bytes: ByteArray? = null
                for (attempt in 0..3) {
                    require(url.protocol == "https")
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 10000; connection.readTimeout = 10000
                        connection.instanceFollowRedirects = false
                        // Public covers do not receive the API bearer token.
                        if (connection.responseCode in 300..399) url = URL(url, connection.getHeaderField("Location") ?: error("No location"))
                        else {
                            require(connection.responseCode == 200)
                            bytes = connection.inputStream.use { it.readBytesLimited(5 * 1024 * 1024) }; break
                        }
                    } finally { connection.disconnect() }
                }
                val input = requireNotNull(bytes)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
                require(bounds.outWidth in 1..10000 && bounds.outHeight in 1..10000)
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                while (bounds.outWidth / options.inSampleSize > 960 || bounds.outHeight / options.inSampleSize > 1600) options.inSampleSize *= 2
                BitmapFactory.decodeByteArray(input, 0, input.size, options)?.also { images.put(value, it) }
            }.getOrNull()
        } }
    }
}
@Composable fun RemoteImage(url: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, url) { value = null; if (url != null) value = CoverImages.load(url) }
    Box(modifier.background(Ds.colors.backgroundSecondary), contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current != null) Image(current.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else DsIcon(R.drawable.ic_photo, null, Ds.colors.labelTertiary)
    }
}
