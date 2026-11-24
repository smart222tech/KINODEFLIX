package com.lumera.app.data.stream

import com.lumera.app.data.model.stremio.Stream

/**
 * Ranks addon-provided language hints for a German-first source picker.
 * A filename/title is a hint, not proof of the embedded audio tracks.
 */
object GermanAudioLanguage {
    const val GERMAN_CONFIRMED = 3
    const val MULTI_LANGUAGE = 2
    const val UNKNOWN = 1
    const val NON_GERMAN = 0

    private val german = Regex(
        """(?i)(?:\bdeutsch\b|\bgerman\b|\bdeu\b|\bger\b|\bde[-_]de\b|\bde[-_]at\b|\bde[-_]ch\b|\[de\]|\(de\))"""
    )
    private val subtitleOnly = Regex(
        """(?i)(?:\b(german|deutsch)\s+(subtitles?|subs?)\b|\b(de|deu|ger)[ ._-]?(sub|subs|subtitle)\b|\bvostde\b)"""
    )
    private val explicitGermanAudio = Regex(
        """(?i)\b(german|deutsch)\s+(dub|audio|dubbing)\b"""
    )
    private val multi = Regex(
        """(?i)(?:\bdual[ ._-]?audio\b|\bmulti[ ._-]?audio\b|\bmehrsprachig\b|\bmultiple[ ._-]?audio\b)"""
    )
    private val nonGerman = Regex(
        """(?i)(?:\bohne deutsch\b|\bno german\b|\bnot german\b|\benglish only\b|\bonly english\b)"""
    )

    fun rank(stream: Stream): Int {
        val explicitLanguage = stream.language.orEmpty()
        val text = listOfNotNull(
            explicitLanguage,
            stream.name,
            stream.title,
            stream.description,
            stream.behaviorHints?.filename
        ).joinToString(" ")

        if (nonGerman.containsMatchIn(text)) return NON_GERMAN
        if (subtitleOnly.containsMatchIn(text) && !explicitGermanAudio.containsMatchIn(text)) return UNKNOWN
        if (german.containsMatchIn(explicitLanguage)) return GERMAN_CONFIRMED
        if (german.containsMatchIn(text)) return GERMAN_CONFIRMED
        if (multi.containsMatchIn(text)) return MULTI_LANGUAGE
        return UNKNOWN
    }

    fun label(stream: Stream): String = when (rank(stream)) {
        GERMAN_CONFIRMED -> "Deutsch-Hinweis"
        MULTI_LANGUAGE -> "Mehrsprachig"
        NON_GERMAN -> "Kein Deutsch-Hinweis"
        else -> "Sprache unbekannt"
    }
}
