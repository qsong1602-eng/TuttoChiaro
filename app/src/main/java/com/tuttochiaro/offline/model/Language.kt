package com.tuttochiaro.offline.model

import java.util.Locale

/** The catalog is shared by translation, ASR hints and language detection. */
enum class Language(
    val tag: String,
    val mlKitCode: String,
    val displayName: String,
    val nativeName: String,
    val englishName: String,
    val previewText: String,
) {
    CHINESE("zh-CN", "zh", "中文", "中文", "Chinese", "你好，请问去机场的车站在哪里？谢谢。"),
    ENGLISH("en-US", "en", "英语", "English", "English", "Hello. Where is the bus stop for the airport? Thank you."),
    SPANISH("es-ES", "es", "西班牙语", "Español", "Spanish", "Hola. ¿Dónde está la parada del autobús al aeropuerto? Gracias."),
    ITALIAN("it-IT", "it", "意大利语", "Italiano", "Italian", "Buongiorno. Dov'è la fermata dell'autobus per l'aeroporto? Grazie."),
    FRENCH("fr-FR", "fr", "法语", "Français", "French", "Bonjour. Où est l'arrêt de bus pour l'aéroport ? Merci."),
    GERMAN("de-DE", "de", "德语", "Deutsch", "German", "Guten Tag. Wo ist die Bushaltestelle zum Flughafen? Danke."),
    PORTUGUESE("pt-PT", "pt", "葡萄牙语", "Português", "Portuguese", "Olá. Onde fica a paragem do autocarro para o aeroporto? Obrigado."),
    JAPANESE("ja-JP", "ja", "日语", "日本語", "Japanese", "こんにちは。空港行きのバス停はどこですか。ありがとうございます。"),
    KOREAN("ko-KR", "ko", "韩语", "한국어", "Korean", "안녕하세요. 공항으로 가는 버스 정류장은 어디인가요? 감사합니다."),
    RUSSIAN("ru-RU", "ru", "俄语", "Русский", "Russian", "Здравствуйте. Где остановка автобуса до аэропорта? Спасибо."),
    THAI("th-TH", "th", "泰语", "ไทย", "Thai", "สวัสดี ป้ายรถเมล์ไปสนามบินอยู่ที่ไหน ขอบคุณ"),
    VIETNAMESE("vi-VN", "vi", "越南语", "Tiếng Việt", "Vietnamese", "Xin chào. Trạm xe buýt đi sân bay ở đâu? Cảm ơn.");

    val locale: Locale get() = Locale.forLanguageTag(tag)
    val translationName: String get() = if (this == CHINESE) "Simplified Chinese" else englishName

    companion object {
        val all = entries.toList()
        val defaults = listOf(CHINESE, ENGLISH, SPANISH, ITALIAN)

        fun fromTag(tag: String?): Language? {
            val normalized = tag?.trim()?.lowercase(Locale.ROOT)
                ?.replace('_', '-')?.substringBefore('-').orEmpty()
            if (normalized == "cmn") return CHINESE
            return entries.firstOrNull {
                normalized == it.mlKitCode || normalized == it.englishName.lowercase(Locale.ROOT)
            }
        }

        /** Stable order and migration fallback for absent/stale preference values. */
        fun commonFromTags(tags: Set<String>?): List<Language> {
            if (tags == null) return defaults
            val selected = tags.mapNotNull(::fromTag).toSet()
            return all.filter { it in selected }.takeIf { it.size >= 2 } ?: defaults
        }
    }
}
