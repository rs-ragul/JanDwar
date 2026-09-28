package `in`.jandwar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.jandwar.app.data.model.MatchedRole
import `in`.jandwar.app.ui.components.GradientHeader
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.theme.Muted
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onDetail: (String) -> Unit
) {
    val allRoles by viewModel.allRoles.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, allRoles) {
        if (query.isBlank()) allRoles.take(100)
        else allRoles.filter {
            it.job_role.contains(query, ignoreCase = true) ||
                    it.qp_code.contains(query, ignoreCase = true) ||
                    it.sector.contains(query, ignoreCase = true)
        }.take(100)
    }

    Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
        GradientHeader(
            title = viewModel.tr("browse_title"),
            subtitle = viewModel.tr("browse_sub"),
            actionText = viewModel.tr("back"),
            onAction = onBack
        )
        Spacer(Modifier.height(12.dp))

        PremiumCard {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(viewModel.tr("browse_note"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(viewModel.tr("search")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(filtered) { role ->
                val rec = MatchedRole(
                    role = role,
                    score = 0,
                    reason = viewModel.tr("reason_base"),
                    skillGapNote = "Check eligibility",
                    centre = viewModel.getCentreForDistrict(viewModel.profile.district)
                )
                CourseCard(rec = rec, viewModel = viewModel, onDetail = { onDetail(role.qp_code) })
            }
        }
    }
}
