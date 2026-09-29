package `in`.jandwar.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import `in`.jandwar.app.data.model.JobRole
import `in`.jandwar.app.ui.components.Badge
import `in`.jandwar.app.ui.components.BrandHeader
import `in`.jandwar.app.ui.components.EmptyState
import `in`.jandwar.app.ui.components.SelectChip
import `in`.jandwar.app.ui.theme.BrandIndigo
import `in`.jandwar.app.ui.theme.Success
import `in`.jandwar.app.ui.theme.sectorColor
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@Composable
fun CoursesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onDetail: (String) -> Unit
) {
    val all by viewModel.allRoles.collectAsState()
    val sectors = remember(all) { viewModel.sectors() }

    // Recomputed whenever the query or the sector filter changes.
    val filtered = remember(viewModel.searchQuery, viewModel.sectorFilter, all) {
        viewModel.filteredRoles()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrandHeader(
            title = viewModel.tr("all_courses"),
            subtitle = "${filtered.size} / ${all.size}",
            onBack = onBack
        )

        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(viewModel.tr("search_hint")) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (viewModel.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = viewModel.tr("close"))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(Modifier.height(11.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    SelectChip(
                        text = viewModel.tr("all_sectors"),
                        selected = viewModel.sectorFilter == null,
                        onClick = { viewModel.sectorFilter = null }
                    )
                }
                items(sectors, key = { it }) { sector ->
                    SelectChip(
                        text = viewModel.sectorLabel(sector),
                        selected = viewModel.sectorFilter == sector,
                        onClick = {
                            viewModel.sectorFilter =
                                if (viewModel.sectorFilter == sector) null else sector
                        }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = viewModel.tr("no_results"),
                    message = viewModel.tr("no_results_sub"),
                    icon = Icons.Rounded.SearchOff
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.qp_code + it.job_role }) { role ->
                    CourseRow(viewModel, role) { onDetail(role.qp_code) }
                }
            }
        }
    }
}

@Composable
private fun CourseRow(
    viewModel: AppViewModel,
    role: JobRole,
    onClick: () -> Unit
) {
    val accent = sectorColor(role.sector)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    role.job_role,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(
                        text = "${viewModel.tr("level")} ${role.nsqf_level.ifBlank { "—" }}",
                        color = BrandIndigo
                    )
                    Spacer(Modifier.width(6.dp))
                    Badge(text = role.durationLabel().ifBlank { "—" }, color = accent)
                    if (role.isFundable()) {
                        Spacer(Modifier.width(6.dp))
                        Badge(text = "GIA", color = Success)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
