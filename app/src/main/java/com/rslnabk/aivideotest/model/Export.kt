package com.rslnabk.aivideotest.model

import android.net.Uri

enum class ExportDestination { GALLERY, FILES, SHARE }
enum class ExportPhase { CHOOSING, PERMISSION, WRITING, SHARE_READY, SUCCEEDED, FAILED, CANCELLED }
data class ExportState(
    val id: String, val jobId: String, val destination: ExportDestination,
    val phase: ExportPhase, val uri: Uri? = null, val message: Int? = null
) {
    val busy get() = phase in listOf(ExportPhase.CHOOSING, ExportPhase.PERMISSION, ExportPhase.WRITING, ExportPhase.SHARE_READY)
}
