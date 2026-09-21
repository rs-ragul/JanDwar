package com.thozhilthunai.app.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.thozhilthunai.app.R
import com.thozhilthunai.app.ui.AppViewModel
import com.thozhilthunai.app.ui.components.ThozhilCard
import com.thozhilthunai.app.ui.components.brandGradient
import com.thozhilthunai.app.ui.theme.BrandSaffron
import com.thozhilthunai.app.ui.theme.BrandTeal

@Composable
fun HomeScreen(
    appViewModel: AppViewModel,
    onFindCourse: () -> Unit,
    onSettings: () -> Unit
) {
    val lang by appViewModel.currentLang.collectAsState()
    val dataLoaded by appViewModel.dataLoaded.collectAsState()

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600), label = "homeAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(brush = brandGradient)
                    .padding(start = 20.dp, end = 8.dp, top = 44.dp, bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_premium),
                            contentDescription = "ThozhilThunai Logo",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column {
                            Text(
                                text = appViewModel.str("name"),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = appViewModel.str("tagline"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(20.dp)
                .alpha(alpha),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Primary CTA card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BrandSaffron),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎯",
                        style = MaterialTheme.typography.displaySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = appViewModel.str("submit"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = appViewModel.str("found"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.9f)
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onFindCourse,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = BrandSaffron
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = appViewModel.str("submit"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Quick stats row
            if (dataLoaded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "📚",
                        count = appViewModel.totalRoles().toString(),
                        label = "Job Roles"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "🏫",
                        count = appViewModel.totalCentres().toString(),
                        label = "Training Centres"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        emoji = "🗺️",
                        count = "38",
                        label = "TN Districts"
                    )
                }
            }

            // Disclaimer card
            ThozhilCard {
                Text(
                    text = "ℹ️ " + appViewModel.str("no_centre"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, emoji: String, count: String, label: String) {
    ThozhilCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = emoji, style = MaterialTheme.typography.titleLarge)
            Text(
                text = count,
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = BrandTeal,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
