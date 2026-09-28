package `in`.jandwar.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.jandwar.app.ui.components.Badge
import `in`.jandwar.app.ui.components.BrandHeader
import `in`.jandwar.app.ui.components.EmptyState
import `in`.jandwar.app.ui.components.InfoBanner
import `in`.jandwar.app.ui.components.KeyValueRow
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.components.PrimaryButton
import `in`.jandwar.app.ui.components.SectionTitle
import `in`.jandwar.app.ui.theme.BrandIndigo
import `in`.jandwar.app.ui.theme.BrandTeal
import `in`.jandwar.app.ui.theme.Success
import `in`.jandwar.app.ui.theme.Warning
import `in`.jandwar.app.ui.theme.sectorColor
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val matched = viewModel.selectedRole
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrandHeader(
            title = matched?.role?.job_role ?: viewModel.tr("view_details"),
            subtitle = matched?.role?.let { viewModel.sectorLabel(it.sector) },
            onBack = onBack
        )

        if (matched == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = viewModel.tr("no_results"),
                    message = viewModel.tr("no_results_sub")
                )
            }
            return@Column
        }

        val role = matched.role
        val accent = sectorColor(role.sector)

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Eligibility banner ──────────────────────────────────────────
            item {
                val ok = matched.eligible
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = (if (ok) Success else Warning).copy(alpha = 0.11f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (ok) Icons.Rounded.Check else Icons.Rounded.PriorityHigh,
                            null,
                            tint = if (ok) Success else Warning,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.width(11.dp))
                        Text(
                            viewModel.tr(if (ok) "eligible" else "needs_edu"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ok) Success else Warning
                        )
                    }
                }
            }

            // ── Course facts ────────────────────────────────────────────────
            item {
                PremiumCard(accent = accent) {
                    SectionTitle(viewModel.tr("course_type"))
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Badge(
                            text = "${viewModel.tr("level")} ${role.nsqf_level.ifBlank { "—" }}",
                            color = BrandIndigo,
                            icon = Icons.Rounded.School
                        )
                        Badge(
                            text = viewModel.tr(
                                if (role.isLongTerm()) "long_term" else "short_term"
                            ),
                            color = BrandTeal
                        )
                        if (role.isFundable()) {
                            Badge(
                                text = viewModel.tr("fundable_badge"),
                                color = Success,
                                icon = Icons.Rounded.Verified
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    KeyValueRow(viewModel.tr("qp_code"), role.qp_code.ifBlank { "—" })
                    KeyValueRow(viewModel.tr("duration"), role.durationLabel())
                    KeyValueRow(viewModel.tr("sector"), viewModel.sectorLabel(role.sector))
                    KeyValueRow(viewModel.tr("ssc"), role.ssc.ifBlank { "—" })
                }
            }

            // ── Why this was suggested ──────────────────────────────────────
            if (matched.reason.isNotBlank() || matched.factors.isNotEmpty()) {
                item {
                    PremiumCard {
                        SectionTitle(viewModel.tr("why"))
                        Spacer(Modifier.height(9.dp))
                        if (matched.reason.isNotBlank()) {
                            Text(
                                matched.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        matched.factors.forEach { factor ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            (if (factor.positive) Success else Warning)
                                                .copy(alpha = 0.18f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (factor.positive) Icons.Rounded.Check
                                        else Icons.Rounded.PriorityHigh,
                                        null,
                                        tint = if (factor.positive) Success else Warning,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    factor.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Skill gap ───────────────────────────────────────────────────
            if (matched.skillGapNote.isNotBlank()) {
                item {
                    PremiumCard {
                        SectionTitle(viewModel.tr("skill_gap"))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            matched.skillGapNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (matched.regionOpportunity.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            InfoBanner(text = matched.regionOpportunity)
                        }
                    }
                }
            }

            // ── Funding ─────────────────────────────────────────────────────
            item {
                PremiumCard(accent = if (role.isFundable()) Success else null) {
                    SectionTitle(viewModel.tr("fundable"))
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Savings,
                            null,
                            tint = if (role.isFundable()) Success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            viewModel.tr(
                                if (role.isFundable()) "fundable_badge" else "not_fundable"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (role.isFundable()) {
                        Spacer(Modifier.height(11.dp))
                        KeyValueRow(viewModel.tr("asset"), viewModel.tr("asset_rule"))
                    }
                }
            }

            // ── Centre ──────────────────────────────────────────────────────
            item {
                PremiumCard {
                    SectionTitle(viewModel.tr("centre"))
                    Spacer(Modifier.height(9.dp))
                    val centre = matched.centre
                    if (centre == null) {
                        Text(
                            viewModel.tr("no_centre"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            centre.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            centre.address.ifBlank { centre.district },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (centre.trades.isNotBlank()) {
                            Spacer(Modifier.height(9.dp))
                            KeyValueRow(viewModel.tr("outcome"), centre.trades)
                        }
                        if (centre.isConfirmed()) {
                            Spacer(Modifier.height(9.dp))
                            Badge(
                                text = centre.confidence,
                                color = Success,
                                icon = Icons.Rounded.Verified
                            )
                        }
                        if (centre.hasPhone()) {
                            Spacer(Modifier.height(14.dp))
                            PrimaryButton(
                                text = viewModel.tr("call"),
                                icon = Icons.Rounded.Call,
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_DIAL,
                                                Uri.parse("tel:${centre.phone}")
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── Disclaimer ──────────────────────────────────────────────────
            item {
                InfoBanner(
                    text = viewModel.tr("not_claim"),
                    icon = Icons.Rounded.Info,
                    tone = Warning
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    viewModel.tr("honesty_note"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

