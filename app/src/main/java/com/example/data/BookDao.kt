package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedTime DESC")
    fun getAllBooksFlow(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): BookEntity?

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY `index` ASC")
    suspend fun getChaptersByBookId(bookId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND `index` = :chapterIndex LIMIT 1")
    suspend fun getChapter(bookId: String, chapterIndex: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Query("UPDATE books SET currentChapterIndex = :chapterIndex, currentWordIndex = :wordIndex WHERE id = :bookId")
    suspend fun updateProgress(bookId: String, chapterIndex: Int, wordIndex: Int)

    @Query("UPDATE books SET currentWpm = :wpm, currentChunkSize = :chunkSize WHERE id = :bookId")
    suspend fun updateConfig(bookId: String, wpm: Int, chunkSize: Int)

    @Transaction
    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun _deleteBookOnly(bookId: String)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun _deleteChaptersOnly(bookId: String)

    @Transaction
    suspend fun deleteBookWithChapters(bookId: String) {
        _deleteChaptersOnly(bookId)
        _deleteBookOnly(bookId)
    }
}
