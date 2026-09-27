package `in`.jandwar.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.jandwar.app.data.model.ConversationMessage
import `in`.jandwar.app.ui.components.GradientHeader
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.theme.*
import `in`.jandwar.app.ui.viewmodel.AppViewModel
import `in`.jandwar.app.ui.viewmodel.VoiceViewModel

@Composable
fun VoiceScreen(
    appViewModel: AppViewModel,
    voiceViewModel: VoiceViewModel,
    onClose: () -> Unit,
    onDone: () -> Unit
) {
    var typedText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        voiceViewModel.start(
            lang = appViewModel.currentLang.ifBlank { "en" },
            onFieldExtracted = { frag ->
                appViewModel.applyProfileFragment(frag)
            },
            onDone = { final ->
                appViewModel.applyProfileFragment(final)
                appViewModel.runMatching()
                onDone()
            }
        )
    }

    LaunchedEffect(voiceViewModel.conversationHistory.size) {
        if (voiceViewModel.conversationHistory.isNotEmpty()) {
            listState.animateScrollToItem(voiceViewModel.conversationHistory.size - 1)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceViewModel.stop()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceViewModel.isSpeaking) 1.18f else if (voiceViewModel.isListening) 1.10f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GradientHeader(
            title = appViewModel.tr("voice_intro"),
            subtitle = "JanDwar - your trusted companion for livelihood mapping",
            actionText = appViewModel.tr("close"),
            onAction = {
                voiceViewModel.stop()
                onClose()
            }
        )

        Spacer(Modifier.height(12.dp))

        // Pulsing orb - empathetic presence, shows listening vs speaking
        Box(
            modifier = Modifier
                .size((110 * scale).dp)
                .clip(CircleShape)
                .background(
                    when {
                        voiceViewModel.isSpeaking -> Brush.radialGradient(listOf(BrandSaffron, BrandIndigo))
                        voiceViewModel.isListening -> Brush.radialGradient(listOf(BrandTeal, BrandIndigo))
                        else -> Brush.radialGradient(listOf(BrandTeal.copy(alpha = 0.7f), BrandIndigo.copy(alpha = 0.7f)))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    voiceViewModel.isSpeaking -> "🔊"
                    voiceViewModel.isListening -> "🎙️"
                    else -> "●"
                },
                color = Color.White.copy(alpha = 0.9f),
                fontSize = if (voiceViewModel.isListening || voiceViewModel.isSpeaking) 24.sp else 28.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        // Status - never show technical errors, show friendly state
        val statusText = voiceViewModel.status
        val isErrorStatus = statusText.lowercase().contains("didn't catch") || statusText.lowercase().contains("tap to speak")
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isErrorStatus -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    voiceViewModel.isListening -> BrandTeal.copy(alpha = 0.12f)
                    voiceViewModel.isSpeaking -> BrandSaffron.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isErrorStatus -> MaterialTheme.colorScheme.onErrorContainer
                    voiceViewModel.isListening -> BrandTeal
                    voiceViewModel.isSpeaking -> BrandSaffron
                    else -> Muted
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Conversation history - chat bubbles
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(voiceViewModel.conversationHistory) { msg ->
                // Filter out empty or very short user messages that are likely errors
                if (msg.text.isNotBlank() && msg.text.length > 2) {
                    ConversationBubble(msg)
                }
            }

            // Current question as assistant bubble if not in history yet
            item {
                val q = voiceViewModel.currentQuestion
                val lowerQ = q.lowercase()
                val isTechnicalError = lowerQ.contains("error: groq") || (lowerQ.contains("model") && lowerQ.contains("does not exist")) || lowerQ.contains("groq api") || q.length > 500
                if (q.isNotBlank() && !isTechnicalError) {
                    val isAlreadyInHistory = voiceViewModel.conversationHistory.lastOrNull()?.text == q
                    if (!isAlreadyInHistory) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(3.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                q,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Profile summary chips - shows what AI understood
        if (appViewModel.profile.isPartiallyComplete()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                appViewModel.profile.education?.let {
                    AssistChip(onClick = {}, label = { Text(it.name, fontSize = 11.sp) })
                }
                if (appViewModel.profile.familyOccupation.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text("Family: ${appViewModel.profile.familyOccupation.take(15)}", fontSize = 11.sp) })
                }
                if (appViewModel.profile.currentLivelihood.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text("Now: ${appViewModel.profile.currentLivelihood.take(15)}", fontSize = 11.sp) })
                }
                if (appViewModel.profile.interests.isNotEmpty()) {
                    AssistChip(onClick = {}, label = { Text(appViewModel.profile.interests.joinToString(), fontSize = 11.sp) })
                }
            }
        }

        // Typed input - always available as fallback for low-connectivity
        OutlinedTextField(
            value = typedText,
            onValueChange = { typedText = it },
            placeholder = { Text("Type your answer... (or just speak)", fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                TextButton(
                    onClick = {
                        if (typedText.isNotBlank()) {
                            voiceViewModel.processTypedAnswer(typedText)
                            typedText = ""
                        }
                    },
                    enabled = typedText.isNotBlank()
                ) {
                    Text("Send", color = if (typedText.isNotBlank()) BrandTeal else Muted, fontWeight = FontWeight.Bold)
                }
            }
        )

        Spacer(Modifier.height(6.dp))

        Text(
            voiceViewModel.transcript.ifBlank { 
                if (voiceViewModel.isListening) "Listening... speak now" 
                else "JanDwar understands Tamil, Hindi, Telugu, Kannada, Malayalam, English - speak naturally like talking to a friend"
            },
            fontSize = 12.sp,
            color = if (voiceViewModel.isListening) BrandTeal else Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    voiceViewModel.stop()
                    onClose()
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(appViewModel.tr("stop_listening"), fontSize = 14.sp)
            }
            Button(
                onClick = { voiceViewModel.repeatQuestion() },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
            ) {
                Text("Repeat 🔁", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConversationBubble(msg: ConversationMessage) {
    val isUser = msg.isUser
    val isDark = isSystemInDarkTheme()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    if (isDark) Color(0xFF2D3A5C) else BrandIndigo.copy(alpha = 0.12f)
                } else {
                    if (isDark) Color(0xFF1E2A3A) else Color.White
                }
            ),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    if (isUser) "You" else "JanDwar",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) {
                        if (isDark) Color(0xFF8BA0D0) else BrandIndigo
                    } else {
                        BrandTeal
                    }
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    msg.text,
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFFE8EAF0) else Ink,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun isSystemInDarkTheme(): Boolean {
    return androidx.compose.foundation.isSystemInDarkTheme()
}
