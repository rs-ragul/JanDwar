package `in`.jandwar.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.jandwar.app.data.model.MatchedRole
import `in`.jandwar.app.ui.components.Badge
import `in`.jandwar.app.ui.components.BrandHeader
import `in`.jandwar.app.ui.components.EmptyState
import `in`.jandwar.app.ui.components.GhostButton
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.theme.BrandIndigo
import `in`.jandwar.app.ui.theme.BrandSaffron
import `in`.jandwar.app.ui.theme.BrandTeal
import `in`.jandwar.app.ui.theme.Success
import `in`.jandwar.app.ui.theme.Warning
import `in`.jandwar.app.ui.theme.sectorColor
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@Composable
fun ResultsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onDetail: (MatchedRole) -> Unit,
    onBrowseAll: () -> Unit
) {
    val results by viewModel.results.collectAsState()
    val narration = viewModel.resultNarration

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrandHeader(
            title = viewModel.tr("options"),
            subtitle = if (results.isEmpty()) null
            else "${results.size} ${viewModel.tr("found")}",
            onBack = onBack
        )

        when {
            viewModel.isMatching -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        viewModel.tr("thinking"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            results.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = viewModel.tr("no_results"),
                    message = viewModel.tr("no_results_sub"),
                    icon = Icons.Rounded.SearchOff,
                    action = {
                        GhostButton(
                            text = viewModel.tr("all_courses"),
                            onClick = onBrowseAll
                        )
                    }
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (narration.isNotBlank()) {
                    item { NarrationCard(narration) }
                }

                items(results, key = { it.role.qp_code + it.score }) { matched ->
                    ResultCard(
                        viewModel = viewModel,
                        matched = matched,
                        isTop = matched == results.first(),
                        onClick = { onDetail(matched) }
                    )
                }

                item {
                    Spacer(Modifier.height(2.dp))
                    GhostButton(
                        text = viewModel.tr("all_courses"),
                        onClick = onBrowseAll,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        viewModel.tr("not_claim"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun NarrationCard(text: String) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = BrandTeal.copy(alpha = 0.09f),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandTeal.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.AutoAwesome,
                null,
                tint = BrandTeal,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(11.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(
    viewModel: AppViewModel,
    matched: MatchedRole,
    isTop: Boolean,
    onClick: () -> Unit
) {
    val role = matched.role
    val accent = sectorColor(role.sector)

    PremiumCard(onClick = onClick, accent = accent) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (isTop) {
                    Badge(
                        text = viewModel.tr("best_match"),
                        color = BrandSaffron,
                        icon = Icons.Rounded.Verified
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    role.job_role,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    viewModel.sectorLabel(role.sector),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
            Spacer(Modifier.width(12.dp))
            ConfidenceRing(matched.confidence, viewModel.tr("match"))
        }

        Spacer(Modifier.height(13.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Badge(
                text = "${viewModel.tr("level")} ${role.nsqf_level.ifBlank { "—" }}",
                color = BrandIndigo,
                icon = Icons.Rounded.School
            )
            Badge(text = role.durationLabel(), color = BrandTeal)
            Badge(
                text = viewModel.tr(if (role.isLongTerm()) "long_term" else "short_term"),
                color = BrandIndigo
            )
            if (role.isFundable()) {
                Badge(
                    text = viewModel.tr("fundable_badge"),
                    color = Success,
                    icon = Icons.Rounded.Check
                )
            }
        }

        if (matched.reason.isNotBlank()) {
            Spacer(Modifier.height(13.dp))
            Text(
                matched.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!matched.eligible) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.PriorityHigh,
                    null,
                    tint = Warning,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    viewModel.tr("needs_edu"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning
                )
            }
        }

        matched.centre?.let { centre ->
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.LocationOn,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${centre.name}, ${centre.district}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(13.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                viewModel.tr("view_details"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ConfidenceRing(confidence: Int, label: String) {
    val target = (confidence.coerceIn(0, 100)) / 100f
    val animated by animateFloatAsState(target, tween(700), label = "confidence")
    val tone = when {
        confidence >= 80 -> Success
        confidence >= 60 -> BrandTeal
        else -> BrandSaffron
    }

    Box(modifier = Modifier.size(62.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(62.dp),
            color = tone.copy(alpha = 0.16f),
            strokeWidth = 5.dp,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round
        )
        CircularProgressIndicator(
            progress = { animated },
            modifier = Modifier.size(62.dp),
            color = tone,
            strokeWidth = 5.dp,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$confidence%",
                style = MaterialTheme.typography.labelLarge,
                color = tone
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
