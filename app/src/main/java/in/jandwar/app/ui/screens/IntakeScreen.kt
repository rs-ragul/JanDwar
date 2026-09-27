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

    fun famOccLabel(): String = when(viewModel.currentLang) {
        "ta" -> "குடும்ப / பரம்பரை தொழில்"
        "hi" -> "परिवार / पारंपरिक व्यवसाय"
        "te" -> "కుటుంబ / సాంప్రదాయ వృత్తి"
        "kn" -> "ಕುಟುಂಬ / ಸಾಂಪ್ರದಾಯಿಕ ಉದ್ಯೋಗ"
        "ml" -> "കുടുംബ / പരമ്പരാഗത തൊഴിൽ"
        else -> "Family / Traditional Occupation"
    }
    fun famOccSub(): String = when(viewModel.currentLang) {
        "ta" -> "குடும்ப தொழில் புரிந்து கொள்ள"
        "hi" -> "परिवार के काम को समझना"
        "te" -> "కుటుంబ వృత్తిని అర్థం చేసుకోవడానికి"
        "kn" -> "ಕುಟುಂಬದ ಕೆಲಸವನ್ನು ಅರ್ಥಮಾಡಿಕೊಳ್ಳಲು"
        "ml" -> "കുടുംബ തൊഴിൽ മനസ്സിലാക്കാൻ"
        else -> "Helps avoid mismatch"
    }
    fun currLabel(): String = when(viewModel.currentLang) {
        "ta" -> "தற்போதைய வாழ்வாதாரம்"
        "hi" -> "वर्तमान आजीविका"
        "te" -> "ప్రస్తుత జీవనోపాధి"
        "kn" -> "ಪ್ರಸ್ತುತ ಜೀವನೋಪಾಯ"
        "ml" -> "നിലവിലെ ഉപജീവനം"
        else -> "Current Livelihood Activities"
    }
    fun physLabel(): String = when(viewModel.currentLang) {
        "ta" -> "உடல் தடை ஏதேனும்?"
        "hi" -> "कोई शारीरिक परेशानी?"
        "te" -> "శారీరక ఇబ్బందులు?"
        "kn" -> "ದೈಹಿಕ ತೊಂದರೆ?"
        "ml" -> "ശാരീരിക ബുദ്ധിമുട്ട്?"
        else -> "Physical Constraints (if any)"
    }
    fun localLabel(): String = when(viewModel.currentLang) {
        "ta" -> "உங்கள் ஊரில் என்ன வேலை கிடைக்கும்?"
        "hi" -> "आपके गाँव में क्या काम मिलता है?"
        "te" -> "మీ ఊరిలో ఏ పనులు దొరుకుతాయి?"
        "kn" -> "ನಿಮ್ಮ ಊರಲ್ಲಿ ಏನು ಕೆಲಸ ಸಿಗುತ್ತದೆ?"
        "ml" -> "നിങ്ങളുടെ ഗ്രാമത്തിൽ എന്ത് ജോലി കിട്ടും?"
        else -> "Local Economic Realities"
    }

    Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
        GradientHeader(
            title = viewModel.tr("intake"),
            subtitle = viewModel.tr("start_intake"),
            actionText = viewModel.tr("back"),
            onAction = onBack
        )
        Spacer(Modifier.height(14.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            Text(viewModel.tr("q_edu"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
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

            PremiumCard {
                Column {
                    Text(famOccLabel(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(famOccSub(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.profile.familyOccupation,
                        onValueChange = { viewModel.updateFamilyOccupation(it) },
                        placeholder = { Text(if (viewModel.currentLang=="ta") "விவசாயம், அரசு, தையல்..." else "e.g., Farming, Government, Tailoring") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            PremiumCard {
                Column {
                    Text(currLabel(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.profile.currentLivelihood,
                        onValueChange = { viewModel.updateCurrentLivelihood(it) },
                        placeholder = { Text(if (viewModel.currentLang=="ta") "மாணவர், விவசாயி, கூலி..." else "e.g., Student, Farmer, Daily wage") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(viewModel.tr("q_pref"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
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

            Text(viewModel.tr("q_travel"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
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
                    Text(physLabel(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = viewModel.profile.physicalConstraints,
                        onValueChange = { viewModel.updatePhysicalConstraints(it) },
                        placeholder = { Text(if (viewModel.currentLang=="ta") "பாரமான வேலை செய்ய முடியாது..." else "e.g., Cannot do heavy lifting") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(viewModel.tr("q_dist"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(18.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            PremiumCard {
                Column {
                    Text(localLabel(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = viewModel.profile.localOpportunity,
                        onValueChange = { viewModel.updateLocalOpportunity(it) },
                        placeholder = { Text(if (viewModel.currentLang=="ta") "பால் தேவை அதிகம்..." else "e.g., Dairy demand high") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 3
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(viewModel.tr("q_int"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
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
                Text(viewModel.tr("no_results").ifBlank { "Please answer required questions" }, color = BrandSaffron, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDistrictSheet) {
        ModalBottomSheet(onDismissRequest = { showDistrictSheet = false }) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(viewModel.tr("select_district"), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
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
