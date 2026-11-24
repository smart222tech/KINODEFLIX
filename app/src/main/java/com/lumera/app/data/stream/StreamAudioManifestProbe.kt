package com.lumera.app.data.stream

import android.util.Xml
import com.lumera.app.data.model.stremio.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Independently inspects HLS/DASH manifests for declared audio-language tracks.
 * It never downloads media segments and never treats subtitle tracks as audio.
 */
object StreamAudioManifestProbe {
    private const val MAX_MANIFEST_BYTES = 512L * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val hlsAttribute = Regex(
        """([A-Z0-9-]+)=(?:"([^"]*)"|([^,]*))(?:,|$)"""
    )

    suspend fun enrich(stream: Stream): Stream = withContext(Dispatchers.IO) {
        val url = stream.url?.trim().orEmpty()
        if (url.isBlank()) return@withContext stream

        val path = url.substringBefore('?').lowercase()
        val language = when {
            path.endsWith(".m3u8") -> fetchManifest(url)?.let(::detectHlsGermanAudio)
            path.endsWith(".mpd") -> fetchManifest(url)?.let(::detectDashGermanAudio)
            else -> null
        }

        if (language == "de") stream.copy(language = "de") else stream
    }

    private fun fetchManifest(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-${MAX_MANIFEST_BYTES - 1}")
            .header("Accept", "application/vnd.apple.mpegurl, application/dash+xml, */*")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                body.source().readUtf8(MAX_MANIFEST_BYTES)
                    .takeIf { text ->
                        val trimmed = text.trimStart()
                        trimmed.startsWith("#EXTM3U") ||
                            trimmed.startsWith("<?xml") ||
                            trimmed.startsWith("<MPD") ||
                            trimmed.startsWith("<")
                    }
            }
        }.getOrNull()
    }

    private fun detectHlsGermanAudio(manifest: String): String? {
        val found = manifest.lineSequence()
            .filter { it.startsWith("#EXT-X-MEDIA:", ignoreCase = true) }
            .mapNotNull { line ->
                val attributes = hlsAttribute.findAll(line.substringAfter(':'))
                    .associate { match ->
                        match.groupValues[1].uppercase() to
                            match.groupValues[2].ifBlank { match.groupValues[3] }.trim()
                    }
                if (!attributes["TYPE"].equals("AUDIO", ignoreCase = true)) {
                    null
                } else {
                    attributes["LANGUAGE"] ?: attributes["NAME"]
                }
            }
            .any(::isGermanLanguage)

        return if (found) "de" else null
    }

    private fun detectDashGermanAudio(mpd: String): String? {
        return runCatching {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(StringReader(mpd))
            }
            var germanFound = false
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG &&
                    parser.name.equals("AdaptationSet", ignoreCase = true)
                ) {
                    val contentType = parser.getAttributeValue(null, "contentType").orEmpty()
                    val mimeType = parser.getAttributeValue(null, "mimeType").orEmpty()
                    val isAudio = contentType.equals("audio", true) ||
                        mimeType.startsWith("audio/", true)
                    if (isAudio) {
                        val language = parser.getAttributeValue(null, "lang")
                            ?: parser.getAttributeValue(
                                "http://www.w3.org/XML/1998/namespace",
                                "lang"
                            )
                        if (isGermanLanguage(language)) germanFound = true
                    }
                }
                parser.next()
            }
            if (germanFound) "de" else null
        }.getOrNull()
    }

    private fun isGermanLanguage(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase()?.replace('_', '-') ?: return false
        return normalized == "de" ||
            normalized.startsWith("de-") ||
            normalized == "deu" ||
            normalized == "ger" ||
            normalized.contains("german") ||
            normalized.contains("deutsch")
    }
}
