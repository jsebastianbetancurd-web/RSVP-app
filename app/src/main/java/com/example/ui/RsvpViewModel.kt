package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BookEntity
import com.example.data.BookRepository
import com.example.data.ChapterEntity
import com.example.data.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

class RsvpViewModel(private val repository: BookRepository) : ViewModel() {

    // List of books in database
    val booksState: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current State
    private val _currentBook = MutableStateFlow<BookEntity?>(null)
    val currentBook: StateFlow<BookEntity?> = _currentBook.asStateFlow()

    private val _chapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val chapters: StateFlow<List<ChapterEntity>> = _chapters.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _chunks = MutableStateFlow<List<String>>(emptyList())
    val chunks: StateFlow<List<String>> = _chunks.asStateFlow()

    private val _currentChunkIndex = MutableStateFlow(0)
    val currentChunkIndex: StateFlow<Int> = _currentChunkIndex.asStateFlow()

    private val _wpm = MutableStateFlow(250)
    val wpm: StateFlow<Int> = _wpm.asStateFlow()

    private val _chunkSize = MutableStateFlow(1)
    val chunkSize: StateFlow<Int> = _chunkSize.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _punctuationPauseEnabled = MutableStateFlow(true)
    val punctuationPauseEnabled: StateFlow<Boolean> = _punctuationPauseEnabled.asStateFlow()

    private val _wordLengthTimingEnabled = MutableStateFlow(true)
    val wordLengthTimingEnabled: StateFlow<Boolean> = _wordLengthTimingEnabled.asStateFlow()

    private val _sentencePauseMultiplier = MutableStateFlow(2.0f)
    val sentencePauseMultiplier: StateFlow<Float> = _sentencePauseMultiplier.asStateFlow()

    private val _commaPauseMultiplier = MutableStateFlow(1.4f)
    val commaPauseMultiplier: StateFlow<Float> = _commaPauseMultiplier.asStateFlow()

    private var rsvpJob: Job? = null
    private var dBSaveJob: Job? = null

    init {
        startRsvpTimer()
        startPeriodicProgressSaver()
    }

    // Load a book from database
    fun selectBook(book: BookEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _isPlaying.value = false
            
            try {
                val dbChapters = repository.getChaptersByBookId(book.id)
                if (dbChapters.isNotEmpty()) {
                    _currentBook.value = book
                    _chapters.value = dbChapters
                    _currentChapterIndex.value = book.currentChapterIndex.coerceIn(0, dbChapters.lastIndex)
                    _wpm.value = book.currentWpm.coerceIn(100, 800)
                    _chunkSize.value = book.currentChunkSize.coerceIn(1, 3)
                    
                    recomputeChunks(dbChapters[_currentChapterIndex.value].text, book.currentWordIndex)
                } else {
                    _errorMessage.value = "This book has no chapters in database."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load book: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Close current book and return to book list
    fun closeBook() {
        viewModelScope.launch {
            _isPlaying.value = false
            saveProgressToDb(instant = true)
            _currentBook.value = null
            _chapters.value = emptyList()
            _chunks.value = emptyList()
            _currentChunkIndex.value = 0
            _currentChapterIndex.value = 0
        }
    }

    // Load EPUB file picked from SAF
    fun importEpub(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _isPlaying.value = false

            try {
                val parser = EpubParser()
                val parsedBook = withContext(Dispatchers.IO) {
                    val stream: InputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Could not open EPUB file stream.")
                    parser.parseEpub(stream)
                }

                // Create Entities
                val bookId = UUID.randomUUID().toString()
                val bookEntity = BookEntity(
                    id = bookId,
                    title = parsedBook.title,
                    author = parsedBook.author,
                    currentChapterIndex = 0,
                    currentWordIndex = 0,
                    currentWpm = 250,
                    currentChunkSize = 1
                )

                val chapterEntities = parsedBook.chapters.map {
                    ChapterEntity(
                        bookId = bookId,
                        index = it.index,
                        title = it.title,
                        text = it.text
                    )
                }

                repository.insertBookWithChapters(bookEntity, chapterEntities)
                selectBook(bookEntity)

            } catch (e: Exception) {
                Log.e("RsvpViewModel", "Error importing EPUB", e)
                _errorMessage.value = "Error importing EPUB: ${e.localizedMessage ?: "Unknown Parse Error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            if (_currentBook.value?.id == bookId) {
                closeBook()
            }
            repository.deleteBook(bookId)
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
        if (!_isPlaying.value) {
            viewModelScope.launch {
                saveProgressToDb(instant = true)
            }
        }
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
        if (!playing) {
            viewModelScope.launch {
                saveProgressToDb(instant = true)
            }
        }
    }

    fun changeWpm(newWpm: Int) {
        _wpm.value = newWpm.coerceIn(100, 800)
    }

    fun changeChunkSize(newSize: Int) {
        val oldSize = _chunkSize.value
        if (oldSize == newSize) return
        
        val oldChunkIndex = _currentChunkIndex.value
        val absoluteWordIndex = oldChunkIndex * oldSize
        
        _chunkSize.value = newSize.coerceIn(1, 3)
        
        // Recompute chunks and remap chunk index based on word index
        _chapters.value.getOrNull(_currentChapterIndex.value)?.let { chapter ->
            recomputeChunks(chapter.text, absoluteWordIndex)
        }
    }

    fun togglePunctuationPause(enabled: Boolean) {
        _punctuationPauseEnabled.value = enabled
    }

    fun toggleWordLengthTiming(enabled: Boolean) {
        _wordLengthTimingEnabled.value = enabled
    }

    fun setSentencePauseMultiplier(multiplier: Float) {
        _sentencePauseMultiplier.value = multiplier.coerceIn(1.0f, 3.0f)
    }

    fun setCommaPauseMultiplier(multiplier: Float) {
        _commaPauseMultiplier.value = multiplier.coerceIn(1.0f, 2.0f)
    }

    fun selectChapter(index: Int) {
        val chs = _chapters.value
        if (index in chs.indices) {
            _isPlaying.value = false
            _currentChapterIndex.value = index
            recomputeChunks(chs[index].text, 0)
            viewModelScope.launch {
                saveProgressToDb(instant = true)
            }
        }
    }

    fun rewindWords(count: Int = 10) {
        val size = _chunkSize.value
        val currentIdx = _currentChunkIndex.value
        val absoluteWordIdx = currentIdx * size
        val targetWordIdx = (absoluteWordIdx - count).coerceIn(0, getMaxWordCount())
        
        _currentChunkIndex.value = targetWordIdx / size
        viewModelScope.launch {
            saveProgressToDb(instant = true)
        }
    }

    fun fastForwardWords(count: Int = 10) {
        val size = _chunkSize.value
        val currentIdx = _currentChunkIndex.value
        val absoluteWordIdx = currentIdx * size
        val targetWordIdx = (absoluteWordIdx + count).coerceIn(0, getMaxWordCount())
        
        _currentChunkIndex.value = targetWordIdx / size
        viewModelScope.launch {
            saveProgressToDb(instant = true)
        }
    }

    fun seekToPercentage(percentage: Float) {
        val totalChunks = _chunks.value.size
        if (totalChunks > 0) {
            val targetChunk = (percentage * totalChunks).toInt().coerceIn(0, totalChunks - 1)
            _currentChunkIndex.value = targetChunk
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun recomputeChunks(text: String, startWordIndex: Int = 0) {
        val size = _chunkSize.value
        val list = mutableListOf<String>()
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        
        var i = 0
        while (i < words.size) {
            val chunkWords = words.subList(i, minOf(i + size, words.size))
            list.add(chunkWords.joinToString(" "))
            i += size
        }

        _chunks.value = list
        val targetChunkIdx = (startWordIndex / size).coerceIn(0, list.lastIndex.coerceAtLeast(0))
        _currentChunkIndex.value = targetChunkIdx
    }

    private fun getMaxWordCount(): Int {
        val chapterText = _chapters.value.getOrNull(_currentChapterIndex.value)?.text ?: ""
        return chapterText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }

    // Core Speed Reading Ticker Loop
    private fun startRsvpTimer() {
        rsvpJob?.cancel()
        rsvpJob = viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    val chunkList = _chunks.value
                    val currentIdx = _currentChunkIndex.value
                    
                    if (currentIdx in chunkList.indices) {
                        val currentChunk = chunkList[currentIdx]
                        val delayMs = calculateDelayMs(currentChunk)
                        
                        delay(delayMs)
                        
                        if (_isPlaying.value) { // Re-check if paused during delay
                            if (currentIdx < chunkList.lastIndex) {
                                _currentChunkIndex.value = currentIdx + 1
                            } else {
                                // Finished chapter
                                _isPlaying.value = false
                                saveProgressToDb(instant = true)
                            }
                        }
                    } else {
                        _isPlaying.value = false
                        delay(50)
                    }
                } else {
                    delay(50)
                }
            }
        }
    }

    internal fun calculateDelayMs(chunk: String): Long {
        val w = _wpm.value.toDouble()
        val c = _chunkSize.value.toDouble()
        
        // Base delay for c words at w Words Per Minute
        val baseDelay = (60000.0 / w) * c
        
        if (chunk.isEmpty()) return baseDelay.toLong()
        
        // Split chunk into words to analyze timing features of each individual word
        val words = chunk.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return baseDelay.toLong()
        
        var cumulativeMultiplier = 0.0
        
        for (word in words) {
            var wordMultiplier = 1.0
            
            // 1. Punctuation sequence pause logic
            if (_punctuationPauseEnabled.value) {
                val punc = getEffectivePunctuation(word)
                if (punc != null) {
                    val puncMultiplier = when (punc) {
                        '.', '?', '!' -> _sentencePauseMultiplier.value.toDouble()
                        ',', ';', ':', '-', '—' -> _commaPauseMultiplier.value.toDouble()
                        else -> 1.0
                    }
                    wordMultiplier *= puncMultiplier
                }
            }
            
            // 2. Word length sequence timing compensation
            if (_wordLengthTimingEnabled.value) {
                // Strip punctuation to find actual character reading load
                val cleanWord = word.filter { it.isLetterOrDigit() }
                val len = cleanWord.length
                val lengthMultiplier = when {
                    len <= 2 -> 0.82   // Very short words (e.g. "it", "of", "a") can be read much faster
                    len in 3..4 -> 0.92  // Moderate words (e.g. "the", "with")
                    len in 5..7 -> 1.0   // Average word length
                    len in 8..10 -> 1.22 // Extra reading visual cognitive capture
                    len in 11..13 -> 1.45 // High cognitive capacity load
                    else -> 1.65         // Extremely long words
                }
                wordMultiplier *= lengthMultiplier
            }
            
            // 3. Numerical series representation delay boost
            val isNumeric = word.all { it.isDigit() || it == ',' || it == '.' }
            if (isNumeric && word.length > 1) {
                wordMultiplier *= 1.25 // small mental pause for number capture
            }
            
            cumulativeMultiplier += wordMultiplier
        }
        
        // Return average word multiplier across this chunk block
        val averageMultiplier = cumulativeMultiplier / words.size
        val finalDelay = baseDelay * averageMultiplier
        
        // Clamp delay boundary to keep physical speed comfortable
        return finalDelay.coerceIn(50.0, 2500.0).toLong()
    }

    internal fun getEffectivePunctuation(word: String): Char? {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return null
        
        // Traverse backwards to fetch trailing punctuation, bypassing outer wrapping markers (quotes, parenthesis)
        var i = trimmed.length - 1
        while (i >= 0) {
            val char = trimmed[i]
            if (char in ".?!,;:-—") {
                return char
            }
            if (char.isLetterOrDigit()) {
                break
            }
            i--
        }
        return null
    }

    private fun startPeriodicProgressSaver() {
        dBSaveJob?.cancel()
        dBSaveJob = viewModelScope.launch {
            while (true) {
                delay(3000) // Debounce save every 3 seconds while playing
                if (_isPlaying.value) {
                    saveProgressToDb(instant = false)
                }
            }
        }
    }

    private suspend fun saveProgressToDb(instant: Boolean) {
        val book = _currentBook.value ?: return
        val currentChIndex = _currentChapterIndex.value
        val absoluteWordIndex = _currentChunkIndex.value * _chunkSize.value
        val currentSpeed = _wpm.value
        val currentSize = _chunkSize.value

        withContext(Dispatchers.IO) {
            repository.updateProgress(book.id, currentChIndex, absoluteWordIndex)
            repository.updateConfig(book.id, currentSpeed, currentSize)
            
            // Sync currentBook locally to reflect updated numbers
            val updatedBook = repository.getBookById(book.id)
            if (updatedBook != null) {
                _currentBook.value = updatedBook
            }
        }
    }

    override fun onCleared() {
        rsvpJob?.cancel()
        dBSaveJob?.cancel()
        super.onCleared()
    }
}

class RsvpViewModelFactory(private val repository: BookRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RsvpViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RsvpViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
