// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.presentation.metadata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.experience.*
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalCollectionsPanel(songs: List<Song>, favorites: List<String>, currentId: String?, musicViewModel: MusicViewModel, onMoveSection: () -> Unit = {}) {
    PersonalCollectionsContent(songs, favorites, currentId,
        onPlayQueue = { queue, index -> musicViewModel.playQueue(queue, enableShuffle = false, startIndex = index) },
        onCreatePlaylist = { title, queue -> musicViewModel.createPlaylist(title, queue) }, onMoveSection = onMoveSection)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalCollectionsContent(songs: List<Song>, favorites: List<String>, currentId: String?,
    onPlayQueue: (List<Song>, Int) -> Unit, onCreatePlaylist: (String, List<Song>) -> Unit, onMoveSection: () -> Unit = {}) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val operationError = CoroutineExceptionHandler { _, _ -> message = context.getString(R.string.adaptive_operation_failed) }
    var hour by remember { mutableStateOf(System.currentTimeMillis() / 3600_000L) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); hour = System.currentTimeMillis() / 3600_000L } }
    var proposal by remember { mutableStateOf(emptyList<Song>()) }
    var proposalMode by remember { mutableStateOf(PersonalCollection.DAYLIST) }
    var proposalBusy by remember { mutableStateOf(false) }
    var reaction by remember { mutableStateOf<Double?>(null) }
    var ratingBusy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<MixSnapshot?>(null) }
    var refresh by remember { mutableLongStateOf(0L) }
    var drawer by remember { mutableStateOf(false) }
    var shelf by remember { mutableStateOf(emptyList<MixSnapshot>()) }
    val catalog = remember(songs) { songs.associateBy { it.uri.toString() } }
    LaunchedEffect(songs, hour, refresh) {
        proposalBusy = true
        try {
            val value = withContext(Dispatchers.IO) {
                val existing = if (refresh == 0L) RecommendationShelf.current(context) else null
                val current = existing?.takeIf { it.createdAt / 3600_000L == hour && it.songKeys.any(catalog::containsKey) } ?: run {
                    val mode = proposalMode
                    val seed = System.currentTimeMillis() xor System.nanoTime()
                    val picks = PersonalCollections.generate(context, songs, favorites, currentId, mode, 20, seed = seed)
                    RecommendationShelf.create(context, mode, null, picks.map { it.uri.toString() }, seed)
                }
                current?.let { RecommendationLearning.get(context).restoreOffers(it) }
                current to current?.let { RecommendationLearning.get(context).model().collections[it.feedbackKey] }
            }
            snapshot = value.first; proposalMode = value.first?.mode ?: proposalMode
            proposal = value.first?.songKeys.orEmpty().mapNotNull(catalog::get); reaction = value.second
            withContext(Dispatchers.IO) { RecommendationLearning.get(context).presented(proposal.map { it.uri.toString() }) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { message = context.getString(R.string.adaptive_operation_failed) }
        finally { proposalBusy = false }
    }
    fun rate(value: Double?) {
        val target = snapshot ?: return
        ratingBusy = true
        scope.launch(operationError) { try {
            withContext(Dispatchers.IO) { RecommendationLearning.get(context).collectionFeedback(target.feedbackKey, target.songKeys, value) }
            if (snapshot?.id == target.id) reaction = value
        } finally { ratingBusy = false } }
    }
    var selected by remember { mutableStateOf<PersonalCollection?>(null) }
    var sheetSnapshot by remember { mutableStateOf<MixSnapshot?>(null) }
    var sheetRefresh by remember { mutableLongStateOf(0L) }
    var collection by remember { mutableStateOf(emptyList<Song>()) }
    var busy by remember { mutableStateOf(false) }; var genre by remember { mutableStateOf<String?>(null) }
    var genresOpen by remember { mutableStateOf(false) }
    fun label(mode: PersonalCollection): String = context.getString(when(mode) {
        PersonalCollection.DAYLIST -> R.string.personal_daylist; PersonalCollection.DAILY_MIX -> R.string.personal_daily_mix
        PersonalCollection.REDISCOVERY -> R.string.personal_rediscovery; PersonalCollection.SIMILAR -> R.string.personal_similar
        PersonalCollection.FAVORITES -> R.string.personal_liked_mix; PersonalCollection.FEWER_REPEATS -> R.string.personal_fewer_repeats
    })
    LaunchedEffect(selected, genre, sheetRefresh) {
        val mode = selected ?: return@LaunchedEffect
        busy = true; message = null
        try {
            val item = withContext(Dispatchers.IO) {
                sheetSnapshot?.takeIf { it.mode == mode && it.genre == genre } ?: run {
                    val seed = System.currentTimeMillis() xor System.nanoTime()
                    val picks = PersonalCollections.generate(context, songs, favorites, currentId, mode, genre = genre, seed = seed)
                    RecommendationShelf.create(context, mode, genre, picks.map { it.uri.toString() }, seed)
                }
            }
            item?.let { RecommendationLearning.get(context).restoreOffers(it) }
            sheetSnapshot = item; collection = item?.songKeys.orEmpty().mapNotNull(catalog::get)
            withContext(Dispatchers.IO) { RecommendationLearning.get(context).presented(collection.map { it.uri.toString() }) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { message = context.getString(R.string.adaptive_operation_failed) }
        finally { busy = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.personal_collections_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(enabled = !proposalBusy && !ratingBusy, onClick = { refresh++ }) { Icon(MaterialSymbolIcon("refresh"), stringResource(R.string.personal_refresh_mix), Modifier.size(24.dp)) }
            IconButton(onClick = { drawer = true; scope.launch(operationError) { shelf = withContext(Dispatchers.IO) { RecommendationShelf.recent(context) } } }) { Icon(MaterialSymbolIcon("shelves"), stringResource(R.string.personal_mix_shelf), Modifier.size(24.dp)) }
            IconButton(onClick = onMoveSection) { Icon(MaterialSymbolIcon("swap_vert"), stringResource(R.string.personal_move_section), Modifier.size(24.dp)) }
        }
        Text(stringResource(R.string.personal_collections_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(onClick = { sheetSnapshot = snapshot; selected = proposalMode; genre = snapshot?.genre }, shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
                        val covers = proposal.take(4)
                        if (covers.isEmpty()) Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(MaterialSymbolIcon("auto_awesome"), null, Modifier.size(32.dp)) }
                        else Column {
                            covers.chunked(2).forEach { row -> Row(Modifier.weight(1f)) { row.forEach { song ->
                                chromahub.rhythm.app.util.M3ImageUtils.M3MediaImage(song.artworkUri, null, Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(0.dp), type = chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType.TRACK, name = song.title)
                            } } }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.personal_proposed_mix), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(label(proposalMode), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(context.getString(R.string.personal_mix_count, proposal.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (proposalBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FilterChip(selected = reaction == 1.0, enabled = !proposalBusy && !ratingBusy && proposal.isNotEmpty(),
                        onClick = { rate(if (reaction == 1.0) null else 1.0) }, label = { Text(stringResource(R.string.personal_like)) },
                        leadingIcon = { Icon(MaterialSymbolIcon("thumb_up", filled = reaction == 1.0), null, Modifier.size(18.dp)) })
                    FilterChip(selected = reaction == 0.0, enabled = !proposalBusy && !ratingBusy && proposal.isNotEmpty(),
                        onClick = { rate(if (reaction == 0.0) null else 0.0) }, label = { Text(stringResource(R.string.personal_dislike)) },
                        leadingIcon = { Icon(MaterialSymbolIcon("thumb_down", filled = reaction == 0.0), null, Modifier.size(18.dp)) })
                    Spacer(Modifier.weight(1f))
                    FilledTonalIconButton(enabled = !proposalBusy && proposal.isNotEmpty(), onClick = {
                        val picks = proposal.toList(); scope.launch(operationError) { withContext(Dispatchers.IO) { RecommendationLearning.get(context).selected(picks.first().uri.toString()) }; onPlayQueue(picks, 0) }
                    }) { Icon(MaterialSymbolIcon("play_arrow"), stringResource(R.string.action_play), Modifier.size(24.dp)) }
                }
                Text(stringResource(R.string.personal_feedback_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!proposalBusy && proposal.isEmpty()) Text(stringResource(R.string.personal_collection_empty), style = MaterialTheme.typography.bodySmall)
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.personal_more_mixes)); Spacer(Modifier.width(8.dp)); Icon(MaterialSymbolIcon(if (expanded) "expand_less" else "expand_more"), null, Modifier.size(18.dp)) }
        if (expanded)         PersonalCollection.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { mode ->
                    Card(onClick = { sheetSnapshot = null; selected = mode; genre = null }, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = if (mode == PersonalCollection.DAYLIST) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(MaterialSymbolIcon(when(mode) { PersonalCollection.DAYLIST -> "schedule"; PersonalCollection.DAILY_MIX -> "album";
                                PersonalCollection.REDISCOVERY -> "history"; PersonalCollection.SIMILAR -> "graphic_eq"; PersonalCollection.FAVORITES -> "favorite"; else -> "shuffle" }), null, Modifier.size(24.dp))
                            Text(label(mode), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    if (selected != null) ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label(selected!!), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.personal_collection_local), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected == PersonalCollection.DAYLIST) Text(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE HH:mm", configuration.locales[0])), style = MaterialTheme.typography.labelLarge)
            if (selected in setOf(PersonalCollection.FAVORITES, PersonalCollection.DAILY_MIX)) {
                Box {
                    OutlinedButton(onClick = { genresOpen = true }) { Text(genre ?: stringResource(R.string.personal_all_genres)) }
                    DropdownMenu(expanded = genresOpen, onDismissRequest = { genresOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.personal_all_genres)) }, onClick = { sheetSnapshot = null; genre = null; genresOpen = false })
                        songs.mapNotNull { it.genre?.trim()?.takeIf(String::isNotBlank) }.distinct().sorted().forEach { value ->
                            DropdownMenuItem(text = { Text(value) }, onClick = { sheetSnapshot = null; genre = value; genresOpen = false })
                        }
                    }
                }
            }
            val currentCollectionId = sheetSnapshot?.feedbackKey.orEmpty()
            TextButton(enabled = !busy && !ratingBusy, onClick = { sheetSnapshot = null; sheetRefresh++ }) {
                Icon(MaterialSymbolIcon("refresh"), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.personal_refresh_mix))
            }
            var sheetReaction by remember(currentCollectionId) { mutableStateOf<Double?>(null) }
            LaunchedEffect(currentCollectionId) { sheetReaction = withContext(Dispatchers.IO) { RecommendationLearning.get(context).model().collections[currentCollectionId] } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1.0 to R.string.personal_like, 0.0 to R.string.personal_dislike).forEach { (value, label) ->
                    FilterChip(selected = sheetReaction == value, enabled = !busy && collection.isNotEmpty() && !ratingBusy, onClick = {
                        val next = if (sheetReaction == value) null else value; val keys = collection.map { it.uri.toString() }; ratingBusy = true
                        scope.launch(operationError) { try { withContext(Dispatchers.IO) { RecommendationLearning.get(context).collectionFeedback(currentCollectionId, keys, next) }; if (sheetSnapshot?.feedbackKey == currentCollectionId) sheetReaction = next; if (snapshot?.feedbackKey == currentCollectionId) reaction = next } finally { ratingBusy = false } }
                    }, label = { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.personal_feedback_toggle_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && collection.isNotEmpty(), onClick = {
                    val queue = collection.toList(); scope.launch(operationError) { withContext(Dispatchers.IO) { queue.firstOrNull()?.let { RecommendationLearning.get(context).selected(it.uri.toString()) } }; onPlayQueue(queue, 0) }; selected = null
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_play)) }
                OutlinedButton(enabled = !busy && collection.isNotEmpty(), onClick = { onCreatePlaylist(label(selected!!), collection); message = context.getString(R.string.personal_collection_saved) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.personal_save_playlist)) }
            }
            TextButton(enabled = !busy && collection.size > 1, onClick = { scope.launch(operationError) {
                busy = true
                try { collection = withContext(Dispatchers.IO) { val ledger = ListeningLedger.get(context); PersonalCollections.reorder(collection, collection.mapNotNull { song -> ledger.feature(song.uri.toString())?.let { song.uri.toString() to it } }.toMap()).also { ordered ->
                    sheetSnapshot = RecommendationShelf.create(context, selected!!, genre, ordered.map { it.uri.toString() }, System.nanoTime())
                } } }
                finally { busy = false }
            } }) { Text(stringResource(R.string.personal_smart_reorder)) }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (!busy && collection.isEmpty()) Text(stringResource(R.string.personal_collection_empty), Modifier.padding(vertical = 20.dp))
            LazyColumn(Modifier.heightIn(max = 330.dp)) {
                items(collection, key = { it.uri.toString() }) { song ->
                    ListItem(headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(song.artist, maxLines = 1) },
                        leadingContent = { chromahub.rhythm.app.util.M3ImageUtils.M3MediaImage(data = song.artworkUri, contentDescription = null, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(8.dp), type = chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType.TRACK, name = song.title) }, modifier = Modifier.clickable {
                            val queue = collection.toList(); val index = queue.indexOf(song)
                            scope.launch(operationError) { withContext(Dispatchers.IO) { RecommendationLearning.get(context).selected(song.uri.toString()) }; onPlayQueue(queue, index) }; selected = null
                        })
                }
            }
        }
    }
    if (drawer) ModalBottomSheet(onDismissRequest = { drawer = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(context.getString(R.string.personal_mix_shelf), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
        Text(context.getString(R.string.personal_mix_shelf_hint), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp).navigationBarsPadding()) {
            items(shelf, key = { it.id }) { item ->
                ListItem(headlineContent = { Text(label(item.mode)) }, supportingContent = {
                    Text(java.time.Instant.ofEpochMilli(item.createdAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm")) + " · " + item.songKeys.size)
                }, leadingContent = { Icon(MaterialSymbolIcon("queue_music"), null, Modifier.size(24.dp)) }, modifier = Modifier.clickable {
                    sheetSnapshot = item; selected = item.mode; genre = item.genre; sheetRefresh++; drawer = false
                })
            }
        }
    }

}
