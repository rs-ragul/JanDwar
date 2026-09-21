package com.thozhilthunai.app.ui.screens.language

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thozhilthunai.app.ui.AppViewModel
import com.thozhilthunai.app.ui.components.brandGradient
import com.thozhilthunai.app.ui.theme.BrandIndigo
import com.thozhilthunai.app.ui.theme.BrandSaffron
import com.thozhilthunai.app.ui.theme.BrandTeal
import com.thozhilthunai.app.ui.theme.ThozhilThunaiTheme

data class LanguageOption(val code: String, val nativeName: String, val emoji: String)

val LANGUAGES = listOf(
    LanguageOption("ta", "தமிழ்", "🌟"),
    LanguageOption("en", "English", "🌍"),
    LanguageOption("hi", "हिन्दी", "🇮🇳"),
    LanguageOption("te", "తెలుగు", "⭐"),
    LanguageOption("kn", "ಕನ್ನಡ", "🌸"),
    LanguageOption("ml", "മലയാളം", "🌴")
)

@Composable
fun LanguageScreen(
    appViewModel: AppViewModel,
    onLanguageSelected: () -> Unit
) {
    var selectedCode by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = brandGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            Text(
                text = "தொழில்துணை",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose your language · மொழியைத் தேர்ந்தெடுக்கவும்",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.75f)),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(LANGUAGES) { lang ->
                    LanguageTile(
                        lang = lang,
                        selected = selectedCode == lang.code,
                        onClick = { selectedCode = lang.code }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    selectedCode?.let { code ->
                        appViewModel.setLanguage(code)
                        onLanguageSelected()
                    }
                },
                enabled = selectedCode != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandSaffron,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (selectedCode != null) "✓ Continue" else "Select a language",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LanguageTile(
    lang: LanguageOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(), label = "langTileScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) Color.White else Color.White.copy(alpha = 0.15f)
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) BrandSaffron else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = lang.emoji, fontSize = 32.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = lang.nativeName,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (selected) BrandIndigo else Color.White,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview
@Composable
private fun LanguagePreview() {
    ThozhilThunaiTheme { /* requires AppViewModel */ }
}
