package com.rslnabk.aivideotest.model

import com.rslnabk.aivideotest.data.demo.DemoRules

enum class PhotoStatus { ABSENT, LOADING, READY, FAILED }
enum class PhotoStyle { NONE, GHIBLI, PERSON_3D, SIMPSONS, FANTASY }
enum class JobStatus { RUNNING, SUCCEEDED, FAILED }
data class GenerationDraft(
    val key: String, val kind: MediaKind, val effectId: String? = null,
    val prompt: String = "", val resolution: Int = 720, val style: PhotoStyle = PhotoStyle.NONE,
    val photo: String? = null, val photoStatus: PhotoStatus = PhotoStatus.ABSENT,
    val pendingPhoto: String? = null, val activeJobId: String? = null
) {
    val characterCount get() = prompt.codePointCount(0, prompt.length)
    val isValid get() = characterCount <= DemoRules.PROMPT_LIMIT &&
        (effectId != null && photo != null || effectId == null && prompt.isNotBlank()) &&
        photoStatus != PhotoStatus.LOADING && photoStatus != PhotoStatus.FAILED
}
data class GenerationJob(
    val id: String, val draft: GenerationDraft, val tokenCost: Int, val resultImage: Int,
    val createdAt: Long, val readyAt: Long, val willFail: Boolean = false,
    val status: JobStatus = JobStatus.RUNNING
)
sealed class SubmitResult {
    data class Accepted(val jobId: String) : SubmitResult()
    data class InsufficientBalance(val required: Int) : SubmitResult()
    data object InvalidDraft : SubmitResult()
}
