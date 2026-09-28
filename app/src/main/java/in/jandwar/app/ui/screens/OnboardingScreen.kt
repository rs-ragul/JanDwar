package `in`.jandwar.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.jandwar.app.ui.components.GhostButton
import `in`.jandwar.app.ui.components.PrimaryButton
import `in`.jandwar.app.ui.theme.BrandIndigo
import `in`.jandwar.app.ui.theme.BrandSaffron
import `in`.jandwar.app.ui.theme.BrandTeal
import `in`.jandwar.app.ui.viewmodel.AppViewModel

private data class Page(val icon: ImageVector, val tint: Color)

private val PAGES = listOf(
    Page(Icons.Rounded.RecordVoiceOver, BrandTeal),
    Page(Icons.Rounded.WorkspacePremium, BrandSaffron),
    Page(Icons.Rounded.CloudOff, BrandIndigo)
)

@Composable
fun OnboardingScreen(
    viewModel: AppViewModel,
    onFinish: () -> Unit
) {
    val page = viewModel.onboardingPage.coerceIn(0, PAGES.lastIndex)
    val isLast = page == PAGES.lastIndex

    BackHandler(enabled = page > 0) { viewModel.prevOnboarding() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onFinish) {
                Text(
                    viewModel.tr("skip"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState > initialState
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(300)) { it * dir / 3 } + fadeIn(tween(300)))
                    .togetherWith(
                        slideOutHorizontally(tween(300)) { -it * dir / 3 } + fadeOut(tween(220))
                    )
            },
            label = "page",
            modifier = Modifier.weight(1f)
        ) { index ->
            val spec = PAGES[index]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(spec.tint.copy(alpha = 0.22f), spec.tint.copy(alpha = 0.04f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(spec.tint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            spec.icon,
                            contentDescription = null,
                            tint = spec.tint,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))

                Text(
                    text = viewModel.tr("on_title_$index"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = viewModel.tr("on_body_$index"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PAGES.indices.forEach { i ->
                val active = i == page
                val w by animateDpAsState(if (active) 26.dp else 8.dp, label = "dot$i")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(w)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page > 0) {
                GhostButton(
                    text = viewModel.tr("back"),
                    onClick = { viewModel.prevOnboarding() },
                    modifier = Modifier.weight(1f)
                )
            }
            PrimaryButton(
                text = if (isLast) viewModel.tr("get_started") else viewModel.tr("next"),
                onClick = { if (isLast) onFinish() else viewModel.nextOnboarding() },
                modifier = Modifier.weight(if (page > 0) 1.4f else 1f)
            )
        }
    }
}
