package `in`.jandwar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.jandwar.app.BuildConfig
import `in`.jandwar.app.ui.components.BrandHeader
import `in`.jandwar.app.ui.components.InfoBanner
import `in`.jandwar.app.ui.components.KeyValueRow
import `in`.jandwar.app.ui.components.OptionTile
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.components.SectionTitle
import `in`.jandwar.app.ui.theme.BrandIndigo
import `in`.jandwar.app.ui.theme.BrandTeal
import `in`.jandwar.app.ui.theme.Error
import `in`.jandwar.app.ui.theme.Success
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onLanguageChange: () -> Unit
) {
    val roles by viewModel.allRoles.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrandHeader(
            title = viewModel.tr("settings"),
            subtitle = viewModel.tr("settings_sub"),
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Language ────────────────────────────────────────────────────
            item {
                SettingRow(
                    icon = Icons.Rounded.Translate,
                    tint = BrandTeal,
                    title = viewModel.tr("change_lang"),
                    subtitle = viewModel.availableLanguages()
                        .firstOrNull { it.first == viewModel.currentLang }?.second
                        ?: viewModel.currentLang,
                    onClick = onLanguageChange
                )
            }

            // ── AI status ───────────────────────────────────────────────────
            item {
                PremiumCard(accent = Success) {
                    SectionTitle(viewModel.tr("ai_status"))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CloudOff,
                            null,
                            tint = Success,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.width(11.dp))
                        Text(
                            viewModel.tr("ai_device_on"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    InfoBanner(
                        text = viewModel.tr("ai_enhanced_off"),
                        tone = Success
                    )
                }
            }

            // ── Microphone cue ──────────────────────────────────────────────
            item {
                PremiumCard {
                    SectionTitle(viewModel.tr("mic_cue"))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        viewModel.tr("mic_cue_sub"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(11.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf(true to viewModel.tr("on"), false to viewModel.tr("off"))
                            .forEach { (value, label) ->
                                OptionTile(
                                    title = label,
                                    selected = viewModel.micCue == value,
                                    onClick = { viewModel.updateMicCue(value) }
                                )
                            }
                    }
                }
            }

            // ── Voice engine ────────────────────────────────────────────────
            item {
                PremiumCard {
                    SectionTitle(viewModel.tr("voice_engine"))
                    Spacer(Modifier.height(11.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf(
                            "auto" to viewModel.tr("engine_auto"),
                            "device" to viewModel.tr("engine_device")
                        ).forEach { (key, label) ->
                            OptionTile(
                                title = label,
                                selected = viewModel.ttsEngine == key,
                                onClick = { viewModel.updateTtsEngine(key) }
                            )
                        }
                    }
                }
            }

            // ── Offline catalogue ───────────────────────────────────────────
            item {
                PremiumCard(accent = BrandIndigo) {
                    SectionTitle(viewModel.tr("offline_data"))
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Storage,
                            null,
                            tint = BrandIndigo,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            viewModel.tr("offline_data_text"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    KeyValueRow(viewModel.tr("stat_roles"), roles.size.toString())
                    KeyValueRow(viewModel.tr("stat_sectors"), viewModel.sectors().size.toString())
                    KeyValueRow(viewModel.tr("stat_fundable"), viewModel.fundableCount().toString())
                    KeyValueRow(viewModel.tr("stat_states"), viewModel.stateCount().toString())
                    KeyValueRow(
                        viewModel.tr("stat_districts"),
                        viewModel.districtCount().toString()
                    )
                    // Centres, not districts-with-a-centre: the old row read
                    // 185 under a "centres" label when there are 660.
                    KeyValueRow(
                        viewModel.tr("stat_centres"),
                        "${viewModel.centreCount()} (${viewModel.confirmedCentreCount()} " +
                                "${viewModel.tr("confirmed")})"
                    )
                }
            }

            // ── Privacy ─────────────────────────────────────────────────────
            item {
                PremiumCard(accent = Success) {
                    SectionTitle(viewModel.tr("privacy"))
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.Lock,
                            null,
                            tint = Success,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            viewModel.tr("privacy_text"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Reset ───────────────────────────────────────────────────────
            item {
                SettingRow(
                    icon = Icons.Rounded.DeleteSweep,
                    tint = Error,
                    title = viewModel.tr("reset_profile"),
                    subtitle = viewModel.tr("privacy_text"),
                    onClick = { confirmReset = true }
                )
            }

            // ── About ───────────────────────────────────────────────────────
            item {
                PremiumCard {
                    SectionTitle(viewModel.tr("about"))
                    Spacer(Modifier.height(9.dp))
                    KeyValueRow(viewModel.tr("version"), BuildConfig.VERSION_NAME)
                    KeyValueRow(viewModel.tr("scheme"), "PM-AJAY · GIA component")
                    KeyValueRow("SIH 2026", "Problem statement 26097")
                    Spacer(Modifier.height(11.dp))
                    Text(
                        viewModel.tr("not_claim"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Text(
                    "${viewModel.tr("name")} · ${viewModel.tr("tagline")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(viewModel.tr("reset_profile")) },
            text = { Text(viewModel.tr("reset_profile_what")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearProfile()
                    confirmReset = false
                }) { Text(viewModel.tr("done"), color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(viewModel.tr("close"))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
