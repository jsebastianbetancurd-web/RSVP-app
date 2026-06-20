package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.BookEntity
import com.example.data.ChapterEntity

// Standard Optimal Recognition Point (ORP) index mapping
fun getOrpIndex(word: String): Int {
    val length = word.length
    if (length <= 1) return 0
    if (length in 2..5) return 1
    if (length in 6..9) return 2
    if (length in 10..13) return 3
    return 4
}

@Composable
fun OrpWordView(chunk: String, modifier: Modifier = Modifier) {
    val words = chunk.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (words.size == 1) {
            val word = words[0]
            val orpIdx = getOrpIndex(word)
            if (orpIdx in word.indices) {
                val prefix = word.substring(0, orpIdx)
                val highlight = word[orpIdx].toString()
                val suffix = word.substring(orpIdx + 1)

                val padLen = maxOf(prefix.length, suffix.length)
                val paddedPrefix = prefix.padStart(padLen, ' ')
                val paddedSuffix = suffix.padEnd(padLen, ' ')

                val textLen = paddedPrefix.length + 1 + paddedSuffix.length
                val fontSizeVal = when {
                    textLen <= 6 -> 54.sp
                    textLen <= 10 -> 44.sp
                    textLen <= 14 -> 34.sp
                    else -> 24.sp
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = paddedPrefix,
                        color = Color.White,
                        fontSize = fontSizeVal,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                    Text(
                        text = highlight,
                        color = Color(0xFFFF0000), // Pure Red
                        fontSize = fontSizeVal,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Text(
                        text = paddedSuffix,
                        color = Color.White,
                        fontSize = fontSizeVal,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Start,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = word,
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        } else {
            // Multiple words chunk: highlight ORP of first word, draw other words in white
            val totalChars = chunk.length
            val fontSizeVal = when {
                totalChars <= 10 -> 42.sp
                totalChars <= 18 -> 32.sp
                totalChars <= 25 -> 24.sp
                else -> 18.sp
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                words.forEachIndexed { index, word ->
                    if (index > 0) {
                        Text(
                            text = " ",
                            color = Color.White,
                            fontSize = fontSizeVal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (index == 0) {
                        val orpIdx = getOrpIndex(word)
                        if (orpIdx in word.indices) {
                            val prefix = word.substring(0, orpIdx)
                            val highlight = word[orpIdx].toString()
                            val suffix = word.substring(orpIdx + 1)
                            Text(prefix, color = Color.White, fontSize = fontSizeVal, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(highlight, color = Color(0xFFFF0000), fontSize = fontSizeVal, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                            Text(suffix, color = Color.White, fontSize = fontSizeVal, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        } else {
                            Text(word, color = Color.White, fontSize = fontSizeVal, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    } else {
                        Text(word, color = Color.White, fontSize = fontSizeVal, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun RsvpAppScreen(viewModel: RsvpViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val currentBook by viewModel.currentBook.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF000000) // Pure Black Background throughout entire experience
    ) {
        Crossfade(targetState = currentBook, label = "ScreenTransition") { book ->
            if (book == null) {
                DashboardScreen(viewModel = viewModel)
            } else {
                ReaderScreen(viewModel = viewModel, book = book)
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: RsvpViewModel) {
    val context = LocalContext.current
    val books by viewModel.booksState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val epubPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importEpub(context, uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Minimal header (White on Black)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Read Faster",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White, // Pure White title
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Minimal RSVP Speed Book Reader",
                        fontSize = 13.sp,
                        color = Color.LightGray, // Pure light grey text
                        fontFamily = FontFamily.SansSerif
                    )
                }
                
                IconButton(
                    onClick = { epubPicker.launch("*/*") },
                    modifier = Modifier
                        .testTag("import_epub_button_top")
                        .border(BorderStroke(1.dp, Color.White), CircleShape)
                        .background(Color.Black, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Import EPUB",
                        tint = Color.White
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analyzing & Parsing EPUB...",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (books.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = "Empty Library",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No books imported",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Upload any local .epub file and parse it to start reading fast.",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = { epubPicker.launch("*/*") },
                            modifier = Modifier
                                .testTag("import_epub_button")
                                .height(48.dp),
                            border = BorderStroke(1.dp, Color.White),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Book", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            } else {
                Text(
                    text = "MY LIBRARY",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(books, key = { it.id }) { book ->
                        BookItemCard(
                            book = book,
                            onClick = { viewModel.selectBook(book) },
                            onDelete = { viewModel.deleteBook(book.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookItemCard(
    book: BookEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("book_item_card_${book.id}")
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, Color(0xFF222222)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(4.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Chapter ${book.currentChapterIndex + 1} • ${book.currentWpm} WPM",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }

            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.testTag("delete_book_button_${book.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete book",
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete from library?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Permanently delete of '${book.title}' and all reading records.", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("DELETE", color = Color(0xFFFF0000), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCEL", color = Color.White)
                }
            },
            containerColor = Color.Black,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.border(BorderStroke(1.dp, Color(0xFF333333)), RoundedCornerShape(8.dp))
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ReaderScreen(viewModel: RsvpViewModel, book: BookEntity) {
    val chapters by viewModel.chapters.collectAsState()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsState()
    val chunks by viewModel.chunks.collectAsState()
    val currentChunkIndex by viewModel.currentChunkIndex.collectAsState()
    val wpm by viewModel.wpm.collectAsState()
    val chunkSize by viewModel.chunkSize.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val punctuationPauseEnabled by viewModel.punctuationPauseEnabled.collectAsState()
    val wordLengthTimingEnabled by viewModel.wordLengthTimingEnabled.collectAsState()
    val sentencePauseMultiplier by viewModel.sentencePauseMultiplier.collectAsState()
    val commaPauseMultiplier by viewModel.commaPauseMultiplier.collectAsState()

    var showChapterSelector by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val currentChapter = chapters.getOrNull(currentChapterIndex)
    val currentWordText = chunks.getOrNull(currentChunkIndex) ?: ""

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = Color(0xFF000000),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color.Black)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.closeBook() },
                    modifier = Modifier.testTag("close_book_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Exit to bookshelf",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showChapterSelector = true }
                        .testTag("chapter_selector_button"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentChapter?.title ?: "Select Chapter",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = book.title,
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.testTag("rsvp_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Timing & Engine Settings",
                        tint = Color.White
                    )
                }
            }
        },
        bottomBar = {
            // Minimalistic and unobtrusive controls row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(top = 16.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // WPM Speed Control
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SPEED",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "$wpm WPM",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Slider(
                        value = wpm.toFloat(),
                        onValueChange = { viewModel.changeWpm(it.toInt()) },
                        valueRange = 100f..800f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF222222)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wpm_slider")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Words per block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "WORDS/FRAME",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (size in 1..3) {
                            val selected = chunkSize == size
                            Box(
                                modifier = Modifier
                                    .size(width = 46.dp, height = 28.dp)
                                    .border(
                                        BorderStroke(
                                            width = 1.dp,
                                            color = if (selected) Color.White else Color(0xFF222222)
                                        ),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .background(Color.Black)
                                    .clickable { viewModel.changeChunkSize(size) }
                                    .testTag("chunk_size_$size"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$size W",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Reading Progress Slider
                val totalChunks = chunks.size
                val currentPercent = if (totalChunks > 0) currentChunkIndex.toFloat() / totalChunks else 0f
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Slider(
                        value = currentPercent,
                        onValueChange = { viewModel.seekToPercentage(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF222222)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Word ${currentChunkIndex * chunkSize} of ${totalChunks * chunkSize}",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = "${(currentPercent * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // RSVP Flashing Field (The Main Reading physics container)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black) // Strict pure black theme
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.togglePlayPause()
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Focus Lines and tick marks matching screenshots perfectly
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val lineWeight = 1.dp.toPx()
                            val lineColor = Color.White.copy(alpha = 0.2f)
                            val lineColorTicks = Color.White.copy(alpha = 0.35f)
                            
                            val centerY = size.height / 2f
                            val centerX = size.width / 2f
                            val offsetFromCenter = 44.dp.toPx()
                            val tickLength = 10.dp.toPx()
                            
                            // Top guide line
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, centerY - offsetFromCenter),
                                end = Offset(size.width, centerY - offsetFromCenter),
                                strokeWidth = lineWeight
                            )
                            // Top vertical center focus tick pointing down
                            drawLine(
                                color = lineColorTicks,
                                start = Offset(centerX, centerY - offsetFromCenter),
                                end = Offset(centerX, centerY - offsetFromCenter + tickLength),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            
                            // Bottom guide line
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, centerY + offsetFromCenter),
                                end = Offset(size.width, centerY + offsetFromCenter),
                                strokeWidth = lineWeight
                            )
                            // Bottom vertical center focus tick pointing up
                            drawLine(
                                color = lineColorTicks,
                                start = Offset(centerX, centerY + offsetFromCenter),
                                end = Offset(centerX, centerY + offsetFromCenter - tickLength),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedContent(
                            targetState = currentWordText,
                            transitionSpec = {
                                fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                            },
                            label = "RsvpWordSplash"
                        ) { txt ->
                            OrpWordView(chunk = txt)
                        }
                    }
                }

                // Clean overlay status indicator on pause
                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .border(BorderStroke(1.dp, Color.White), CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Tap to read",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Unobtrusive Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rewind 10 words
                IconButton(
                    onClick = { viewModel.rewindWords(10) },
                    modifier = Modifier
                        .size(48.dp)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), CircleShape)
                        .background(Color.Black, CircleShape)
                        .testTag("rsvp_rewind_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind 10 words",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(28.dp))

                // Play / Pause Button in Clean White-on-Black style
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(64.dp)
                        .border(BorderStroke(1.5.dp, Color.White), CircleShape)
                        .background(Color.Black, CircleShape)
                        .testTag("rsvp_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(28.dp))

                // Fast Forward 10 words
                IconButton(
                    onClick = { viewModel.fastForwardWords(10) },
                    modifier = Modifier
                        .size(48.dp)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), CircleShape)
                        .background(Color.Black, CircleShape)
                        .testTag("rsvp_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Fast forward 10 words",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // Chapters list overlay
    if (showChapterSelector) {
        Dialog(onDismissRequest = { showChapterSelector = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "CHAPTERS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(chapters) { ch ->
                            val current = ch.index == currentChapterIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectChapter(ch.index)
                                        showChapterSelector = false
                                    }
                                    .background(
                                        if (current) Color.White.copy(alpha = 0.15f)
                                        else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (current) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = ch.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { showChapterSelector = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("CLOSE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Engine Settings overlay
    if (showSettingsDialog) {
        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "ENGINE TIMING SETTINGS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Punctuation Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Punctuation Pauses",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Pause slightly longer on sentence/clause markers",
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                        Switch(
                            checked = punctuationPauseEnabled,
                            onCheckedChange = { viewModel.togglePunctuationPause(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color.White,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.Black,
                                uncheckedBorderColor = Color.White
                            ),
                            modifier = Modifier.testTag("toggle_punctuation")
                        )
                    }

                    if (punctuationPauseEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Sentence Delay Slider
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Sentence End Delay",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${String.format("%.1f", sentencePauseMultiplier)}x",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Slider(
                                value = sentencePauseMultiplier,
                                onValueChange = { viewModel.setSentencePauseMultiplier(it) },
                                valueRange = 1.0f..3.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color(0xFF222222)
                                ),
                                modifier = Modifier.testTag("sentence_delay_slider")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Comma Delay Slider
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Clause / Connector Delay",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${String.format("%.1f", commaPauseMultiplier)}x",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Slider(
                                value = commaPauseMultiplier,
                                onValueChange = { viewModel.setCommaPauseMultiplier(it) },
                                valueRange = 1.0f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color(0xFF222222)
                                ),
                                modifier = Modifier.testTag("comma_delay_slider")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF111111), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Word Length Sequence Timing Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Word Length Compensation",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Allows longer words extra display duration & shorter words compressed speed",
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                        Switch(
                            checked = wordLengthTimingEnabled,
                            onCheckedChange = { viewModel.toggleWordLengthTiming(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color.White,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.Black,
                                uncheckedBorderColor = Color.White
                            ),
                            modifier = Modifier.testTag("toggle_word_length")
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    TextButton(
                        onClick = { showSettingsDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("CLOSE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
