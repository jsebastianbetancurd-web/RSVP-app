package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val addedTime: Long = System.currentTimeMillis(),
    val currentChapterIndex: Int = 0,
    val currentWordIndex: Int = 0,
    val currentWpm: Int = 250,
    val currentChunkSize: Int = 1
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["bookId", "index"]
)
data class ChapterEntity(
    val bookId: String,
    val index: Int, // 0-indexed
    val title: String,
    val text: String
)
