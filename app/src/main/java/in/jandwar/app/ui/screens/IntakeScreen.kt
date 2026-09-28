package `in`.jandwar.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import `in`.jandwar.app.data.model.EducationLevel
import `in`.jandwar.app.data.model.Mobility
import `in`.jandwar.app.data.model.Preference
import `in`.jandwar.app.ui.components.BrandHeader
import `in`.jandwar.app.ui.components.GhostButton
import `in`.jandwar.app.ui.components.InfoBanner
import `in`.jandwar.app.ui.components.OptionTile
import `in`.jandwar.app.ui.components.PrimaryButton
import `in`.jandwar.app.ui.components.SelectChip
import `in`.jandwar.app.ui.components.StepProgress
import `in`.jandwar.app.ui.viewmodel.AppViewModel

private const val STEPS = 5

@Composable
fun IntakeScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val profile = viewModel.profile

    val canAdvance = when (step) {
        0 -> profile.education != null
        1 -> true               // occupation text is optional
        2 -> profile.interests.isNotEmpty()
        3 -> profile.preference != null && profile.mobility != null
        4 -> profile.district.isNotBlank()
        else -> false
    }

    BackHandler { if (step > 0) step-- else onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BrandHeader(
            title = viewModel.tr("intake_title"),
            subtitle = "${step + 1} ${viewModel.tr("of")} $STEPS",
            onBack = { if (step > 0) step-- else onBack() }
        )

        Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            StepProgress(current = step + 1, total = STEPS)
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(280)) { it * dir / 4 } + fadeIn(tween(280)))
                    .togetherWith(
                        slideOutHorizontally(tween(280)) { -it * dir / 4 } + fadeOut(tween(200))
                    )
            },
            label = "step",
            modifier = Modifier.weight(1f)
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                when (current) {
                    0 -> EducationStep(viewModel)
                    1 -> OccupationStep(viewModel)
                    2 -> InterestStep(viewModel)
                    3 -> PreferenceStep(viewModel)
                    4 -> DistrictStep(viewModel)
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 0) {
                    GhostButton(
                        text = viewModel.tr("back"),
                        onClick = { step-- },
                        modifier = Modifier.weight(1f)
                    )
                }
                PrimaryButton(
                    text = if (step == STEPS - 1) viewModel.tr("submit") else viewModel.tr("next"),
                    enabled = canAdvance,
                    onClick = {
                        if (step == STEPS - 1) {
                            viewModel.runMatching()
                            onSubmit()
                        } else step++
                    },
                    modifier = Modifier.weight(if (step > 0) 1.5f else 1f)
                )
            }
        }
    }
}

// ── Steps ───────────────────────────────────────────────────────────────────

@Composable
private fun StepHeading(title: String, hint: String? = null) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground
    )
    if (!hint.isNullOrBlank()) {
        Spacer(Modifier.height(5.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun EducationStep(viewModel: AppViewModel) {
    StepHeading(viewModel.tr("q_edu"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EducationLevel.entries.forEach { level ->
            OptionTile(
                title = viewModel.tr(level.key),
                selected = viewModel.profile.education == level,
                onClick = { viewModel.updateEducation(level) }
            )
        }
    }
}

@Composable
private fun OccupationStep(viewModel: AppViewModel) {
    StepHeading(viewModel.tr("q_family"), viewModel.tr("optional"))

    OutlinedTextField(
        value = viewModel.profile.familyOccupation,
        onValueChange = viewModel::updateFamilyOccupation,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp),
        label = { Text(viewModel.tr("q_family")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        shape = MaterialTheme.shapes.medium
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = viewModel.profile.currentLivelihood,
        onValueChange = viewModel::updateCurrentLivelihood,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp),
        label = { Text(viewModel.tr("q_current")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.medium
    )

    Spacer(Modifier.height(18.dp))
    InfoBanner(
        text = viewModel.tr("card_voice_sub"),
        icon = Icons.Rounded.Work
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestStep(viewModel: AppViewModel) {
    StepHeading(viewModel.tr("q_int"))
    val chips = remember(viewModel.currentLang) { viewModel.getInterestChips() }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        chips.forEach { chip ->
            SelectChip(
                text = chip.label,
                selected = viewModel.profile.interests.contains(chip.key),
                onClick = { viewModel.toggleInterest(chip.key) }
            )
        }
    }
}

@Composable
private fun PreferenceStep(viewModel: AppViewModel) {
    StepHeading(viewModel.tr("q_pref"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionTile(
            title = viewModel.tr(Preference.SELF.key),
            selected = viewModel.profile.preference == Preference.SELF,
            onClick = { viewModel.updatePreference(Preference.SELF) },
            leadingIcon = Icons.Rounded.Storefront
        )
        OptionTile(
            title = viewModel.tr(Preference.WAGE.key),
            selected = viewModel.profile.preference == Preference.WAGE,
            onClick = { viewModel.updatePreference(Preference.WAGE) },
            leadingIcon = Icons.Rounded.Work
        )
    }

    Spacer(Modifier.height(26.dp))
    StepHeading(viewModel.tr("q_travel"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionTile(
            title = viewModel.tr(Mobility.LOCAL.key),
            selected = viewModel.profile.mobility == Mobility.LOCAL,
            onClick = { viewModel.updateMobility(Mobility.LOCAL) },
            leadingIcon = Icons.Rounded.Home
        )
        OptionTile(
            title = viewModel.tr(Mobility.DISTRICT.key),
            selected = viewModel.profile.mobility == Mobility.DISTRICT,
            onClick = { viewModel.updateMobility(Mobility.DISTRICT) },
            leadingIcon = Icons.Rounded.DirectionsBus
        )
        OptionTile(
            title = viewModel.tr(Mobility.STATE.key),
            selected = viewModel.profile.mobility == Mobility.STATE,
            onClick = { viewModel.updateMobility(Mobility.STATE) },
            leadingIcon = Icons.Rounded.Map
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DistrictStep(viewModel: AppViewModel) {
    StepHeading(viewModel.tr("q_dist"))

    val districts = remember { viewModel.getDistricts() }
    val withCentre = remember { viewModel.districtsWithCentre().toSet() }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = viewModel.profile.district,
            onValueChange = {},
            readOnly = true,
            label = { Text(viewModel.tr("select_district")) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(min = 62.dp),
            shape = MaterialTheme.shapes.medium
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            districts.forEach { d ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d, style = MaterialTheme.typography.bodyLarge)
                            if (d in withCentre) {
                                Spacer(Modifier.height(0.dp))
                                Text(
                                    "  •  ${viewModel.tr("centre")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        viewModel.updateDistrict(d)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }

    Spacer(Modifier.height(18.dp))

    val centre = viewModel.profile.district.takeIf { it.isNotBlank() }
        ?.let { viewModel.getCentreForDistrict(it) }

    if (viewModel.profile.district.isNotBlank()) {
        InfoBanner(
            text = centre?.let { "${it.name} — ${it.district}" } ?: viewModel.tr("no_centre"),
            icon = Icons.Rounded.Agriculture
        )
    }
}
