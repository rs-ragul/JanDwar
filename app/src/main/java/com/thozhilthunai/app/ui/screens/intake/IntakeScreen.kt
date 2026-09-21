package com.thozhilthunai.app.ui.screens.intake

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thozhilthunai.app.data.model.EducationLevel
import com.thozhilthunai.app.data.model.IntakeFields
import com.thozhilthunai.app.data.repository.DataRepository
import com.thozhilthunai.app.domain.MatcherUseCase
import com.thozhilthunai.app.domain.TextParser
import com.thozhilthunai.app.speech.SpeechGateway
import com.thozhilthunai.app.speech.SpeechState
import com.thozhilthunai.app.ui.AppViewModel
import com.thozhilthunai.app.ui.components.SectorChip
import com.thozhilthunai.app.ui.theme.BrandSaffron
import com.thozhilthunai.app.ui.theme.BrandTeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntakeViewModel @Inject constructor(
    private val matcher: MatcherUseCase,
    private val textParser: TextParser,
    private val speechGateway: SpeechGateway,
    private val dataRepository: DataRepository
) : ViewModel() {

    val speechState: StateFlow<SpeechState> = speechGateway.state

    private val _freeText = MutableStateFlow("")
    val freeText: StateFlow<String> = _freeText.asStateFlow()

    private val _fields = MutableStateFlow(IntakeFields())
    val fields: StateFlow<IntakeFields> = _fields.asStateFlow()

    private val _matchedQpCodes = MutableStateFlow<List<String>>(emptyList())
    val matchedQpCodes: StateFlow<List<String>> = _matchedQpCodes.asStateFlow()

    init {
        viewModelScope.launch {
            speechGateway.state.collectLatest { state ->
                if (state is SpeechState.Result) {
                    _freeText.value = state.text
                    applyFreeText(state.text)
                }
            }
        }
    }

    fun setEducation(level: EducationLevel) {
        _fields.value = _fields.value.copy(education = level)
    }

    fun setDistrict(district: String) {
        _fields.value = _fields.value.copy(district = district)
    }

    fun toggleInterest(key: String) {
        val current = _fields.value.interests.toMutableSet()
        if (key in current) current.remove(key) else current.add(key)
        _fields.value = _fields.value.copy(interests = current)
    }

    fun setFreeText(text: String) { _freeText.value = text }

    fun applyFreeText(text: String) {
        val parsed = textParser.parse(text)
        _fields.value = parsed
    }

    fun startListening(langCode: String) { speechGateway.startListening(langCode) }
    fun stopListening() { speechGateway.stopListening() }

    fun runMatch(): List<String> {
        val results = matcher.match(_fields.value)
        val codes = results.map { it.role.qpCode }
        _matchedQpCodes.value = codes
        return codes
    }

    fun getDistricts(): List<String> =
        (dataRepository.districts as kotlinx.coroutines.flow.StateFlow).value.map { it.name }.sorted()

    override fun onCleared() {
        super.onCleared()
        speechGateway.destroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeScreen(
    appViewModel: AppViewModel,
    onResults: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: IntakeViewModel = hiltViewModel()
) {
    val lang by appViewModel.currentLang.collectAsState()
    val fields by viewModel.fields.collectAsState()
    val freeText by viewModel.freeText.collectAsState()
    val speechState by viewModel.speechState.collectAsState()
    val context = LocalContext.current

    var districtExpanded by remember { mutableStateOf(false) }
    var districtSearch by remember { mutableStateOf("") }
    val districts = remember { viewModel.getDistricts() }
    val filteredDistricts = remember(districtSearch) {
        districts.filter { it.contains(districtSearch, ignoreCase = true) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startListening(lang)
    }

    val educationOptions = listOf(
        EducationLevel.BELOW_8 to appViewModel.str("edu_below8"),
        EducationLevel.STANDARD_8 to appViewModel.str("edu_8"),
        EducationLevel.STANDARD_10 to appViewModel.str("edu_10"),
        EducationLevel.STANDARD_12 to appViewModel.str("edu_12"),
        EducationLevel.ITI_DIPLOMA to "ITI / Diploma",
        EducationLevel.GRADUATE to appViewModel.str("edu_grad")
    )

    val interestKeys = appViewModel.getAllInterestKeys()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appViewModel.str("q_edu"), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // — Education section —
            SectionHeader(appViewModel.str("q_edu"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                educationOptions.forEach { (level, label) ->
                    SectorChip(
                        text = label,
                        selected = fields.education == level,
                        onClick = { viewModel.setEducation(level) }
                    )
                }
            }

            HorizontalDivider()

            // — District section —
            SectionHeader(appViewModel.str("q_dist"))
            ExposedDropdownMenuBox(
                expanded = districtExpanded,
                onExpandedChange = { districtExpanded = it }
            ) {
                OutlinedTextField(
                    value = if (fields.district.isEmpty()) districtSearch else fields.district,
                    onValueChange = {
                        districtSearch = it
                        viewModel.setDistrict("")
                        districtExpanded = true
                    },
                    label = { Text(appViewModel.str("q_dist")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = districtExpanded, onDismissRequest = { districtExpanded = false }) {
                    filteredDistricts.take(20).forEach { district ->
                        DropdownMenuItem(
                            text = { Text(district) },
                            onClick = {
                                viewModel.setDistrict(district)
                                districtSearch = ""
                                districtExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // — Interests section —
            SectionHeader(appViewModel.str("q_int"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                interestKeys.forEach { key ->
                    SectorChip(
                        text = appViewModel.interestLabel(key),
                        selected = key in fields.interests,
                        onClick = { viewModel.toggleInterest(key) }
                    )
                }
            }

            HorizontalDivider()

            // — Voice / text input —
            SectionHeader(appViewModel.str("voice"))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = freeText,
                    onValueChange = viewModel::setFreeText,
                    label = { Text(appViewModel.str("tap_hint")) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )
                IconButton(
                    onClick = {
                        when (speechState) {
                            is SpeechState.Listening -> viewModel.stopListening()
                            else -> {
                                val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                if (perm == PackageManager.PERMISSION_GRANTED) viewModel.startListening(lang)
                                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (speechState is SpeechState.Listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Microphone",
                        tint = if (speechState is SpeechState.Listening) BrandSaffron else BrandTeal,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            if (freeText.isNotBlank()) {
                TextButton(onClick = { viewModel.applyFreeText(freeText) }) {
                    Text(appViewModel.str("use"), color = BrandTeal)
                }
            }
            if (speechState is SpeechState.Listening) {
                Text(
                    text = appViewModel.str("listening"),
                    color = BrandSaffron,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // — Submit button —
            Button(
                onClick = {
                    val codes = viewModel.runMatch()
                    onResults(codes.take(50).joinToString(","))
                },
                enabled = fields.district.isNotEmpty() || true, // allow even without district
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    appViewModel.str("submit"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}
