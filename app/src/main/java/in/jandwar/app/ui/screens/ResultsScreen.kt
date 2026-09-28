package `in`.jandwar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.jandwar.app.data.model.MatchedRole
import `in`.jandwar.app.ui.components.GradientHeader
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.theme.*
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@Composable
fun ResultsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onDetail: (String) -> Unit
) {
    val results by viewModel.results.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
        GradientHeader(
            title = viewModel.tr("options"),
            subtitle = "${viewModel.profile.district} • ${viewModel.profile.toReadableSummary().take(60)}",
            actionText = viewModel.tr("back"),
            onAction = onBack
        )
        Spacer(Modifier.height(14.dp))

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "${results.size} ${viewModel.tr("found")}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (viewModel.profile.familyOccupation.isNotBlank()) {
                    Text(
                        "Family: ${viewModel.profile.familyOccupation} • Current: ${viewModel.profile.currentLivelihood}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (results.isEmpty()) {
            PremiumCard {
                Text(viewModel.tr("no_results"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(results) { rec ->
                    CourseCard(rec = rec, viewModel = viewModel, onDetail = { onDetail(rec.role.qp_code) })
                }
            }
        }
    }
}

@Composable
fun CourseCard(rec: MatchedRole, viewModel: AppViewModel, onDetail: () -> Unit) {
    Card(
        onClick = onDetail,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = when (rec.role.sector) {
                        "agriculture" -> Color(0xFF169654)
                        "food_processing" -> BrandSaffron
                        "construction" -> BrandIndigo
                        "handloom_textile", "apparel" -> Color(0xFFD32A84)
                        else -> BrandTeal
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            when (rec.role.sector) {
                                "agriculture" -> "AG"
                                "food_processing" -> "FD"
                                "construction" -> "CN"
                                "handloom_textile", "apparel" -> "TX"
                                else -> "SK"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(rec.role.job_role, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 3)
                    Text("${rec.role.qp_code} · ${rec.role.ssc}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (rec.role.isFundable()) "Fundable" else "Check", color = Color.White, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (rec.role.isFundable()) Success else BrandSaffron
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            Row {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(end = 4.dp)) {
                    Text(
                        if (rec.role.isLongTerm()) viewModel.tr("long_term") else viewModel.tr("short_term"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        "${viewModel.tr("level")} ${rec.role.levelLabel(viewModel.tr("level"))}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(rec.reason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)

            Spacer(Modifier.height(8.dp))

            if (rec.familyFitNote.isNotBlank()) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("👨‍👩‍👧‍👦 ", fontSize = 14.sp)
                        Text(rec.familyFitNote, fontSize = 13.sp, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth()) {
                Text(rec.skillGapNote, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
            }

            Spacer(Modifier.height(6.dp))

            if (rec.regionOpportunity.isNotBlank()) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        Text("📍 ", fontSize = 14.sp)
                        Text(rec.regionOpportunity, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Text(
                rec.centre?.name ?: viewModel.tr("no_centre"),
                fontSize = 14.sp,
                color = if (rec.centre == null) BrandSaffron else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onDetail,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(viewModel.tr("details"), fontWeight = FontWeight.Bold)
            }
        }
    }
}
