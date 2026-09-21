package com.thozhilthunai.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thozhilthunai.app.ui.AppViewModel
import com.thozhilthunai.app.ui.theme.BrandIndigo
import com.thozhilthunai.app.ui.theme.BrandSaffron
import com.thozhilthunai.app.ui.theme.BrandTeal

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onChangeLanguage: () -> Unit
) {
    val textSizeIndex by appViewModel.textSizeIndex.collectAsState()
    val textSizeLabels = listOf("Small", "Medium", "Large")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language
            SettingsCard(title = appViewModel.str("change_lang")) {
                Button(
                    onClick = onChangeLanguage,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(appViewModel.str("change_lang"))
                }
            }

            // Text size
            SettingsCard(title = "Text Size") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    textSizeLabels.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = textSizeIndex == idx,
                            onClick = { appViewModel.setTextSizeIndex(idx) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // About
            SettingsCard(title = "About") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ThozhilThunai v1.0",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandTeal
                    )
                    Text(
                        text = "Helping rural job-seekers in Tamil Nadu discover skill-training courses — offline, free, in your language.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Data: 516 NSQF Qualification Packs · 20 TANUVAS/VUTRC Centres · 38 TN Districts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Disclaimer
            SettingsCard(title = appViewModel.str("not_claim")) {
                Text(
                    text = "• Course/centre details are indicative only.\n" +
                            "• Confirm eligibility, fees and empanelment with the training centre / TAHDCO / the relevant Skill Council before enrolling.\n" +
                            "• Asset subsidy: up to Rs.50,000 or 50% of asset cost (with loan), whichever is lower.\n" +
                            "• This app does not guarantee placement or admission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
