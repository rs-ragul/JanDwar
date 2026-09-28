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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import `in`.jandwar.app.ui.components.BrandHeader
import `in`.jandwar.app.ui.components.CircleIconButton
import `in`.jandwar.app.ui.components.InfoBanner
import `in`.jandwar.app.ui.components.SectionTitle
import `in`.jandwar.app.ui.components.StatTile
import `in`.jandwar.app.ui.theme.BrandIndigo
import `in`.jandwar.app.ui.theme.BrandSaffron
import `in`.jandwar.app.ui.theme.BrandTeal
import `in`.jandwar.app.ui.theme.Success
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNavigateIntake: () -> Unit,
    onNavigateCourses: () -> Unit,
    onNavigateVoice: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val allRoles by viewModel.allRoles.collectAsState()
    val roleCount = allRoles.size
    val fundable = remember(roleCount) { viewModel.fundableCount() }
    val districts = remember(roleCount) { viewModel.getDistricts().size }
    val centres = remember(roleCount) { viewModel.districtsWithCentre().size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrandHeader(
            title = viewModel.tr("name"),
            subtitle = viewModel.tr("tagline"),
            showEmblem = true,
            trailing = {
                CircleIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = viewModel.tr("settings"),
                    onClick = onNavigateSettings
                )
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = viewModel.tr("home_greeting"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = viewModel.tr("home_title"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            item { AiStatusPill(viewModel) }

            // Hero action — the PS's primary journey.
            item {
                HeroActionCard(
                    title = viewModel.tr("card_voice_title"),
                    subtitle = viewModel.tr("card_voice_sub"),
                    action = viewModel.tr("card_voice_action"),
                    icon = Icons.Rounded.Mic,
                    onClick = onNavigateVoice
                )
            }

            item {
                ActionCard(
                    title = viewModel.tr("card_form_title"),
                    subtitle = viewModel.tr("card_form_sub"),
                    action = viewModel.tr("card_form_action"),
                    icon = Icons.Rounded.TouchApp,
                    tint = BrandSaffron,
                    onClick = onNavigateIntake
                )
            }

            item {
                ActionCard(
                    title = viewModel.tr("card_browse_title"),
                    subtitle = viewModel.tr("card_browse_sub"),
                    action = viewModel.tr("card_browse_action"),
                    icon = Icons.Rounded.MenuBook,
                    tint = BrandIndigo,
                    onClick = onNavigateCourses
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle(viewModel.tr("offline_data"))
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = roleCount.toString(),
                        label = viewModel.tr("stat_roles"),
                        tint = BrandIndigo,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = fundable.toString(),
                        label = viewModel.tr("stat_fundable"),
                        tint = Success,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = districts.toString(),
                        label = viewModel.tr("stat_districts"),
                        tint = BrandTeal,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = centres.toString(),
                        label = viewModel.tr("stat_centres"),
                        tint = BrandSaffron,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(Modifier.height(2.dp))
                InfoBanner(
                    text = viewModel.tr("honesty_note"),
                    icon = Icons.Rounded.VerifiedUser,
                    tone = Success
                )
            }
        }
    }
}

@Composable
private fun AiStatusPill(viewModel: AppViewModel) {
    val cloud = viewModel.aiReady
    val tone = if (cloud) BrandTeal else Success
    Surface(
        shape = RoundedCornerShape(50),
        color = tone.copy(alpha = 0.11f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (cloud) Icons.Rounded.CloudDone else Icons.Rounded.OfflineBolt,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = viewModel.tr(if (cloud) "ai_cloud_on" else "ai_device_on"),
                style = MaterialTheme.typography.labelMedium,
                color = tone
            )
        }
    }
}

/** Large gradient card for the app's headline action. */
@Composable
private fun HeroActionCard(
    title: String,
    subtitle: String,
    action: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(listOf(BrandIndigo, BrandTeal))
            )
        ) {
            // Decorative bloom — matchParentSize() so it never forces the
            // card taller than its content.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            radius = 520f
                        )
                    )
            )
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    Spacer(Modifier.height(11.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.22f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                action,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    action: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(tint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    action,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
