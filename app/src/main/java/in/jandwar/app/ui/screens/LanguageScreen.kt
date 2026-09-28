package `in`.jandwar.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.jandwar.app.ui.components.BrandHeader
import `in`.jandwar.app.ui.components.InfoBanner
import `in`.jandwar.app.ui.components.PrimaryButton
import `in`.jandwar.app.ui.viewmodel.AppViewModel

/** English hints so a first-time user can find their language before it is set. */
private val LANG_HINT = mapOf(
    "en" to "English",
    "ta" to "Tamil",
    "hi" to "Hindi",
    "te" to "Telugu",
    "kn" to "Kannada",
    "ml" to "Malayalam"
)

@Composable
fun LanguageScreen(
    viewModel: AppViewModel,
    onLanguageSelected: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val languages = remember { viewModel.availableLanguages() }
    var selected by remember { mutableStateOf(viewModel.currentLang) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrandHeader(
            title = viewModel.tr("choose"),
            subtitle = viewModel.tr("choose_sub"),
            onBack = onBack,
            showEmblem = onBack == null
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(languages, key = { it.first }) { (code, nativeName) ->
                LanguageRow(
                    code = code,
                    nativeName = nativeName,
                    hint = LANG_HINT[code] ?: code.uppercase(),
                    selected = selected == code,
                    onClick = {
                        selected = code
                        // Apply immediately so the screen itself re-renders in
                        // the chosen language — instant, tangible feedback.
                        viewModel.setLanguage(code)
                    }
                )
            }

            item {
                Spacer(Modifier.height(6.dp))
                InfoBanner(text = viewModel.tr("lang_note"))
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Box(
                Modifier
                    .navigationBarsPadding()
                    .padding(18.dp)
            ) {
                PrimaryButton(
                    text = viewModel.tr("continue"),
                    onClick = {
                        viewModel.setLanguage(selected)
                        onLanguageSelected()
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    code: String,
    nativeName: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) accent.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) accent else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) accent.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Translate,
                    contentDescription = null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = nativeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) accent else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
