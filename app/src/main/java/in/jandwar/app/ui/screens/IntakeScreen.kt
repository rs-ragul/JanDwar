package `in`.jandwar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.jandwar.app.data.model.EducationLevel
import `in`.jandwar.app.data.model.Mobility
import `in`.jandwar.app.data.model.Preference
import `in`.jandwar.app.ui.components.GradientHeader
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.theme.BrandIndigo
import `in`.jandwar.app.ui.theme.BrandSaffron
import `in`.jandwar.app.ui.theme.BrandTeal
import `in`.jandwar.app.ui.theme.Ink
import `in`.jandwar.app.ui.theme.Muted
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    var showDistrictSheet by remember { mutableStateOf(false) }
    val districts = viewModel.getDistricts()

    Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
        GradientHeader(
            title = viewModel.tr("intake"),
            subtitle = viewModel.tr("start_intake"),
            actionText = viewModel.tr("back"),
            onAction = onBack
        )
        Spacer(Modifier.height(14.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // Education - Required
            Text(viewModel.tr("q_edu"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Educational background - as per PM-AJAY guidelines", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            listOf(
                EducationLevel.BELOW_8 to viewModel.tr("edu_below8"),
                EducationLevel.CLASS_8 to viewModel.tr("edu_8"),
                EducationLevel.CLASS_10 to viewModel.tr("edu_10"),
                EducationLevel.CLASS_12 to viewModel.tr("edu_12"),
                EducationLevel.GRADUATE to viewModel.tr("edu_grad")
            ).forEach { (level, label) ->
                val selected = viewModel.profile.education == level
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.updateEducation(level) },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandTeal.copy(alpha = 0.15f),
                        selectedLabelColor = BrandIndigo
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // Family Occupation - NEW per problem statement
            PremiumCard {
                Column {
                    Text("Family / Traditional Occupation", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Existing or traditional family occupations - helps avoid mismatch", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.profile.familyOccupation,
                        onValueChange = { viewModel.updateFamilyOccupation(it) },
                        placeholder = { Text("e.g., Farming, Cattle rearing, Tailoring, Construction labour") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Current Livelihood - NEW
            PremiumCard {
                Column {
                    Text("Current Livelihood Activities", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("What do you do currently? Daily wage, farming, unemployed, student", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.profile.currentLivelihood,
                        onValueChange = { viewModel.updateCurrentLivelihood(it) },
                        placeholder = { Text("e.g., Daily wage labour, Farmer, Unemployed, Student") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Preference
            Text(viewModel.tr("q_pref"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Preference for self-employment or wage employment", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            listOf(
                Preference.SELF to viewModel.tr("pref_self"),
                Preference.WAGE to viewModel.tr("pref_wage")
            ).forEach { (pref, label) ->
                val selected = viewModel.profile.preference == pref
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.updatePreference(pref) },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandTeal.copy(alpha = 0.15f),
                        selectedLabelColor = BrandIndigo
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            // Mobility + Physical Constraints - NEW
            Text(viewModel.tr("q_travel"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Mobility and physical constraints - per problem statement", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            listOf(
                Mobility.LOCAL to viewModel.tr("travel_local"),
                Mobility.DISTRICT to viewModel.tr("travel_district"),
                Mobility.STATE to viewModel.tr("travel_any")
            ).forEach { (mob, label) ->
                val selected = viewModel.profile.mobility == mob
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.updateMobility(mob) },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandTeal.copy(alpha = 0.15f),
                        selectedLabelColor = BrandIndigo
                    )
                )
            }

            Spacer(Modifier.height(8.dp))
            PremiumCard {
                Column {
                    Text("Physical Constraints (if any)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    OutlinedTextField(
                        value = viewModel.profile.physicalConstraints,
                        onValueChange = { viewModel.updatePhysicalConstraints(it) },
                        placeholder = { Text("e.g., Cannot do heavy lifting, cannot walk long distance") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // District
            Text(viewModel.tr("q_dist"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Local economic realities and opportunities", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            Card(
                onClick = { showDistrictSheet = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (viewModel.profile.district.isBlank()) viewModel.tr("select_district") else viewModel.profile.district,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.padding(18.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            PremiumCard {
                Column {
                    Text("Local Economic Realities", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("What work is available in your village/area? Local market demand", fontSize = 12.sp, color = Muted)
                    OutlinedTextField(
                        value = viewModel.profile.localOpportunity,
                        onValueChange = { viewModel.updateLocalOpportunity(it) },
                        placeholder = { Text("e.g., Dairy demand high, no construction work, textile mills nearby") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 3
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Interests
            Text(viewModel.tr("q_int"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Skills and interests - assessed for NSQF mapping", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            viewModel.getInterestChips().forEach { chip ->
                val selected = viewModel.profile.interests.contains(chip.key)
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.toggleInterest(chip.key) },
                    label = { Text(chip.label) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandTeal.copy(alpha = 0.15f),
                        selectedLabelColor = BrandIndigo
                    )
                )
            }

            Spacer(Modifier.height(18.dp))
            val canSubmit = viewModel.profile.education != null &&
                    viewModel.profile.preference != null &&
                    viewModel.profile.mobility != null &&
                    viewModel.profile.district.isNotBlank() &&
                    viewModel.profile.interests.isNotEmpty()

            Button(
                onClick = {
                    viewModel.runMatching()
                    onSubmit()
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
            ) {
                Text(viewModel.tr("submit"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (!canSubmit) {
                Text("Please answer required questions (education, preference, mobility, district, interests)", color = BrandSaffron, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Family occupation, current livelihood, physical constraints are optional but help AI give more empathetic, accurate recommendations per PM-AJAY", fontSize = 11.sp, color = Muted)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDistrictSheet) {
        ModalBottomSheet(onDismissRequest = { showDistrictSheet = false }) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Select District", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                districts.forEach { district ->
                    TextButton(
                        onClick = {
                            viewModel.updateDistrict(district)
                            showDistrictSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(district, modifier = Modifier.fillMaxWidth(), fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
