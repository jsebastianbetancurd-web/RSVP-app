package com.example.ui

import com.example.data.BookDao
import com.example.data.BookEntity
import com.example.data.BookRepository
import com.example.data.ChapterEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FakeBookDao : BookDao {
    override fun getAllBooksFlow(): Flow<List<BookEntity>> = flowOf(emptyList())
    override suspend fun getBookById(id: String): BookEntity? = null
    override suspend fun getChaptersByBookId(bookId: String): List<ChapterEntity> = emptyList()
    override suspend fun getChapter(bookId: String, chapterIndex: Int): ChapterEntity? = null
    override suspend fun insertBook(book: BookEntity) {}
    override suspend fun insertChapters(chapters: List<ChapterEntity>) {}
    override suspend fun updateProgress(bookId: String, chapterIndex: Int, wordIndex: Int) {}
    override suspend fun updateConfig(bookId: String, wpm: Int, chunkSize: Int) {}
    override suspend fun _deleteBookOnly(bookId: String) {}
    override suspend fun _deleteChaptersOnly(bookId: String) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    private val fakeDao = FakeBookDao()
    private val repository = BookRepository(fakeDao)
    private val viewModel = RsvpViewModel(repository)

    @Test
    fun testPunctuationExtraction() {
        // Simple punctuation
        assertEquals('.', viewModel.getEffectivePunctuation("Hello."))
        assertEquals(',', viewModel.getEffectivePunctuation("World,"))
        assertEquals('!', viewModel.getEffectivePunctuation("Whoa!"))
        
        // Punctuation inside quotation marks or parenthesis
        assertEquals('!', viewModel.getEffectivePunctuation("\"Indeed!\""))
        assertEquals(',', viewModel.getEffectivePunctuation("(therefore),"))
        assertEquals('.', viewModel.getEffectivePunctuation("concluding..."))
        
        // No punctuation
        assertNull(viewModel.getEffectivePunctuation("NormalWord"))
        assertNull(viewModel.getEffectivePunctuation("NoPunc123"))
    }

    @Test
    fun testWordLengthTimingCompensation() {
        // Base delay at 300 WPM with chunkSize=1 is: (60,000 / 300) * 1 = 200ms
        viewModel.changeWpm(300)
        viewModel.changeChunkSize(1)

        // Make sure settings correspond to active timing
        viewModel.togglePunctuationPause(false)
        viewModel.toggleWordLengthTiming(true)

        val shortWordDelay = viewModel.calculateDelayMs("a") // len <= 2 -> 0.82 multiplier -> 164ms
        val standardWordDelay = viewModel.calculateDelayMs("hello") // len 5..7 -> 1.0 multiplier -> 200ms
        val longWordDelay = viewModel.calculateDelayMs("revolutionary") // len >= 11 -> 1.45/1.65 multiplier -> >200ms

        assertTrue("Short words should read faster", shortWordDelay < standardWordDelay)
        assertTrue("Long words should take longer to digest", longWordDelay > standardWordDelay)
    }

    @Test
    fun testPunctuationPausesAndModifiers() {
        viewModel.changeWpm(300)
        viewModel.changeChunkSize(1)

        // Enable punctuation, disable length timing to isolate punctuation pauses
        viewModel.togglePunctuationPause(true)
        viewModel.toggleWordLengthTiming(false)
        viewModel.setSentencePauseMultiplier(2.5f)
        viewModel.setCommaPauseMultiplier(1.5f)

        val noPuncDelay = viewModel.calculateDelayMs("Normal") // 200ms
        val sentenceEndDelay = viewModel.calculateDelayMs("Sentence.") // 200 * 2.5 = 500ms
        val commaDelay = viewModel.calculateDelayMs("Clause,") // 200 * 1.5 = 300ms

        assertEquals(200L, noPuncDelay)
        assertEquals(500L, sentenceEndDelay)
        assertEquals(300L, commaDelay)
    }

    @Test
    fun testNumericRepresentationSlowdown() {
        viewModel.changeWpm(300)
        viewModel.changeChunkSize(1)
        
        // Isolate numerical slowdown
        viewModel.togglePunctuationPause(false)
        viewModel.toggleWordLengthTiming(false)

        val wordDelay = viewModel.calculateDelayMs("Standard") // 200ms
        val numberDelay = viewModel.calculateDelayMs("2026") // 200 * 1.25 = 250ms

        assertEquals(200L, wordDelay)
        assertEquals(250L, numberDelay)
    }
}
