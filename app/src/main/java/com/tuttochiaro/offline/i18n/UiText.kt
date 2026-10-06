package com.tuttochiaro.offline.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.tuttochiaro.offline.model.Language
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/** Resolve messages at display time, so retained state follows a new UI locale. */
@ConsistentCopyVisibility
data class UiText private constructor(@StringRes val resourceId: Int, val arguments: List<Any>, val rawText: String?) {
    constructor(@StringRes resourceId: Int, vararg arguments: Any) : this(resourceId, arguments.toList(), null)
    fun resolve(context: Context): String {
        rawText?.let { return it }
        val configured = context.resources.configuration.locales
        val locale = (0 until configured.size()).map { configured[it] }
            .firstOrNull { AppLanguage.fromTag(it.language) != AppLanguage.SYSTEM } ?: java.util.Locale.ENGLISH
        val resolved = arguments.map {
            when (it) {
                is UiText -> it.resolve(context)
                is Language -> it.locale.getDisplayLanguage(locale)
                else -> it
            }
        }
        return context.getString(resourceId, *resolved.toTypedArray())
    }
    companion object {
        fun literal(text: String) = UiText(resourceId = 0, arguments = emptyList(), rawText = text)
    }
}

@Composable
fun UiText.localized(): String {
    LocalConfiguration.current
    return resolve(LocalContext.current)
}

interface LocalizedFailure { val uiText: UiText }
class UiStateException(override val uiText: UiText, cause: Throwable? = null) :
    IllegalStateException("UI message ${uiText.resourceId}", cause), LocalizedFailure
class UiArgumentException(override val uiText: UiText) :
    IllegalArgumentException("UI message ${uiText.resourceId}"), LocalizedFailure
@OptIn(ExperimentalContracts::class)
inline fun checkUi(value: Boolean, message: () -> UiText) {
    contract { returns() implies value }
    if (!value) throw UiStateException(message())
}
@OptIn(ExperimentalContracts::class)
inline fun requireUi(value: Boolean, message: () -> UiText) {
    contract { returns() implies value }
    if (!value) throw UiArgumentException(message())
}
fun Throwable.toUiText(@StringRes fallback: Int): UiText =
    (this as? LocalizedFailure)?.uiText ?: message?.takeIf { it.isNotBlank() }?.let(UiText::literal) ?: UiText(fallback)
