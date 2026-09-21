package com.thozhilthunai.app.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.thozhilthunai.app.data.model.Centre
import com.thozhilthunai.app.data.model.JobRole
import com.thozhilthunai.app.data.repository.DataRepository
import com.thozhilthunai.app.data.repository.UserPreferencesRepository
import com.thozhilthunai.app.ui.AppViewModel
import com.thozhilthunai.app.ui.components.DisclaimerNote
import com.thozhilthunai.app.ui.components.NsqfBadge
import com.thozhilthunai.app.ui.components.ThozhilCard
import com.thozhilthunai.app.ui.theme.BrandIndigo
import com.thozhilthunai.app.ui.theme.BrandSaffron
import com.thozhilthunai.app.ui.theme.BrandTeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    fun getRole(qpCode: String): JobRole? =
        dataRepository.getJobRoleSnapshot().find { it.qpCode == qpCode }

    fun getCentresForDistrict(district: String): List<Centre> =
        dataRepository.getCentresForDistrict(district)

    fun getSavedDistrict(): String = runBlocking {
        // Retrieve last district from prefs — store via separate key in a real app
        ""
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    appViewModel: AppViewModel,
    qpCode: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val role = remember(qpCode) { viewModel.getRole(qpCode) }

    // For demo, resolve district from a remembered state; in production store from intake
    var district by remember { mutableStateOf("") }
    val centres = remember(district) { if (district.isNotEmpty()) viewModel.getCentresForDistrict(district) else emptyList() }

    if (role == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Role not found")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(role.jobRole, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // — Role header —
            ThozhilCard {
                Text(
                    text = role.jobRole,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = BrandIndigo,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NsqfBadge(level = role.nsqfLevel, inferred = role.nsqfLevel.contains("inferred"))
                    Surface(color = BrandTeal.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "${role.notionalHours} hrs",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandTeal,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                DetailRow(label = "QP Code", value = role.qpCode)
                DetailRow(label = "Sector", value = role.sector.replace("_", " ").uppercase())
                DetailRow(label = "SSC", value = role.ssc)
            }

            // — District selector for centres —
            OutlinedTextField(
                value = district,
                onValueChange = { district = it },
                label = { Text(appViewModel.str("q_dist")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // — Training centres —
            if (centres.isNotEmpty()) {
                Text(
                    text = appViewModel.str("centre"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                centres.forEach { centre ->
                    CentreCard(centre = centre, onCall = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${centre.phone}"))
                        context.startActivity(intent)
                    })
                }
            } else if (district.isNotEmpty()) {
                ThozhilCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = BrandSaffron)
                        Text(
                            text = appViewModel.str("no_centre"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // — Legal disclaimer —
            DisclaimerNote(
                text = "Course/centre details are indicative. Confirm eligibility, fees and empanelment with the training centre / TAHDCO / the relevant Skill Council before enrolling."
            )

            // — Asset subsidy note (verbatim, never altered) —
            DisclaimerNote(
                text = "Asset subsidy: up to Rs.50,000 or 50% of asset cost (with loan), whichever is lower."
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CentreCard(centre: Centre, onCall: () -> Unit) {
    ThozhilCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = centre.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = centre.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (centre.trades.isNotEmpty()) {
                    Text(
                        text = "Trades: " + centre.trades.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onCall) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = BrandIndigo)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
