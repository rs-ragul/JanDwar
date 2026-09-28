package `in`.jandwar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.jandwar.app.ui.components.GradientHeader
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.components.StatCard
import `in`.jandwar.app.ui.theme.*
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNavigateIntake: () -> Unit,
    onNavigateCourses: () -> Unit,
    onNavigateVoice: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        GradientHeader(
            title = viewModel.tr("name"),
            subtitle = viewModel.tr("tagline"),
            actionText = viewModel.tr("settings"),
            onAction = onNavigateSettings,
            withLogo = true
        )

        Spacer(Modifier.height(16.dp))

        PremiumCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = BrandSaffron,
                    modifier = Modifier.size(62.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(viewModel.tr("voice_title"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(viewModel.tr("voice_sub"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = onNavigateVoice,
                    label = { Text(viewModel.tr("speak_button"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(viewModel.tr("choose_path"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(2.dp))

        PremiumCard {
            Column {
                Text(viewModel.tr("personalized_title"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(viewModel.tr("personalized_sub"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                TextButton(onClick = onNavigateIntake) {
                    Text(viewModel.tr("start_intake") + "  ->", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        PremiumCard {
            Column {
                Text(viewModel.tr("browse_title"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(viewModel.tr("browse_sub"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                TextButton(onClick = onNavigateCourses) {
                    Text(viewModel.tr("browse_action") + "  ->", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        StatCard("516", viewModel.tr("stat_roles"), "343", viewModel.tr("stat_fundable"))
        Spacer(Modifier.height(8.dp))
        StatCard("38", viewModel.tr("stat_districts"), "20", viewModel.tr("stat_centres"))

        Spacer(Modifier.height(12.dp))
        PremiumCard {
            Text(viewModel.tr("honesty_note"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
