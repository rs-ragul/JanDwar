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
            onFieldExtracted = { frag -> appViewModel.applyProfileFragment(frag) },
            onDone = { final ->
                appViewModel.applyProfileFragment(final)
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
        onDispose { voiceViewModel.stop() }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceViewModel.isSpeaking) 1.18f else if (voiceViewModel.isListening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GradientHeader(
            title = appViewModel.tr("voice_intro"),
            subtitle = appViewModel.tr("voice_sub"),
            actionText = appViewModel.tr("close"),
            onAction = {
                voiceViewModel.stop()
                onClose()
            }
        )

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier.size((110 * scale).dp).clip(CircleShape).background(
                when {
                    voiceViewModel.isSpeaking -> Brush.radialGradient(listOf(BrandSaffron, BrandIndigo))
                    voiceViewModel.isListening -> Brush.radialGradient(listOf(BrandTeal, BrandIndigo))
                    else -> Brush.radialGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
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
                color = if (voiceViewModel.isListening || voiceViewModel.isSpeaking) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (voiceViewModel.isListening || voiceViewModel.isSpeaking) 24.sp else 28.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    voiceViewModel.isListening -> BrandTeal.copy(alpha = 0.15f)
                    voiceViewModel.isSpeaking -> BrandSaffron.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (voiceViewModel.isListening) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = BrandTeal)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    voiceViewModel.status,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        voiceViewModel.isListening -> BrandTeal
                        voiceViewModel.isSpeaking -> BrandSaffron
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(voiceViewModel.conversationHistory) { msg ->
                if (msg.text.isNotBlank() && msg.text.length > 2) {
                    ConversationBubble(msg, appViewModel)
                }
            }
            item {
                val q = voiceViewModel.currentQuestion
                val lowerQ = q.lowercase()
                val isTechnicalError = lowerQ.contains("error: groq") || (lowerQ.contains("model") && lowerQ.contains("does not exist")) || lowerQ.contains("groq api") || q.length > 600
                if (q.isNotBlank() && !isTechnicalError) {
                    val isAlreadyInHistory = voiceViewModel.conversationHistory.lastOrNull()?.text == q
                    if (!isAlreadyInHistory) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(3.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(q, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
            if (voiceViewModel.isExplainingResults && voiceViewModel.matchedResults.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🎯 Top Recommendations", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            voiceViewModel.matchedResults.forEachIndexed { idx, role ->
                                Text("${idx + 1}. ${role.role.job_role} (${role.role.qp_code})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(role.skillGapNote, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (appViewModel.profile.isPartiallyComplete()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                appViewModel.profile.education?.let { AssistChip(onClick = {}, label = { Text(it.name, fontSize = 11.sp) }) }
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

        OutlinedTextField(
            value = typedText,
            onValueChange = { typedText = it },
            placeholder = { Text(if (appViewModel.currentLang == "ta") "உங்கள் பதிலை தட்டச்சு செய்யுங்கள்..." else "Type your answer... (or just speak)", fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                TextButton(onClick = { if (typedText.isNotBlank()) { voiceViewModel.processTypedAnswer(typedText); typedText = "" } }, enabled = typedText.isNotBlank()) {
                    Text(appViewModel.tr("send").ifBlank { "Send" }, color = if (typedText.isNotBlank()) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
        )

        Spacer(Modifier.height(6.dp))

        Text(
            voiceViewModel.transcript.ifBlank {
                if (voiceViewModel.isListening) {
                    when (appViewModel.currentLang) {
                        "ta" -> "கேட்கிறேன்... இப்போது பேசுங்கள்"
                        "hi" -> "सुन रहा हूँ... अभी बोलें"
                        "te" -> "వింటున్నాను... ఇప్పుడు మాట్లాడండి"
                        "kn" -> "ಆಲಿಸುತ್ತಿದ್ದೇನೆ... ಈಗ ಮಾತನಾಡಿ"
                        "ml" -> "കേൾക്കുന്നു... ഇപ്പോൾ സംസാരിക്കൂ"
                        else -> "Listening... speak now"
                    }
                } else {
                    when (appViewModel.currentLang) {
                        "ta" -> "தமிழ், ஆங்கிலம், இந்தி - இயல்பாக பேசுங்கள்"
                        "hi" -> "हिंदी, अंग्रेजी, तमिल - स्वाभाविक रूप से बोलें"
                        else -> "Tamil, Hindi, English - speak naturally like talking to a friend"
                    }
                }
            },
            fontSize = 12.sp,
            color = if (voiceViewModel.isListening) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { voiceViewModel.stop(); onClose() }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                Text(appViewModel.tr("stop_listening"), fontSize = 14.sp)
            }
            Button(onClick = { voiceViewModel.repeatQuestion() }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)) {
                Text(if (appViewModel.currentLang == "ta") "மீண்டும்" else "Repeat 🔁", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConversationBubble(msg: ConversationMessage, appViewModel: AppViewModel) {
    val isUser = msg.isUser
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Card(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (isUser) 18.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 18.dp),
            colors = CardDefaults.cardColors(containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(if (isUser) appViewModel.tr("you").ifBlank { "You" } else "JanDwar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else BrandTeal)
                Spacer(Modifier.height(3.dp))
                Text(msg.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 19.sp)
            }
        }
    }
}
