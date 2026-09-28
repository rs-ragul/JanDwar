package `in`.jandwar.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import `in`.jandwar.app.ai.ConversationEngine
import `in`.jandwar.app.data.model.EducationLevel
import `in`.jandwar.app.ui.components.Badge
import `in`.jandwar.app.ui.components.BrandHeader
import `in`.jandwar.app.ui.components.CircleIconButton
import `in`.jandwar.app.ui.components.GhostButton
import `in`.jandwar.app.ui.components.PrimaryButton
import `in`.jandwar.app.ui.components.StepProgress
import `in`.jandwar.app.ui.components.VoiceOrb
import `in`.jandwar.app.ui.theme.BrandIndigo
import `in`.jandwar.app.ui.theme.BrandSaffron
import `in`.jandwar.app.ui.theme.BrandTeal
import `in`.jandwar.app.ui.theme.Success
import `in`.jandwar.app.ui.theme.Warning
import `in`.jandwar.app.ui.viewmodel.AppViewModel
import `in`.jandwar.app.ui.viewmodel.VoiceViewModel

@Composable
fun VoiceScreen(
    appViewModel: AppViewModel,
    voiceViewModel: VoiceViewModel,
    onClose: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val state by voiceViewModel.state.collectAsState()
    val lang = appViewModel.currentLang

    val initiallyGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }
    var micGranted by remember { mutableStateOf(initiallyGranted) }

    // The interview only opens once we know whether the mic is usable, so the
    // user never sees a "permission denied" banner flash before answering.
    var permissionResolved by remember { mutableStateOf(initiallyGranted) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        permissionResolved = true
        voiceViewModel.onMicPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        if (!initiallyGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(permissionResolved) {
        if (!permissionResolved) return@LaunchedEffect
        voiceViewModel.start(lang, micGranted) { profile ->
            appViewModel.completeInterview(profile) { results ->
                // The summary is spoken *and* shown as an assistant turn; the
                // move to the results page is driven by navigateToResults
                // below, once the reading finishes or the user skips it.
                voiceViewModel.narrateResults(
                    results = results,
                    profileSummary = profile.summaryLine()
                ) { }
            }
        }
    }

    // Single exit point, so finishing the narration and tapping
    // "See my results" can never both navigate.
    var navigated by remember { mutableStateOf(false) }
    LaunchedEffect(voiceViewModel.navigateToResults) {
        if (voiceViewModel.navigateToResults && !navigated) {
            navigated = true
            appViewModel.updateResultNarration(voiceViewModel.narration)
            voiceViewModel.consumeNavigation()
            onDone()
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceViewModel.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        BrandHeader(
            title = appViewModel.tr("voice_title"),
            subtitle = appViewModel.tr("voice_sub"),
            trailing = {
                CircleIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = appViewModel.tr("close"),
                    onClick = {
                        voiceViewModel.stop()
                        onClose()
                    }
                )
            }
        )

        // ── Progress + understood-so-far ────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            StepProgress(
                current = (state.progress * 7).toInt(),
                total = 7
            )
            Spacer(Modifier.height(10.dp))
            UnderstoodRow(appViewModel, state)
        }

        // ── Orb + status ────────────────────────────────────────────────────
        val speaking = state.phase == ConversationEngine.Phase.SPEAKING ||
                state.phase == ConversationEngine.Phase.SUMMARY
        // The orb and the button must follow the microphone itself, not the
        // phase: the phase flips to LISTENING a moment before the recogniser
        // actually opens, which is why the button used to lie.
        val listening = state.micLive
        val thinking = state.phase == ConversationEngine.Phase.THINKING
        val summarising = state.phase == ConversationEngine.Phase.SUMMARY

        val orbScale by animateFloatAsState(
            targetValue = 1f + (state.amplitude * 0.10f),
            animationSpec = tween(110),
            label = "orbScale"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            VoiceOrb(
                isSpeaking = speaking,
                isListening = listening,
                size = 96.dp,
                modifier = Modifier.scale(orbScale)
            )
        }

        // Status line only — the words themselves live in the transcript
        // below, so nothing is ever printed twice.
        Text(
            text = when {
                speaking -> appViewModel.tr("speaking")
                state.micLive -> appViewModel.tr("listening")
                thinking -> appViewModel.tr("thinking")
                summarising -> appViewModel.tr("summary_wait")
                state.phase == ConversationEngine.Phase.DONE -> appViewModel.tr("done")
                else -> appViewModel.tr("tap_to_speak")
            },
            style = MaterialTheme.typography.labelLarge,
            color = when {
                state.micLive -> BrandTeal
                speaking -> BrandSaffron
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp)
        )

        // ── Notice banner ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = !state.notice.isNullOrBlank(),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Warning.copy(alpha = 0.11f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    null,
                    tint = Warning,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    state.notice.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (state.micPermissionNeeded) {
                    Spacer(Modifier.width(8.dp))
                    GhostButton(
                        text = appViewModel.tr("grant_mic"),
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        }

        // ── Transcript ──────────────────────────────────────────────────────
        val listState = rememberLazyListState()
        LaunchedEffect(state.turns.size, state.partial) {
            val target = state.turns.size
            if (target > 0) listState.animateScrollToItem(target - 1)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(state.turns.size) { index ->
                val turn = state.turns[index]
                val isLastAssistant = !turn.fromUser &&
                        index == state.turns.indexOfLast { !it.fromUser }
                Bubble(
                    text = turn.text,
                    fromUser = turn.fromUser,
                    label = if (turn.fromUser) appViewModel.tr("you")
                    else appViewModel.tr("name"),
                    // The line currently coming out of the speaker is framed
                    // and tagged instead of being repeated in a second card.
                    highlight = isLastAssistant && speaking,
                    statusLabel = if (isLastAssistant && speaking) {
                        appViewModel.tr("speaking")
                    } else null
                )
            }
            if (state.partial.isNotBlank()) {
                items(1) {
                    Bubble(
                        text = state.partial,
                        fromUser = true,
                        label = appViewModel.tr("you"),
                        ghost = true
                    )
                }
            }
        }

        // ── Controls ────────────────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .navigationBarsPadding()
            ) {
                when {
                    // Matching is done and the assistant is reading the
                    // summary. Stay here so it can be read on screen; the
                    // user may jump ahead at any time.
                    summarising || state.phase == ConversationEngine.Phase.DONE -> {
                        PrimaryButton(
                            text = appViewModel.tr("see_results"),
                            icon = Icons.Rounded.Check,
                            loading = voiceViewModel.isNarrating,
                            onClick = { voiceViewModel.skipNarration() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    state.inputMode == ConversationEngine.InputMode.TEXT -> {
                        TextAnswerBar(appViewModel, voiceViewModel, micAvailable = micGranted)
                    }

                    else -> {
                        VoiceControlBar(
                            appViewModel = appViewModel,
                            voiceViewModel = voiceViewModel,
                            listening = listening,
                            speaking = speaking,
                            thinking = thinking,
                            busy = speaking || thinking
                        )
                    }
                }

                if (state.canFinishEarly &&
                    state.phase != ConversationEngine.Phase.DONE &&
                    !summarising
                ) {
                    Spacer(Modifier.height(10.dp))
                    GhostButton(
                        text = appViewModel.tr("submit"),
                        icon = Icons.Rounded.Check,
                        onClick = { voiceViewModel.finishEarly() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Pieces ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnderstoodRow(
    appViewModel: AppViewModel,
    state: ConversationEngine.State
) {
    val p = state.profile
    val chips = buildList {
        p.edu?.let { raw ->
            val level = EducationLevel.fromAiString(raw)
            add(if (level != null) appViewModel.tr(level.key) else raw)
        }
        p.district?.let { add(it) }
        p.familyOccupation?.let { add(appViewModel.occupationLabel(it)) }
        p.currentLivelihood?.let { add(appViewModel.occupationLabel(it)) }
        p.interests.forEach { add(appViewModel.interestLabel(it)) }
        p.preference?.let { add(appViewModel.tr(it)) }
        p.mobility?.let { raw ->
            // The NLU emits local / district / state; "any" is the legacy
            // spelling of the widest option. Map every variant onto a real
            // key so a raw slot value can never leak into the chip.
            val key = when (val m = raw.removePrefix("travel_").lowercase()) {
                "local" -> "travel_local"
                "district" -> "travel_district"
                "state", "any" -> "travel_state"
                else -> "travel_$m"
            }
            add(appViewModel.tr(key))
        }
    }.filter { it.isNotBlank() }
        // Free-text answers ("my father works in a government office in
        // Dindigul") arrive verbatim; a chip is not a paragraph.
        .map { if (it.length > 26) it.take(24).trimEnd() + "\u2026" else it }
        .distinctBy { it.lowercase() }

    if (chips.isEmpty()) return

    Column {
        Text(
            appViewModel.tr("profile_so_far"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(7.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chips.take(8).forEach { chip ->
                Badge(text = chip, color = Success, icon = Icons.Rounded.Check)
            }
        }
    }
}

@Composable
private fun Bubble(
    text: String,
    fromUser: Boolean,
    label: String,
    ghost: Boolean = false,
    highlight: Boolean = false,
    statusLabel: String? = null
) {
    val bg = when {
        ghost -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        fromUser -> BrandIndigo
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        ghost -> MaterialTheme.colorScheme.onSurfaceVariant
        fromUser -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (highlight) {
                    Icon(
                        Icons.Rounded.VolumeUp,
                        null,
                        tint = BrandSaffron,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!statusLabel.isNullOrBlank()) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandSaffron
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (fromUser) 18.dp else 5.dp,
                    bottomEnd = if (fromUser) 5.dp else 18.dp
                ),
                color = if (highlight) BrandSaffron.copy(alpha = 0.09f) else bg,
                border = when {
                    highlight -> androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        BrandSaffron.copy(alpha = 0.65f)
                    )

                    !fromUser && !ghost -> androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline
                    )

                    else -> null
                }
            ) {
                Text(
                    text,
                    style = if (highlight) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodyMedium,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceControlBar(
    appViewModel: AppViewModel,
    voiceViewModel: VoiceViewModel,
    listening: Boolean,
    speaking: Boolean,
    thinking: Boolean,
    busy: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = { voiceViewModel.useTextInput() },
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                Icons.Rounded.Keyboard,
                contentDescription = appViewModel.tr("type_hint"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PrimaryButton(
            // Never invite a tap while the assistant is mid-sentence.
            text = when {
                speaking -> appViewModel.tr("speaking")
                thinking -> appViewModel.tr("thinking")
                listening -> appViewModel.tr("listening")
                else -> appViewModel.tr("tap_to_speak")
            },
            icon = Icons.Rounded.Mic,
            loading = busy,
            onClick = { voiceViewModel.listenNow() },
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = { voiceViewModel.repeatLast() },
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                Icons.Rounded.Replay,
                contentDescription = appViewModel.tr("repeat"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TextAnswerBar(
    appViewModel: AppViewModel,
    voiceViewModel: VoiceViewModel,
    micAvailable: Boolean
) {
    var draft by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    val send = {
        if (draft.isNotBlank()) {
            voiceViewModel.submitText(draft)
            draft = ""
            keyboard?.hide()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (micAvailable) {
            IconButton(
                onClick = { voiceViewModel.useVoiceInput() },
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(BrandTeal.copy(alpha = 0.14f))
            ) {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = appViewModel.tr("tap_to_speak"),
                    tint = BrandTeal
                )
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 54.dp),
            placeholder = { Text(appViewModel.tr("type_hint")) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        IconButton(
            onClick = send,
            enabled = draft.isNotBlank(),
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(
                    if (draft.isNotBlank()) BrandIndigo
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Icon(
                Icons.Rounded.Send,
                contentDescription = appViewModel.tr("send"),
                tint = if (draft.isNotBlank()) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
