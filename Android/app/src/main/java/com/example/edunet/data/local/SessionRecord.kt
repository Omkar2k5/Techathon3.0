package com.example.edunet.data.local

data class SavedFile(
    val name: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val receivedAt: Long          // epoch ms — when this file arrived
)

data class SessionRecord(
    val subjectCode: String,
    val subjectName: String,
    val dateKey: String,          // "yyyy-MM-dd"  e.g. "2026-02-22"
    val files: List<SavedFile>
)
