package com.tuttochiaro.offline.i18n

/** Independent of source, target, ASR and favorite language preferences. */
enum class AppLanguage(val tag: String, val nativeLabel: String) {
    SYSTEM("", ""), ENGLISH("en", "English"), CHINESE("zh", "中文"),
    SPANISH("es", "Español"), ITALIAN("it", "Italiano");
    companion object {
        fun fromTag(tag: String?): AppLanguage {
            val language = tag.orEmpty().replace('_', '-').substringBefore('-').lowercase(java.util.Locale.ROOT)
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}
