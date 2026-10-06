package com.tuttochiaro.offline

import com.tuttochiaro.offline.R
import com.tuttochiaro.offline.i18n.*

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tuttochiaro.offline.ui.OfflineResourcesScreen
import com.tuttochiaro.offline.ui.OcrCameraScreen
import com.tuttochiaro.offline.ui.TranslatorScreen
import com.tuttochiaro.offline.ui.TuttoChiaroTheme

class MainActivity : AppCompatActivity() {
    private val viewModel: TranslatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TuttoChiaroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    App(viewModel)
                }
            }
        }
    }

    override fun onStop() {
        viewModel.stopListening()
        viewModel.textToSpeech.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.setPermissionState(
            microphone = hasPermission(Manifest.permission.RECORD_AUDIO),
            camera = hasPermission(Manifest.permission.CAMERA),
        )
        viewModel.refreshResources()
    }

    @Composable
    private fun App(viewModel: TranslatorViewModel) {
        val state by viewModel.uiState.collectAsState()
        LocalConfiguration.current // Refresh the picker after OS-level locale changes.
        val appLanguage = AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag())

        val microphoneLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            viewModel.setPermissionState(microphone = granted)
            if (granted) viewModel.startListening()
            else viewModel.showMessage(UiText(R.string.rt_mic_denied))
        }
        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            viewModel.setPermissionState(camera = granted)
            if (granted) viewModel.showCamera()
            else viewModel.showMessage(UiText(R.string.rt_camera_denied))
        }

        BackHandler(enabled = state.showCamera || state.showResources) {
            if (state.showCamera) viewModel.closeCamera()
            else viewModel.showResources(false)
        }

        when {
            state.showCamera -> OcrCameraScreen(viewModel = viewModel)
            state.showResources -> OfflineResourcesScreen(
                state = state,
                appLanguage = appLanguage,
                onSelectAppLanguage = { language ->
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
                },
                onBack = { viewModel.showResources(false) },
                onRefresh = viewModel::refreshResources,
                onPrepareModel = viewModel::prepareBundledModel,
                onPrepareSpeech = viewModel::prepareSpeechModel,
                onSelectTranslation = viewModel::selectTranslationModel,
                onSelectSpeech = viewModel::selectSpeechModel,
                onToggleCommonLanguage = viewModel::toggleCommonLanguage,
                onTtsRate = viewModel::setTtsRate,
                onTtsVoice = viewModel::selectTtsVoice,
                onPreviewTts = viewModel::previewTts,
                onStopTts = { viewModel.textToSpeech.stop() },
                onInstallTts = {
                    launchSafely(
                        viewModel.textToSpeech.installVoiceDataIntent(),
                        UiText(R.string.rt_no_tts_engine),
                    )
                },
                onDismissMessage = viewModel::clearMessage,
            )
            else -> TranslatorScreen(
                state = state,
                onInputChange = viewModel::updateInput,
                onContinuousSpeech = viewModel::selectContinuousSpeech,
                onCancelSpeech = viewModel::stopListening,
                onSelectSource = viewModel::selectSource,
                onToggleTarget = viewModel::toggleTarget,
                onTranslate = { viewModel.translateNow() },
                onMicClick = {
                    if (state.isListening) viewModel.finishListening()
                    else if (state.isRecognizing || state.continuousSessionActive) viewModel.stopListening()
                    else if (state.microphonePermissionGranted) viewModel.startListening()
                    else microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onCameraClick = {
                    if (state.cameraPermissionGranted) viewModel.showCamera()
                    else cameraLauncher.launch(Manifest.permission.CAMERA)
                },
                onSpeak = viewModel::speak,
                onResourcesClick = { viewModel.showResources(true) },
                onDismissMessage = viewModel::clearMessage,
            )
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun launchSafely(intent: Intent, message: UiText) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage(message)
        } catch (_: SecurityException) {
            viewModel.showMessage(message)
        }
    }
}
