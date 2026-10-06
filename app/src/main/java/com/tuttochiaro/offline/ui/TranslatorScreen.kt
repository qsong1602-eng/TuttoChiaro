package com.tuttochiaro.offline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuttochiaro.offline.model.InputOrigin
import com.tuttochiaro.offline.model.Language
import com.tuttochiaro.offline.model.TranslationResult
import com.tuttochiaro.offline.model.TranslatorUiState
import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.localized
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TranslatorScreen(
    state: TranslatorUiState,
    onInputChange: (String) -> Unit,
    onContinuousSpeech: (Boolean) -> Unit,
    onCancelSpeech: () -> Unit,
    onSelectSource: (Language?) -> Unit,
    onToggleTarget: (Language) -> Unit,
    onTranslate: () -> Unit,
    onMicClick: () -> Unit,
    onCameraClick: () -> Unit,
    onSpeak: (String, Language) -> Unit,
    onResourcesClick: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val pagerState = rememberPagerState(initialPage = if (state.continuousSpeech) 1 else 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.settledPage) {
        focusManager.clearFocus()
        val continuous = pagerState.settledPage == 1
        if (state.continuousSpeech != continuous) onContinuousSpeech(continuous)
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = TuttoColors.Paper,
        topBar = {
            AppHeader(onResourcesClick = onResourcesClick)
        },
        snackbarHost = {
            AnimatedVisibility(state.message != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = TuttoColors.Ink,
                    contentColor = TuttoColors.White,
                    dismissAction = {
                        IconButton(onClick = onDismissMessage) {
                            Icon(Icons.Default.Close, stringResource(R.string.ui_close_hint), tint = TuttoColors.White)
                        }
                    },
                ) { Text(state.message?.localized().orEmpty()) }
            }
        },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = TuttoColors.Paper,
                contentColor = TuttoColors.Ink,
            ) {
                listOf(R.string.ui_ordinary_translation, R.string.ui_continuous_translation).forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { focusManager.clearFocus(); scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(title)) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val active = pagerState.settledPage == page && !pagerState.isScrollInProgress && state.continuousSpeech == (page == 1)
                if (page == 0) {
                    OrdinaryTranslationPage(
                        state = state,
                        onInputChange = { if (active) onInputChange(it) },
                        onSelectSource = { if (active) onSelectSource(it) },
                        onToggleTarget = { if (active) onToggleTarget(it) },
                        onTranslate = { if (active) { focusManager.clearFocus(); onTranslate() } },
                        onMicClick = { if (active) { focusManager.clearFocus(); onMicClick() } },
                        onCameraClick = { if (active) onCameraClick() },
                        onSpeak = { text, language -> if (active) onSpeak(text, language) },
                        onResourcesClick = onResourcesClick,
                    )
                } else {
                    ContinuousTranslationPage(
                        state = state,
                        enabled = active,
                        onSelectSource = { if (active) onSelectSource(it) },
                        onToggleTarget = { if (active) onToggleTarget(it) },
                        onMicClick = { if (active) onMicClick() },
                        onCancelSpeech = { if (active) onCancelSpeech() },
                        onSpeak = { text, language -> if (active) onSpeak(text, language) },
                        onResourcesClick = onResourcesClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrdinaryTranslationPage(
    state: TranslatorUiState,
    onInputChange: (String) -> Unit,
    onSelectSource: (Language?) -> Unit,
    onToggleTarget: (Language) -> Unit,
    onTranslate: () -> Unit,
    onMicClick: () -> Unit,
    onCameraClick: () -> Unit,
    onSpeak: (String, Language) -> Unit,
    onResourcesClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.ui_ordinary_translation), style = MaterialTheme.typography.headlineSmall, color = TuttoColors.Ink)
            Text(stringResource(R.string.ui_ordinary_help), style = MaterialTheme.typography.bodyMedium, color = TuttoColors.InkSoft)
        }
        item { ModelStatus(state) }
        item { SourceLanguageSelector(state, onSelectSource) }
        item { InputCard(state, onInputChange, onTranslate, onMicClick, onCameraClick) }
        item { state.speechMessage?.let { Text(it.localized(), color = TuttoColors.Tomato) } }
        item { TargetLanguageSelector(state, onToggleTarget) }
        item {
            ResultHeading(
                when {
                    state.isTranslating -> state.translationProgress?.localized()
                        ?: stringResource(R.string.ui_preparing_translation, state.translationModel.label)
                    state.results.isEmpty() -> stringResource(R.string.ui_results_empty)
                    else -> pluralStringResource(R.plurals.ui_results_count, state.results.size, state.results.size)
                },
            )
        }
        if (state.isTranslating) item { ProgressIndicator() }
        items(state.results, key = { it.language.tag }) { result ->
            TranslationCard(
                result = result,
                ttsReady = state.resourceStates.firstOrNull { it.language == result.language }?.ttsReady == true,
                onSpeak = onSpeak,
            )
        }
        item { SettingsLink(onResourcesClick) }
    }
}

@Composable
private fun ContinuousTranslationPage(
    state: TranslatorUiState,
    enabled: Boolean,
    onSelectSource: (Language?) -> Unit,
    onToggleTarget: (Language) -> Unit,
    onMicClick: () -> Unit,
    onCancelSpeech: () -> Unit,
    onSpeak: (String, Language) -> Unit,
    onResourcesClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.ui_continuous_translation), style = MaterialTheme.typography.headlineSmall, color = TuttoColors.Ink)
            Text(
                stringResource(R.string.ui_continuous_help),
                style = MaterialTheme.typography.bodyMedium,
                color = TuttoColors.InkSoft,
            )
        }
        item { ModelStatus(state) }
        item { SourceLanguageSelector(state, onSelectSource) }
        item { TargetLanguageSelector(state, onToggleTarget) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = TuttoColors.White),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.ui_continuous_capture), style = MaterialTheme.typography.titleMedium, color = TuttoColors.Ink)
                    Text(
                        state.speechMessage?.localized() ?: if (state.continuousSessionActive) {
                            stringResource(R.string.ui_processing_speech)
                        } else stringResource(R.string.ui_select_languages_start),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.speechMessage != null) TuttoColors.Tomato else TuttoColors.InkSoft,
                    )
                    ActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        icon = if (state.isListening) Icons.Default.Stop else Icons.Default.Mic,
                        label = when {
                            state.isListening -> stringResource(R.string.ui_finish_capture)
                            state.continuousSessionActive -> stringResource(R.string.ui_cancel_processing)
                            else -> stringResource(R.string.ui_start_continuous)
                        },
                        emphasized = true,
                        enabled = enabled,
                        onClick = if (state.continuousSessionActive && !state.isListening) onCancelSpeech else onMicClick,
                    )
                    if (state.continuousSessionActive && state.isListening) {
                        TextButton(onClick = onCancelSpeech, enabled = enabled) { Text(stringResource(R.string.ui_cancel_round)) }
                    }
                }
            }
        }
        item { ResultHeading(if (state.speechSegments.isEmpty()) stringResource(R.string.ui_history_empty)
            else pluralStringResource(R.plurals.ui_segments_count, state.speechSegments.size, state.speechSegments.size)) }
        items(state.speechSegments, key = { "speech-${it.id}" }) { segment ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ui_segment_heading, segment.id, segment.language?.localizedDisplayName() ?: stringResource(R.string.ui_original)), style = MaterialTheme.typography.labelLarge)
                    Text(segment.original, style = MaterialTheme.typography.bodyLarge)
                    if (!segment.complete) Text(stringResource(R.string.ui_translating), color = TuttoColors.InkSoft)
                    segment.error?.let { Text(it.localized(), color = TuttoColors.Tomato) }
                    segment.translations.forEach { result ->
                        TranslationCard(
                            result = result,
                            ttsReady = !state.continuousSessionActive && state.resourceStates.any { it.language == result.language && it.ttsReady },
                            onSpeak = onSpeak,
                        )
                    }
                }
            }
        }
        item { SettingsLink(onResourcesClick) }
    }
}

@Composable
private fun ModelStatus(state: TranslatorUiState) {
    if (!state.modelReady) Text(state.modelMessage.localized(), color = TuttoColors.Tomato, style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceLanguageSelector(state: TranslatorUiState, onSelectSource: (Language?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.ui_source_language), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.sourceLanguage == null, onClick = { onSelectSource(null) }, label = { Text(stringResource(R.string.ui_auto)) })
            state.commonLanguages.forEach { language ->
                FilterChip(
                    selected = state.sourceLanguage == language,
                    onClick = { onSelectSource(language) },
                    label = { Text(language.localizedDisplayName()) },
                )
            }
        }
        Text(stringResource(R.string.ui_source_help), style = MaterialTheme.typography.bodySmall, color = TuttoColors.InkSoft)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetLanguageSelector(state: TranslatorUiState, onToggleTarget: (Language) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ui_target_languages), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ui_multi_select), style = MaterialTheme.typography.labelMedium, color = TuttoColors.InkSoft)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.commonLanguages.forEach { language ->
                val selected = language in state.selectedTargets
                FilterChip(
                    selected = selected,
                    onClick = { onToggleTarget(language) },
                    label = { Text(language.nativeName) },
                    leadingIcon = if (selected) { { Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp)) } } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TuttoColors.Ink,
                        selectedLabelColor = TuttoColors.White,
                        selectedLeadingIconColor = TuttoColors.Sun,
                        containerColor = TuttoColors.White,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = TuttoColors.PaperDeep,
                        selectedBorderColor = TuttoColors.Ink,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ResultHeading(label: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = TuttoColors.PaperDeep)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TuttoColors.InkSoft)
        HorizontalDivider(Modifier.weight(1f), color = TuttoColors.PaperDeep)
    }
}

@Composable
private fun ProgressIndicator() {
    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = TuttoColors.Tomato, strokeWidth = 3.dp)
    }
}

@Composable
private fun SettingsLink(onResourcesClick: () -> Unit) {
    TextButton(onClick = onResourcesClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.ui_settings_link))
    }
}

@Composable
private fun AppHeader(onResourcesClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TuttoColors.Paper)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(TuttoColors.Tomato, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("T", color = TuttoColors.White, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("TUTTOCHIARO", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp)
                Text(stringResource(R.string.ui_tagline), style = MaterialTheme.typography.labelMedium, color = TuttoColors.InkSoft)
            }
        }
        TextButton(onClick = onResourcesClick) {
            Text(stringResource(R.string.ui_settings), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun InputCard(
    state: TranslatorUiState,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onMicClick: () -> Unit,
    onCameraClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = TuttoColors.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (state.origin) {
                            InputOrigin.TEXT -> Icons.Default.Translate
                            InputOrigin.SPEECH -> Icons.Default.RecordVoiceOver
                            InputOrigin.OCR -> Icons.Default.CameraAlt
                        },
                        null,
                        tint = TuttoColors.Tomato,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        state.sourceLanguage?.let { stringResource(R.string.ui_specified_language, it.localizedDisplayName()) }
                            ?: state.detectedLanguage?.let { stringResource(R.string.ui_detected_language, it.localizedDisplayName()) }
                            ?: stringResource(R.string.ui_auto_detect_language),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.detectedLanguage != null) TuttoColors.Olive else TuttoColors.InkSoft,
                    )
                }
                if (state.input.isNotBlank()) {
                    IconButton(onClick = { onInputChange("") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.ui_clear_input), Modifier.size(19.dp), tint = TuttoColors.InkSoft)
                    }
                }
            }

            TextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                minLines = 3,
                maxLines = 8,
                placeholder = {
                    Text(
                        stringResource(R.string.ui_input_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TuttoColors.InkSoft.copy(alpha = 0.72f),
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onTranslate() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = if (state.isListening) Icons.Default.Stop else Icons.Default.Mic,
                    label = if (state.isListening) stringResource(R.string.ui_finish_capture)
                        else if (state.isRecognizing || state.continuousSessionActive) stringResource(R.string.ui_cancel_processing)
                        else stringResource(R.string.ui_speak),
                    emphasized = true,
                    onClick = onMicClick,
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CameraAlt,
                    label = stringResource(R.string.ui_road_sign),
                    emphasized = false,
                    onClick = onCameraClick,
                )
                IconButton(
                    onClick = onTranslate,
                    enabled = state.input.isNotBlank() && !state.isTranslating && !state.continuousSessionActive && !state.isListening && !state.isRecognizing,
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            if (state.input.isNotBlank()) TuttoColors.Ink else TuttoColors.PaperDeep,
                            CircleShape,
                        ),
                ) {
                    if (state.isTranslating) {
                        CircularProgressIndicator(Modifier.size(21.dp), color = TuttoColors.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Translate, stringResource(R.string.ui_translate_now), tint = TuttoColors.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    emphasized: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (emphasized) TuttoColors.Tomato else TuttoColors.PaperDeep,
            contentColor = if (emphasized) TuttoColors.White else TuttoColors.Ink,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.size(7.dp))
        Text(label, maxLines = 2)
    }
}

@Composable
private fun TranslationCard(
    result: TranslationResult,
    ttsReady: Boolean,
    onSpeak: (String, Language) -> Unit,
) {
    val accent = when (result.language) {
        Language.CHINESE -> TuttoColors.Tomato
        Language.ENGLISH -> TuttoColors.Ink
        Language.SPANISH -> Color(0xFFC58B23)
        Language.ITALIAN -> TuttoColors.Olive
        else -> TuttoColors.InkSoft
    }
    val speakDescription = stringResource(R.string.ui_speak_translation, result.language.localizedDisplayName())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = TuttoColors.White),
        border = BorderStroke(1.dp, TuttoColors.PaperDeep),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.size(width = 6.dp, height = 132.dp).background(accent))
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp, top = 14.dp, end = 10.dp, bottom = 15.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(result.language.nativeName, style = MaterialTheme.typography.labelLarge, color = accent)
                         Text(result.language.localizedDisplayName(), style = MaterialTheme.typography.labelMedium, color = TuttoColors.InkSoft)
                    }
                    IconButton(
                        onClick = { onSpeak(result.text, result.language) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(accent.copy(alpha = if (ttsReady) 0.12f else 0.06f), CircleShape)
                            .semantics { contentDescription = speakDescription },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.VolumeUp,
                            null,
                            tint = if (ttsReady) accent else TuttoColors.InkSoft,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(result.text, style = MaterialTheme.typography.bodyLarge, color = TuttoColors.Ink)
            }
        }
    }
}
