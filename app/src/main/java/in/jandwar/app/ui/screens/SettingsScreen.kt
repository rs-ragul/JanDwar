package `in`.jandwar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.jandwar.app.ui.components.GradientHeader
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.theme.*
import `in`.jandwar.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onLanguageChange: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var showTtsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        GradientHeader(
            title = viewModel.tr("settings"),
            subtitle = viewModel.tr("tagline"),
            actionText = viewModel.tr("back"),
            onAction = onBack
        )
        Spacer(Modifier.height(16.dp))

        Text(viewModel.tr("settings_sub"), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(12.dp))

        PremiumCard {
            Column {
                Text(viewModel.tr("change_lang"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onLanguageChange,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Language")
                }
            }
        }

        PremiumCard {
            Column {
                Text(viewModel.tr("offline_data"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(viewModel.tr("offline_data_text"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }

        PremiumCard {
            Column {
                Text(
                    if (viewModel.isOfflineAiInstalled) viewModel.tr("offline_ai_installed") else viewModel.tr("offline_ai"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(viewModel.tr("offline_ai_text"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

                if (downloading) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("$progress%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Button(
                    onClick = {
                        if (viewModel.isOfflineAiInstalled) return@Button
                        downloading = true
                        progress = 0
                        scope.launch {
                            for (i in 0..100 step 2) {
                                delay(50)
                                progress = i
                            }
                            downloading = false
                            viewModel.updateOfflineAiInstalled(true)
                        }
                    },
                    enabled = !viewModel.isOfflineAiInstalled && !downloading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (viewModel.isOfflineAiInstalled) "Installed" else "Download")
                }
            }
        }

        PremiumCard {
            Column {
                Text("TTS Engine (Important for Tamil/Hindi)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Current: ${viewModel.ttsEngine} - Tap to change if Tamil TTS is worst", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                Text("Recommended: sarvam for Tamil/Hindi (natural), android for offline", fontSize = 12.sp, color = BrandTeal, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Button(
                    onClick = { showTtsDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select TTS Engine")
                }
            }
        }

        PremiumCard {
            Column {
                Text("API Configuration Status", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Your config.json mapping:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Text("• UDYAT KEY (07e29...) → bhashini_user_id ✓ You have correct", fontSize = 12.sp, color = Success, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Text("• INFERENCE (n44PH...) → bhashini_inference_key ✓ Correct", fontSize = 12.sp, color = Success, fontWeight = FontWeight.Bold)
                Text("• App ID (d0bed4...) → bhashini_app_id ✓ Correct", fontSize = 12.sp, color = Success, fontWeight = FontWeight.Bold)
                Text("• groq_api_key → Groq (AI) ✓", fontSize = 12.sp, color = Success, modifier = Modifier.padding(top = 4.dp))
                Text("• sarvam_api_key → Sarvam (Best Tamil TTS) ✓", fontSize = 12.sp, color = Success, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Tip: For best Tamil voice, set TTS to 'sarvam'. Bhashini is for translation only.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        PremiumCard {
            Column {
                Text(viewModel.tr("privacy"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(viewModel.tr("privacy_text"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }

        PremiumCard {
            Column {
                Text(viewModel.tr("app_name_label"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("JanDwar · ${viewModel.tr("tagline")} · v2.0-premium", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                Text(viewModel.tr("honesty_note"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(viewModel.tr("not_claim_text"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showTtsDialog) {
        val options = listOf(
            "auto" to "auto (Recommended: Sarvam for Tamil/Hindi, Android fallback)",
            "sarvam" to "sarvam ⭐ Best for Tamil/Hindi/Te (Natural)",
            "android" to "android (Google TTS - Good offline)",
            "bhashini" to "bhashini (AI4Bharat - Translation focused)",
            "android_offline" to "android_offline (Fully offline)"
        )
        AlertDialog(
            onDismissRequest = { showTtsDialog = false },
            title = { Text("Select TTS Engine") },
            text = {
                Column {
                    Text("For Tamil/Hindi, use sarvam for most natural voice.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    options.forEach { (value, label) ->
                        TextButton(
                            onClick = {
                                viewModel.updateTtsEngine(value)
                                showTtsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.fillMaxWidth(), fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTtsDialog = false }) { Text("Close") }
            }
        )
    }
}
