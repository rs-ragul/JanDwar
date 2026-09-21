package com.thozhilthunai.app.ui.screens.results

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.thozhilthunai.app.data.model.MatchedRole
import com.thozhilthunai.app.data.repository.DataRepository
import com.thozhilthunai.app.ui.AppViewModel
import com.thozhilthunai.app.ui.components.NsqfBadge
import com.thozhilthunai.app.ui.components.ThozhilCard
import com.thozhilthunai.app.ui.theme.BrandIndigo
import com.thozhilthunai.app.ui.theme.BrandTeal
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var _qpCodes: List<String> = emptyList()
    private var _roles: List<MatchedRole> = emptyList()

    fun loadFromCodes(codes: List<String>) {
        _qpCodes = codes
        val allRoles = dataRepository.getJobRoleSnapshot()
        _roles = codes.mapIndexed { idx, code ->
            val role = allRoles.find { it.qpCode == code }
            role?.let { MatchedRole(it, score = codes.size - idx, levelInferred = it.nsqfLevel.contains("inferred")) }
        }.filterNotNull()
    }

    fun getRoles(): List<MatchedRole> = _roles
}

@Composable
fun ResultsScreen(
    appViewModel: AppViewModel,
    onRoleClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    // Retrieve codes passed via route (comma-separated)
    // In a real Nav3 setup, the codes are embedded in the route key
    // For now we read from the ViewModel if loaded, else show empty state

    val roles = remember { viewModel.getRoles() }
    val lang by appViewModel.currentLang.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(appViewModel.str("options"), fontWeight = FontWeight.Bold)
                        Text(
                            text = "${roles.size} ${appViewModel.str("found")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (roles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No roles matched. Try different filters.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(roles) { matched ->
                    RoleCard(matched = matched, onClick = { onRoleClick(matched.role.qpCode) })
                }
            }
        }
    }
}

@Composable
private fun RoleCard(matched: MatchedRole, onClick: () -> Unit) {
    ThozhilCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = matched.role.jobRole,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandIndigo
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NsqfBadge(level = matched.role.nsqfLevel, inferred = matched.levelInferred)
                    Surface(
                        color = BrandTeal.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${matched.role.notionalHours}h",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandTeal,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = matched.role.ssc + " · " + matched.role.sector.replace("_", " ").uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
