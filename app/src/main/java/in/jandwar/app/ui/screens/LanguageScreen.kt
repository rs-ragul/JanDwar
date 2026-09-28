package `in`.jandwar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.jandwar.app.ui.components.GradientHeader
import `in`.jandwar.app.ui.theme.*
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@Composable
fun LanguageScreen(
    viewModel: AppViewModel,
    onLanguageSelected: () -> Unit
) {
    val langs = listOf(
        "en" to "English",
        "ta" to "தமிழ்",
        "hi" to "हिन्दी",
        "te" to "తెలుగు",
        "kn" to "ಕನ್ನಡ",
        "ml" to "മലയാളം"
    )

    val englishNames = mapOf(
        "en" to "English",
        "ta" to "Tamil",
        "hi" to "Hindi",
        "te" to "Telugu",
        "kn" to "Kannada",
        "ml" to "Malayalam"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        GradientHeader(
            title = "JanDwar",
            subtitle = "Choose Language",
            withLogo = true
        )
        Spacer(Modifier.height(22.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(langs) { (code, label) ->
                val isSelected = viewModel.currentLang == code
                Card(
                    onClick = {
                        viewModel.setLanguage(code)
                        onLanguageSelected()
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) BrandTeal.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) BrandTeal else BorderLight
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 5.dp),
                    modifier = Modifier.height(124.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            label,
                            fontSize = if (code == "en") 24.sp else 27.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        Text(
                            englishNames[code] ?: code,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "You can change this anytime from Settings.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}
