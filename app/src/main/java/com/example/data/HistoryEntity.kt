package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_table")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalUri: String,
    val enhancedUri: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val label: String,
    val actualWidth: Int = 0,
    val actualHeight: Int = 0,
    val mimeType: String = "image/png",
    val scale: Int = 4,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0
)
