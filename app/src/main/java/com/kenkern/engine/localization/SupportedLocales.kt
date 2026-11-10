package com.kenkern.engine.localization

/**
 * Locales officially supported by the KENKERN editor UI.
 *
 * All user-facing strings live in Android string resources
 * (`values/values-pt-rBR/values-pt-rPT/values-fr` string files).
 * Identifiers, APIs and internal engine names are never localized.
 */
object SupportedLocales {

    /** BCP-47 tags of all shipped translations (English is the default/base). */
    val tags: List<String> = listOf("en", "pt-BR", "pt-PT", "fr")

    const val DEFAULT_TAG: String = "en"
}
