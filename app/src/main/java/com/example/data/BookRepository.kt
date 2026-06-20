package com.example.data

import kotlinx.coroutines.flow.Flow

class BookRepository(private val bookDao: BookDao) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooksFlow()

    suspend fun getBookById(id: String): BookEntity? {
        return bookDao.getBookById(id)
    }

    suspend fun getChaptersByBookId(bookId: String): List<ChapterEntity> {
        return bookDao.getChaptersByBookId(bookId)
    }

    suspend fun getChapter(bookId: String, chapterIndex: Int): ChapterEntity? {
        return bookDao.getChapter(bookId, chapterIndex)
    }

    suspend fun insertBookWithChapters(book: BookEntity, chapters: List<ChapterEntity>) {
        bookDao.insertBook(book)
        bookDao.insertChapters(chapters)
    }

    suspend fun updateProgress(bookId: String, chapterIndex: Int, wordIndex: Int) {
        bookDao.updateProgress(bookId, chapterIndex, wordIndex)
    }

    suspend fun updateConfig(bookId: String, wpm: Int, chunkSize: Int) {
        bookDao.updateConfig(bookId, wpm, chunkSize)
    }

    suspend fun deleteBook(bookId: String) {
        bookDao.deleteBookWithChapters(bookId)
    }
}
