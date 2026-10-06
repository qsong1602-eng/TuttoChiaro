package com.tuttochiaro.offline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.AppLanguage
import com.tuttochiaro.offline.i18n.localized
import com.tuttochiaro.offline.model.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OfflineResourcesScreen(
    state: TranslatorUiState,
    appLanguage: AppLanguage,
    onSelectAppLanguage: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPrepareModel: () -> Unit,
    onPrepareSpeech: () -> Unit,
    onSelectTranslation: (TranslationModel) -> Unit,
    onSelectSpeech: (SpeechModel) -> Unit,
    onToggleCommonLanguage: (Language) -> Unit,
    onTtsRate: (Float) -> Unit,
    onTtsVoice: (Language, String?) -> Unit,
    onPreviewTts: (Language) -> Unit,
    onStopTts: () -> Unit,
    onInstallTts: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val commonLanguages = Language.all.filter { it in state.commonLanguages }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.ui_settings)) }, navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.ui_back)) } }) },
        snackbarHost = {
            state.message?.let { message ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.ui_close)) } },
                ) { Text(message.localized()) }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsCard(stringResource(R.string.ui_interface_language)) {
                    Text(stringResource(R.string.ui_interface_language_help))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppLanguage.entries.forEach { language ->
                            FilterChip(
                                selected = appLanguage == language,
                                onClick = { onSelectAppLanguage(language) },
                                label = { Text(if (language == AppLanguage.SYSTEM) stringResource(R.string.ui_follow_system) else language.nativeLabel) },
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.ui_common_languages)) {
                    Text(stringResource(R.string.ui_common_languages_help))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Language.all.forEach { language ->
                            FilterChip(
                                selected = language in state.commonLanguages,
                                onClick = { onToggleCommonLanguage(language) },
                                label = { Text(language.localizedDisplayName()) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.ui_common_languages_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                SettingsCard(stringResource(R.string.ui_translation_model)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TranslationModel.entries.forEach { model ->
                            FilterChip(
                                selected = state.translationModel == model,
                                onClick = { onSelectTranslation(model) },
                                label = { Text(model.label) },
                            )
                        }
                    }
                    Text(state.modelMessage.localized())
                    Button(onClick = onPrepareModel, enabled = !state.isPreparingModel) {
                        Text(if (state.modelReady) stringResource(R.string.ui_recheck) else stringResource(R.string.ui_prepare_translation_model))
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.ui_speech_model)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeechModel.entries.forEach { model ->
                            FilterChip(
                                selected = state.speechModel == model,
                                onClick = { onSelectSpeech(model) },
                                label = { Text(model.label) },
                            )
                        }
                    }
                    Text(state.modelStates[state.speechModel.id]?.message?.localized() ?: stringResource(R.string.ui_waiting_local_preparation))
                    Text(stringResource(R.string.ui_speech_model_help))
                    Button(onClick = onPrepareSpeech) { Text(stringResource(R.string.ui_prepare_speech_model)) }
                }
            }
            item {
                SettingsCard(stringResource(R.string.ui_offline_speech)) {
                    Text(stringResource(R.string.ui_offline_speech_help))
                    Text(stringResource(R.string.ui_speech_rate, state.ttsRate))
                    Slider(value = state.ttsRate, onValueChange = onTtsRate, valueRange = 0.75f..1.2f, steps = 8)
                    TextButton(onClick = { onTtsRate(1f) }) { Text(stringResource(R.string.ui_reset_speech_rate)) }

                    commonLanguages.forEach { language ->
                        val resource = state.resourceStates.firstOrNull { it.language == language }
                        val voices = state.ttsVoices[language].orEmpty()
                        val selected = voices.firstOrNull { it.id == state.ttsSelectedVoices[language] }
                        var expanded by remember(language) { mutableStateOf(false) }

                        HorizontalDivider()
                        Text(stringResource(R.string.ui_speech_language_status, language.localizedDisplayName(),
                            if (resource?.ttsReady == true) stringResource(R.string.ui_offline_voice_available) else stringResource(R.string.ui_offline_voice_missing)))
                        Box {
                            OutlinedButton(onClick = { expanded = true }, enabled = voices.isNotEmpty()) {
                                Text(selected?.displayLabel ?: stringResource(R.string.ui_no_offline_voices))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.ui_auto_select_voice)) }, onClick = {
                                    expanded = false
                                    onTtsVoice(language, null)
                                })
                                voices.forEach { voice ->
                                    DropdownMenuItem(text = { Text(voice.displayLabel) }, onClick = {
                                        expanded = false
                                        onTtsVoice(language, voice.id)
                                    })
                                }
                            }
                        }
                        TextButton(onClick = { onPreviewTts(language) }, enabled = resource?.ttsReady == true) {
                            Text(stringResource(R.string.ui_preview_voice, language.localizedDisplayName()))
                        }
                    }
                    TextButton(onClick = onStopTts) { Text(stringResource(R.string.ui_stop_speech)) }
                    TextButton(onClick = onInstallTts) { Text(stringResource(R.string.ui_manage_device_voices)) }
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.ui_refresh_speech_status)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
