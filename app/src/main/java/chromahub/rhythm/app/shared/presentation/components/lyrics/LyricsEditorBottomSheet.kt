/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.lyrics

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmToggleButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.RhythmToggleOption
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.LyricsFileUtils
import chromahub.rhythm.app.util.RhythmLyricsParser
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.model.AppSettings
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.google.gson.Gson

enum class LyricFormat {
    SOURCE,
    LINE_BY_LINE,
    WORD_BY_WORD
}

data class SaveLyricsInput(
    val fileName: String,
    val mimeType: String,
    val initialUri: Uri?
)

class CreateDocumentWithInitialFolder : ActivityResultContract<SaveLyricsInput, Uri?>() {
    override fun createIntent(context: Context, input: SaveLyricsInput): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.fileName)
        if (input.initialUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input.initialUri)
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != android.app.Activity.RESULT_OK) null else intent.data
    }
}

private fun getInitialFolderUri(filePath: String?): Uri? {
    if (filePath.isNullOrBlank()) return null
    return try {
        val file = File(filePath)
        val parentFile = file.parentFile ?: return null
        val parentPath = parentFile.absolutePath

        // Check if it's primary external storage
        val primaryPrefix = "/storage/emulated/0"
        if (parentPath.startsWith(primaryPrefix, ignoreCase = true)) {
            val relativePath = parentPath.substring(primaryPrefix.length).trim('/')
            val docId = if (relativePath.isEmpty()) "primary:" else "primary:$relativePath"
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
        } else {
            // Check if it's secondary external storage (e.g. micro SD card /storage/XXXX-XXXX/...)
            val storagePrefix = "/storage/"
            if (parentPath.startsWith(storagePrefix, ignoreCase = true)) {
                val subPath = parentPath.substring(storagePrefix.length)
                val parts = subPath.split('/')
                if (parts.isNotEmpty()) {
                    val volumeId = parts[0]
                    if (volumeId != "emulated") {
                        val relativePath = parts.drop(1).joinToString("/")
                        val docId = if (relativePath.isEmpty()) "$volumeId:" else "$volumeId:$relativePath"
                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
                    } else null
                } else null
            } else null
        }
    } catch (e: Exception) {
        Log.w("LyricsEditor", "Failed to resolve initial folder URI for path: $filePath", e)
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorBottomSheet(
    lyricsData: LyricsData?,
    songTitle: String,
    initialTimeOffset: Int = 0,
    song: Song? = null,
    isStreamingMode: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, Int, String) -> Unit,
    onRefresh: () -> Unit = {},
    onEmbedInFile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    var selectedFormat by remember(lyricsData) {
        mutableStateOf(
            if (lyricsData?.wordByWordLyrics?.isNotBlank() == true) LyricFormat.WORD_BY_WORD
            else if (lyricsData?.syncedLyrics?.isNotBlank() == true) LyricFormat.LINE_BY_LINE
            else LyricFormat.SOURCE
        )
    }
    
    val sourceForm = remember(lyricsData) {
        lyricsData?.wordByWordLyrics?.takeIf { it.isNotBlank() }
            ?: lyricsData?.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: lyricsData?.plainLyrics?.takeIf { it.isNotBlank() }
            ?: ""
    }
    
    val lineByLineForm = remember(lyricsData) {
        lyricsData?.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: lyricsData?.wordByWordLyrics?.takeIf { it.isNotBlank() }?.let {
                try {
                    RhythmLyricsParser.toLRCFormat(RhythmLyricsParser.parseWordByWordLyrics(it))
                } catch (e: Exception) {
                    ""
                }
            }
            ?: lyricsData?.plainLyrics?.takeIf { it.isNotBlank() }
            ?: ""
    }
    
    val wordByWordForm = remember(lyricsData) {
        lyricsData?.wordByWordLyrics?.takeIf { it.isNotBlank() } ?: ""
    }
    
    var editedSource by remember { mutableStateOf(sourceForm) }
    var editedLineByLine by remember { mutableStateOf(lineByLineForm) }
    var editedWordByWord by remember { mutableStateOf(wordByWordForm) }
    
    val editedLyrics = when (selectedFormat) {
        LyricFormat.SOURCE -> editedSource
        LyricFormat.LINE_BY_LINE -> editedLineByLine
        LyricFormat.WORD_BY_WORD -> editedWordByWord
    }
    
    fun updateEditedLyrics(newText: String) {
        when (selectedFormat) {
            LyricFormat.SOURCE -> editedSource = newText
            LyricFormat.LINE_BY_LINE -> editedLineByLine = newText
            LyricFormat.WORD_BY_WORD -> editedWordByWord = newText
        }
    }
    
    var timeOffset by remember { mutableIntStateOf(initialTimeOffset) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    
    // Helper functions for lyrics document handling
    fun getDocumentDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        val name = cursor.getString(nameIndex)?.trim()
                        if (name.isNullOrEmpty()) null else name
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w("LyricsEditor", "Unable to read lyrics document name", e)
            null
        }
    }

    fun maybeRenameLyricsDocument(uri: Uri, expectedFileName: String): Uri {
        // Validate inputs
        if (expectedFileName.isBlank()) return uri
        
        val currentName = getDocumentDisplayName(uri) ?: return uri
        if (currentName.isBlank()) return uri
        
        // Check if provider appended .txt to our .lrc filename
        // E.g., we wanted "song.lrc" but got "song.lrc.txt"
        val currentNameWithoutTxt = if (currentName.endsWith(".txt", ignoreCase = true) && currentName.length > 4) {
            currentName.substring(0, currentName.length - 4)  // Safe because we checked length > 4
        } else {
            currentName
        }
        
        // Check if removing .txt from current name gives us the expected filename
        val shouldRename = currentName.endsWith(".txt", ignoreCase = true) &&
            currentNameWithoutTxt.equals(expectedFileName, ignoreCase = true)
        
        if (!shouldRename) {
            return uri
        }
        
        return try {
            DocumentsContract.renameDocument(context.contentResolver, uri, expectedFileName) ?: uri
        } catch (e: Exception) {
            Log.w("LyricsEditor", "Unable to rename saved lyrics document from '$currentName' to '$expectedFileName'", e)
            uri
        }
    }
    
    // Check if lyrics are synced (contain LRC timestamps or word-by-word JSON)
    val hasSyncedLyrics = remember(editedLyrics, selectedFormat) {
        selectedFormat == LyricFormat.WORD_BY_WORD ||
        editedLyrics.contains(Regex("""\[\d{2}:\d{2}\.\d{2,3}\]"""))
    }
    
    // Update edited states when lyricsData changes
    LaunchedEffect(lyricsData) {
        editedSource = sourceForm
        editedLineByLine = lineByLineForm
        editedWordByWord = wordByWordForm
    }
    
    // Update timeOffset when initialTimeOffset changes
    LaunchedEffect(initialTimeOffset) {
        timeOffset = initialTimeOffset
    }

    // Animation states
    var showContent by remember { mutableStateOf(true) }

    // Function to adjust LRC timestamps or word-by-word JSON timestamps
    fun adjustLyricsTimestamps(lyrics: String, offsetMs: Int): String {
        if (offsetMs == 0) return lyrics
        
        if (selectedFormat == LyricFormat.WORD_BY_WORD) {
            return try {
                val parsed = RhythmLyricsParser.parseWordByWordLyrics(lyrics)
                if (parsed.isEmpty()) return lyrics
                val adjusted = parsed.map { line ->
                    line.copy(
                        lineTimestamp = (line.lineTimestamp + offsetMs).coerceAtLeast(0L),
                        lineEndtime = (line.lineEndtime + offsetMs).coerceAtLeast(0L),
                        words = line.words.map { word ->
                            word.copy(
                                timestamp = (word.timestamp + offsetMs).coerceAtLeast(0L),
                                endtime = (word.endtime + offsetMs).coerceAtLeast(0L)
                            )
                        }
                    )
                }
                RhythmLyricsParser.toWordByWordJson(adjusted)
            } catch (e: Exception) {
                lyrics
            }
        }

        // If lyrics have Enhanced LRC word timestamps, adjust both line and word timestamps
        if (chromahub.rhythm.app.util.LyricsParser.hasWordTimestamps(lyrics)) {
            return try {
                val parsed = RhythmLyricsParser.parseEnhancedLRCtoWordByWord(lyrics)
                if (parsed.isNotEmpty()) {
                    val adjusted = parsed.map { line ->
                        line.copy(
                            lineTimestamp = (line.lineTimestamp + offsetMs).coerceAtLeast(0L),
                            lineEndtime = (line.lineEndtime + offsetMs).coerceAtLeast(0L),
                            words = line.words.map { word ->
                                word.copy(
                                    timestamp = (word.timestamp + offsetMs).coerceAtLeast(0L),
                                    endtime = (word.endtime + offsetMs).coerceAtLeast(0L)
                                )
                            }
                        )
                    }
                    RhythmLyricsParser.toEnhancedLRCFormat(adjusted)
                } else lyrics
            } catch (_: Exception) {
                lyrics
            }
        }
        
        val lrcRegex = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$""", RegexOption.MULTILINE)
        return lyrics.lines().joinToString("\n") { line ->
            lrcRegex.matchEntire(line)?.let { match ->
                val minutes = match.groupValues[1].toInt()
                val seconds = match.groupValues[2].toInt()
                val centiseconds = match.groupValues[3].padEnd(3, '0').take(3).toInt()
                val text = match.groupValues[4]
                
                // Convert to milliseconds
                var totalMs = (minutes * 60 * 1000) + (seconds * 1000) + centiseconds
                totalMs += offsetMs
                
                // Don't allow negative timestamps
                if (totalMs < 0) totalMs = 0
                
                // Convert back to LRC format
                val newMinutes = totalMs / 60000
                val newSeconds = (totalMs % 60000) / 1000
                val newCentiseconds = (totalMs % 1000)
                
                "[%02d:%02d.%03d]%s".format(newMinutes, newSeconds, newCentiseconds, text)
            } ?: line
        }
    }

    val appSettings = remember { AppSettings.getInstance(context) }
    val songLyricsPreferences by appSettings.songLyricsPreferences.collectAsState()
    val songCustomLrcFiles by appSettings.songCustomLrcFiles.collectAsState()
    val lrcRenameBehavior by appSettings.lrcRenameBehavior.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileName by remember { mutableStateOf("") }
    var pendingExpectedName by remember { mutableStateOf("") }
    var pendingLyrics by remember { mutableStateOf("") }
    var rememberChoiceCheckbox by remember { mutableStateOf(false) }

    fun applyLoadedLyrics(loadedLyrics: String) {
        val loadedTrimmed = loadedLyrics.trim()
        
        val isWordByWordJson = (loadedTrimmed.startsWith("[") || loadedTrimmed.startsWith("{")) && 
            (loadedTrimmed.contains("\"timestamp\"") || loadedTrimmed.contains("\"words\""))
            
        val isLrc = loadedLyrics.contains(Regex("\\[\\d{2}:\\d{2}\\.\\d{2,3}]"))

        val isTtml = loadedTrimmed.startsWith("<") && (
            loadedTrimmed.contains("<tt") ||
            loadedTrimmed.contains("http://www.w3.org/ns/ttml") ||
            loadedTrimmed.contains("<p ") ||
            loadedTrimmed.contains("<p>") ||
            loadedTrimmed.contains("<span ")
        )
        
        if (isWordByWordJson) {
            editedWordByWord = loadedLyrics
            editedSource = loadedLyrics
            try {
                val parsed = RhythmLyricsParser.parseWordByWordLyrics(loadedLyrics)
                editedLineByLine = RhythmLyricsParser.toLRCFormat(parsed)
            } catch (_: Exception) {
                editedLineByLine = ""
            }
            selectedFormat = LyricFormat.WORD_BY_WORD
        } else if (isTtml) {
            val parsedLines = RhythmLyricsParser.parseTtmlLyrics(loadedLyrics)
            if (parsedLines.isNotEmpty()) {
                val wordByWordJson = Gson().toJson(parsedLines)
                val parsedWordByWordLines = RhythmLyricsParser.parseWordByWordLyrics(wordByWordJson)
                val hasWordTiming = RhythmLyricsParser.hasWordTiming(parsedWordByWordLines)
                editedWordByWord = if (hasWordTiming) wordByWordJson else ""
                editedLineByLine = RhythmLyricsParser.toLRCFormat(parsedWordByWordLines)
                editedSource = loadedLyrics
                selectedFormat = if (hasWordTiming) LyricFormat.WORD_BY_WORD else LyricFormat.LINE_BY_LINE
            } else {
                val semanticLyrics = chromahub.rhythm.app.util.parseTtml(null, loadedLyrics)
                val plain = when (semanticLyrics) {
                    is chromahub.rhythm.app.util.SemanticLyrics.UnsyncedLyrics ->
                        semanticLyrics.unsyncedText.joinToString("\n") { it.first }
                    is chromahub.rhythm.app.util.SemanticLyrics.SyncedLyrics ->
                        semanticLyrics.text.joinToString("\n") { it.text }
                    else -> loadedLyrics
                }
                editedSource = loadedLyrics
                editedLineByLine = plain
                editedWordByWord = ""
                selectedFormat = LyricFormat.LINE_BY_LINE
            }
        } else if (isLrc) {
            val hasWordTimestamps = chromahub.rhythm.app.util.LyricsParser.hasWordTimestamps(loadedLyrics)
            if (hasWordTimestamps) {
                val parsedWordByWordLines = try {
                    RhythmLyricsParser.parseEnhancedLRCtoWordByWord(loadedLyrics)
                } catch (_: Exception) {
                    emptyList()
                }
                if (parsedWordByWordLines.isNotEmpty()) {
                    val wordByWordJson = Gson().toJson(parsedWordByWordLines)
                    val hasWordTiming = RhythmLyricsParser.hasWordTiming(parsedWordByWordLines)
                    editedWordByWord = if (hasWordTiming) wordByWordJson else ""
                    editedLineByLine = loadedLyrics
                    editedSource = loadedLyrics
                    selectedFormat = if (hasWordTiming) LyricFormat.WORD_BY_WORD else LyricFormat.LINE_BY_LINE
                } else {
                    editedLineByLine = loadedLyrics
                    editedSource = loadedLyrics
                    editedWordByWord = ""
                    selectedFormat = LyricFormat.LINE_BY_LINE
                }
            } else {
                editedLineByLine = loadedLyrics
                editedSource = loadedLyrics
                editedWordByWord = ""
                selectedFormat = LyricFormat.LINE_BY_LINE
            }
        } else {
            editedSource = loadedLyrics
            editedLineByLine = loadedLyrics
            editedWordByWord = ""
            selectedFormat = LyricFormat.SOURCE
        }
        Toast.makeText(context, R.string.lyrics_loaded_success, Toast.LENGTH_SHORT).show()
    }

    suspend fun performRename(
        context: Context,
        sourceUri: Uri,
        parentDir: File?,
        destFileName: String,
        content: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (parentDir == null || !parentDir.exists()) return@withContext false
        try {
            val destFile = File(parentDir, destFileName)
            destFile.writeText(content)
            true
        } catch (e: Exception) {
            Log.e("LyricsEditor", "Failed to write renamed file", e)
            false
        }
    }

    // File picker launcher for loading .lrc files
    val loadLyricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        LyricsFileUtils.loadLyricsFromUri(context, selectedUri)
                    }

                    if (result.lyrics != null) {
                        val loadedLyrics = result.lyrics
                        val loadedFileName = getDocumentDisplayName(selectedUri)
                        
                        if (song != null && song.path != null && loadedFileName != null) {
                            val songFile = File(song.path)
                            val songNameWithoutExt = songFile.nameWithoutExtension
                            val loadedExt = File(loadedFileName).extension.lowercase().ifEmpty { "lrc" }
                            val expectedLrcName = "$songNameWithoutExt.$loadedExt"
                            
                            if (!loadedFileName.equals(expectedLrcName, ignoreCase = true)) {
                                when (lrcRenameBehavior) {
                                    "always" -> {
                                        val success = performRename(context, selectedUri, songFile.parentFile, expectedLrcName, loadedLyrics)
                                        if (!success) {
                                            appSettings.setSongCustomLrcFile(song.id, loadedFileName)
                                            Toast.makeText(context, context.getString(R.string.lyrics_rename_permission_denied), Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.lyrics_renamed_to_format, expectedLrcName), Toast.LENGTH_SHORT).show()
                                        }
                                        applyLoadedLyrics(loadedLyrics)
                                    }
                                    "never" -> {
                                        appSettings.setSongCustomLrcFile(song.id, loadedFileName)
                                        applyLoadedLyrics(loadedLyrics)
                                    }
                                    else -> { // "ask"
                                        pendingUri = selectedUri
                                        pendingFileName = loadedFileName
                                        pendingExpectedName = expectedLrcName
                                        pendingLyrics = loadedLyrics
                                        rememberChoiceCheckbox = false
                                        showRenameDialog = true
                                    }
                                }
                            } else {
                                applyLoadedLyrics(loadedLyrics)
                            }
                        } else {
                            applyLoadedLyrics(loadedLyrics)
                        }
                    } else {
                        Toast.makeText(
                            context,
                            result.errorMessage ?: "Error loading lyrics file",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("LyricsEditor", "Error loading lyrics file", e)
                    Toast.makeText(context, context.getString(R.string.error_loading_lyrics, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val sanitizedTitle = remember(songTitle) {
        songTitle.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("_+"), "_")  // Collapse multiple underscores
            .trim('_')  // Remove leading/trailing underscores
            .takeIf { it.isNotEmpty() } ?: "lyrics"  // Fallback to "lyrics" if empty
    }

    val defaultLyricsFileName = remember(song, sanitizedTitle, selectedFormat, editedSource, editedLineByLine) {
        val baseName = if (song != null && !song.path.isNullOrBlank()) {
            File(song.path).nameWithoutExtension
        } else {
            sanitizedTitle
        }
        when {
            selectedFormat == LyricFormat.WORD_BY_WORD -> "$baseName.json"
            selectedFormat == LyricFormat.LINE_BY_LINE && chromahub.rhythm.app.util.LyricsParser.hasWordTimestamps(editedLineByLine) -> "$baseName.elrc"
            selectedFormat == LyricFormat.SOURCE && editedSource.trim().startsWith("<") -> "$baseName.ttml"
            else -> "$baseName.lrc"
        }
    }

    val detectedFormatLabel = remember(selectedFormat, editedLineByLine, editedSource, editedWordByWord) {
        when (selectedFormat) {
            LyricFormat.WORD_BY_WORD -> "Word-by-Word JSON (.json)"
            LyricFormat.LINE_BY_LINE -> {
                if (chromahub.rhythm.app.util.LyricsParser.hasWordTimestamps(editedLineByLine)) {
                    "Enhanced LRC (.elrc)"
                } else {
                    "Standard LRC (.lrc)"
                }
            }
            LyricFormat.SOURCE -> {
                if (editedSource.trim().startsWith("<")) {
                    "TTML XML (.ttml)"
                } else {
                    "Raw Source"
                }
            }
        }
    }

    // File picker launcher for saving .lrc files
    val saveLyricsLauncher = rememberLauncherForActivityResult(
        contract = CreateDocumentWithInitialFolder()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(editedLyrics.toByteArray())
                    outputStream.flush()
                }

                maybeRenameLyricsDocument(it, defaultLyricsFileName)

                Toast.makeText(context, R.string.lyrics_saved_success, Toast.LENGTH_SHORT).show()
                onSave(editedLyrics, timeOffset, selectedFormat.name)
                onDismiss()
            } catch (e: Exception) {
                Log.e("LyricsEditor", "Error saving lyrics file", e)
                Toast.makeText(context, context.getString(R.string.error_saving_lyrics, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.TWO_PANE_DIALOG,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header with animation
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                LyricsEditorHeader(
                    songTitle = songTitle,
                    hasLyrics = editedLyrics.isNotBlank(),
                    formatLabel = detectedFormatLabel
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (song != null) {
                val songId = song.id
                val currentPref = songLyricsPreferences[songId]
                val customLrc = songCustomLrcFiles[songId]
                
                var dropdownExpanded by remember { mutableStateOf(false) }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lyrics_source_preference),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (currentPref) {
                                    "online" -> "Online first"
                                    "embedded" -> "Embedded first"
                                    "lrc" -> "Local LRC file first"
                                    else -> "Default (App settings)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            FilledTonalButton(
                                onClick = { dropdownExpanded = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.lyrics_change))
                                Icon(
                                    imageVector = MaterialSymbolIcon("arrow_drop_down", filled = true),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier
                                    .widthIn(min = 220.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(4.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                val options = listOf(
                                    Triple(null, "Default (App settings)", "settings"),
                                    Triple("online", "Online first", "cloud"),
                                    Triple("embedded", "Embedded first", "music_note"),
                                    Triple("lrc", "Local LRC file first", "storage")
                                )
                                
                                val outerRadius = 16.dp
                                val innerRadius = 4.dp
                                val itemSpacing = 3.dp

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                                ) {
                                    options.forEachIndexed { index, (prefValue, label, iconName) ->
                                        val itemShape = when {
                                            options.size == 1 -> RoundedCornerShape(outerRadius)
                                            index == 0 -> RoundedCornerShape(
                                                topStart = outerRadius, topEnd = outerRadius,
                                                bottomStart = innerRadius, bottomEnd = innerRadius
                                            )
                                            index == options.size - 1 -> RoundedCornerShape(
                                                topStart = innerRadius, topEnd = innerRadius,
                                                bottomStart = outerRadius, bottomEnd = outerRadius
                                            )
                                            else -> RoundedCornerShape(innerRadius)
                                        }

                                        Surface(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                appSettings.setSongLyricsPreference(songId, prefValue)
                                                dropdownExpanded = false
                                                onRefresh()
                                            },
                                            shape = itemShape,
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = MaterialSymbolIcon(iconName, filled = true),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (customLrc != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("description", filled = true),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = customLrc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    appSettings.setSongCustomLrcFile(songId, null)
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.lyrics_clear), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Format Selector Button Group like Theme Switcher
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    RhythmToggleButtonGroup(
                        options = listOf(
                            RhythmToggleOption(text = stringResource(R.string.lyrics_source)),
                            RhythmToggleOption(text = stringResource(R.string.lyrics_line_by_line)),
                            RhythmToggleOption(text = stringResource(R.string.lyrics_word_by_word))
                        ),
                        selectedIndices = setOf(
                            when (selectedFormat) {
                                LyricFormat.SOURCE -> 0
                                LyricFormat.LINE_BY_LINE -> 1
                                LyricFormat.WORD_BY_WORD -> 2
                            }
                        ),
                        onToggle = { index ->
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            val targetFormat = when (index) {
                                0 -> LyricFormat.SOURCE
                                1 -> LyricFormat.LINE_BY_LINE
                                else -> LyricFormat.WORD_BY_WORD
                            }
                            // Auto-translate between formats when switching
                            if (targetFormat == LyricFormat.LINE_BY_LINE && editedLineByLine.isBlank() && editedWordByWord.isNotBlank()) {
                                try {
                                    val parsed = RhythmLyricsParser.parseWordByWordLyrics(editedWordByWord)
                                    if (parsed.isNotEmpty()) {
                                        editedLineByLine = RhythmLyricsParser.toEnhancedLRCFormat(parsed)
                                    }
                                } catch (_: Exception) {}
                            } else if (targetFormat == LyricFormat.WORD_BY_WORD && editedWordByWord.isBlank() && chromahub.rhythm.app.util.LyricsParser.hasWordTimestamps(editedLineByLine)) {
                                try {
                                    val parsed = RhythmLyricsParser.parseEnhancedLRCtoWordByWord(editedLineByLine)
                                    if (parsed.isNotEmpty()) {
                                        editedWordByWord = com.google.gson.Gson().toJson(parsed)
                                    }
                                } catch (_: Exception) {}
                            } else if (targetFormat == LyricFormat.SOURCE && editedSource.isBlank()) {
                                if (editedWordByWord.isNotBlank()) {
                                    try {
                                        val parsed = RhythmLyricsParser.parseWordByWordLyrics(editedWordByWord)
                                        if (parsed.isNotEmpty()) {
                                            editedSource = RhythmLyricsParser.toTtmlFormat(parsed, song?.title, song?.artist)
                                        }
                                    } catch (_: Exception) {}
                                } else if (editedLineByLine.isNotBlank()) {
                                    editedSource = editedLineByLine
                                }
                            }
                            selectedFormat = targetFormat
                        },
                        modifier = Modifier.fillMaxWidth(),
                        size = RhythmButtonSize.Medium,
                        isShowingCheck = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timestamp Adjustment Controls
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = MaterialSymbolIcon("sync", filled = true),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (hasSyncedLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = context.getString(R.string.sync_adjustment),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasSyncedLyrics) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (hasSyncedLyrics) {
                                Text(
                                    text = "${if (timeOffset >= 0) "+" else ""}${timeOffset}ms",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Reset/Refresh button
                            FilledTonalButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    timeOffset = 0
                                    onRefresh()
                                },
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(context.getString(R.string.bottomsheet_reset), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    RhythmGroupedButton(
                        modifier = Modifier.fillMaxWidth(),
                        size = RhythmButtonSize.Large
                    ) {
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                timeOffset -= 500
                                val adjusted = adjustLyricsTimestamps(editedLyrics, -500)
                                updateEditedLyrics(adjusted)
                                onSave(adjusted, timeOffset, selectedFormat.name)
                            },
                            weight = 1f,
                            isFirst = true,
                            isLast = false,
                            enabled = hasSyncedLyrics,
                            height = 72.dp,
                            containerColor = if (hasSyncedLyrics) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            contentColor = if (hasSyncedLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Remove,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.lyricseditorbottomsheet_str_500ms),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = context.getString(R.string.bottomsheet_lyrics_earlier),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasSyncedLyrics) 
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                        
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                timeOffset -= 100
                                val adjusted = adjustLyricsTimestamps(editedLyrics, -100)
                                updateEditedLyrics(adjusted)
                                onSave(adjusted, timeOffset, selectedFormat.name)
                            },
                            weight = 1f,
                            isFirst = false,
                            isLast = false,
                            enabled = hasSyncedLyrics,
                            height = 72.dp,
                            containerColor = if (hasSyncedLyrics) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            contentColor = if (hasSyncedLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Remove,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.lyricseditorbottomsheet_str_100ms),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = context.getString(R.string.bottomsheet_lyrics_earlier),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasSyncedLyrics) 
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                        
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                timeOffset += 100
                                val adjusted = adjustLyricsTimestamps(editedLyrics, 100)
                                updateEditedLyrics(adjusted)
                                onSave(adjusted, timeOffset, selectedFormat.name)
                            },
                            weight = 1f,
                            isFirst = false,
                            isLast = false,
                            enabled = hasSyncedLyrics,
                            height = 72.dp,
                            containerColor = if (hasSyncedLyrics) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            contentColor = if (hasSyncedLyrics) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.lyricseditorbottomsheet_str_100ms),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = context.getString(R.string.bottomsheet_lyrics_later),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasSyncedLyrics) 
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                        
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                timeOffset += 500
                                val adjusted = adjustLyricsTimestamps(editedLyrics, 500)
                                updateEditedLyrics(adjusted)
                                onSave(adjusted, timeOffset, selectedFormat.name)
                            },
                            weight = 1f,
                            isFirst = false,
                            isLast = true,
                            enabled = hasSyncedLyrics,
                            height = 72.dp,
                            containerColor = if (hasSyncedLyrics) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            contentColor = if (hasSyncedLyrics) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.lyricseditorbottomsheet_str_500ms),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = context.getString(R.string.bottomsheet_lyrics_later),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasSyncedLyrics) 
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lyrics Text Field with animation
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp)
                ) {
                    OutlinedTextField(
                        value = editedLyrics,
                        onValueChange = { updateEditedLyrics(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        placeholder = {
                            Text(
                                text = when (selectedFormat) {
                                    LyricFormat.WORD_BY_WORD -> "Enter word-by-word lyrics JSON…"
                                    LyricFormat.LINE_BY_LINE -> "Enter timestamped LRC or Enhanced LRC ([00:12.34]<00:12.34>word)…"
                                    LyricFormat.SOURCE -> "Enter raw TTML XML, LRC, or plain text…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // Sticky Footer with action buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    RhythmGroupedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        size = RhythmButtonSize.Large
                    ) {
                        // Load File Button
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                loadLyricsLauncher.launch(
                                    arrayOf(
                                        "text/plain",
                                        "text/*",
                                        "text/x-lrc",
                                        "application/x-lrc",
                                        "application/json",
                                        "application/xml",
                                        "text/xml",
                                        "application/ttml+xml",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            weight = 1f,
                            isFirst = true,
                            icon = MaterialSymbolIcon("file_open", filled = true),
                            text = context.getString(R.string.bottomsheet_lyrics_load)
                        )

                        // Save File Button
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                if (editedLyrics.isNotBlank()) {
                                    val mimeType = when {
                                        selectedFormat == LyricFormat.WORD_BY_WORD -> "application/json"
                                        selectedFormat == LyricFormat.LINE_BY_LINE && chromahub.rhythm.app.util.LyricsParser.hasWordTimestamps(editedLineByLine) -> "text/x-lrc"
                                        selectedFormat == LyricFormat.SOURCE && editedSource.trim().startsWith("<") -> "application/ttml+xml"
                                        else -> "application/octet-stream"
                                    }
                                    val initialUri = getInitialFolderUri(song?.path)
                                    saveLyricsLauncher.launch(
                                        SaveLyricsInput(
                                            fileName = defaultLyricsFileName,
                                            mimeType = mimeType,
                                            initialUri = initialUri
                                        )
                                    )
                                }
                            },
                            weight = 1f,
                            isLast = true,
                            enabled = editedLyrics.isNotBlank(),
                            icon = MaterialSymbolIcon("save", filled = true),
                            text = context.getString(R.string.bottomsheet_lyrics_save)
                        )
                    }

                    // Embed in File is local-only — streaming songs have no writable file.
                    if (!isStreamingMode) {
                        Spacer(modifier = Modifier.height(12.dp))

                        RhythmGroupedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            size = RhythmButtonSize.Large
                        ) {
                            RhythmButtonWeighted(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    if (editedLyrics.isNotBlank()) {
                                        onEmbedInFile(editedLyrics)
                                    }
                                },
                                weight = 1f,
                                isFirst = true,
                                isLast = true,
                                enabled = editedLyrics.isNotBlank(),
                                icon = RhythmIcons.MusicNote,
                                text = context.getString(R.string.bottomsheet_lyrics_embed)
                            )
                        }
                    }
            }
        }

        if (showRenameDialog && song != null) {
            AlertDialog(
                onDismissRequest = { 
                    showRenameDialog = false 
                    appSettings.setSongCustomLrcFile(song.id, pendingFileName)
                    applyLoadedLyrics(pendingLyrics)
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("drive_file_rename_outline", filled = true),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.lyrics_rename_lrc_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.lyrics_rename_lrc_body, pendingFileName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberChoiceCheckbox,
                                onCheckedChange = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    rememberChoiceCheckbox = it
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.lyrics_remember_choice),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRenameDialog = false
                            if (rememberChoiceCheckbox) {
                                appSettings.setLrcRenameBehavior("always")
                            }
                            scope.launch {
                                val songFile = File(song.path!!)
                                val success = performRename(context, pendingUri!!, songFile.parentFile, pendingExpectedName, pendingLyrics)
                                if (!success) {
                                    appSettings.setSongCustomLrcFile(song.id, pendingFileName)
                                    Toast.makeText(context, context.getString(R.string.lyrics_rename_permission_denied), Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.lyrics_renamed_success), Toast.LENGTH_SHORT).show()
                                }
                                applyLoadedLyrics(pendingLyrics)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("drive_file_rename_outline", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.lyrics_rename))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showRenameDialog = false
                            if (rememberChoiceCheckbox) {
                                appSettings.setLrcRenameBehavior("never")
                            }
                            appSettings.setSongCustomLrcFile(song.id, pendingFileName)
                            applyLoadedLyrics(pendingLyrics)
                        }
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("label", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.lyrics_tag_keep_custom))
                    }
                }
            )
        }
    }
}
}

@Composable
private fun LyricsEditorHeader(
    songTitle: String,
    hasLyrics: Boolean,
    formatLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = context.getString(R.string.lyrics_editor_title),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        )
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        text = songTitle,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!formatLabel.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            text = formatLabel,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

private fun getDocumentDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    val name = cursor.getString(nameIndex)?.trim()
                    if (name.isNullOrEmpty()) null else name
                } else {
                    null
                }
            } else {
                null
            }
        }
    } catch (e: Exception) {
        Log.w("LyricsEditor", "Unable to read lyrics document name", e)
        null
    }
}
