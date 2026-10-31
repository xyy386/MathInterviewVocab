package com.xingyiyao.mathinterviewvocab.audio

import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AudioEntry(val text: String, val accent: String, val file: String, val sha256: String, val bytes: Long)

@Serializable
data class AudioCoverage(
    val complete: Boolean,
    val expectedEntries: Int,
    val availableEntries: Int,
    val missingEntries: Int,
    val failures: List<String>,
)

@Serializable
private data class AudioManifest(val version: Int, val entries: Map<String, AudioEntry>, val coverage: AudioCoverage)

/** 索引只接受 APK 中预生成的两种口音；缺失选区不能伪装成另一条音频。 */
class AudioInventory private constructor(private val entries: Map<String, AudioEntry>) {
    val size: Int get() = entries.size

    fun find(text: String, accent: String): AudioEntry? {
        if (accent !in accents) return null
        val cleaned = VocabularyCore.sanitizeSelection(text) ?: return null
        val normalized = VocabularyCore.normalizeAudioText(cleaned)
        return entries[key(accent, normalized)]?.takeIf { it.text == normalized && it.accent == accent }
    }

    fun allEntries(): Collection<AudioEntry> = entries.values

    companion object {
        val accents = setOf("en-US", "en-GB")
        private val digestPattern = Regex("[0-9a-f]{64}")
        private val json = Json { ignoreUnknownKeys = true }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        fun key(accent: String, text: String): String = sha256("$accent\n$text".toByteArray(Charsets.UTF_8))

        fun parse(source: String): AudioInventory {
            val manifest = json.decodeFromString<AudioManifest>(source)
            require(manifest.version == 1 && manifest.entries.size == 2328) { "离线音频索引版本或数量错误。" }
            val coverage = manifest.coverage
            require(coverage.complete && coverage.expectedEntries == 2328 && coverage.availableEntries == 2328 &&
                coverage.missingEntries == 0 && coverage.failures.isEmpty()) { "离线音频资源不完整。" }
            manifest.entries.forEach { (entryKey, entry) ->
                require(digestPattern.matches(entryKey) && entry.file == "$entryKey.mp3" && entry.accent in accents &&
                    digestPattern.matches(entry.sha256) && entry.bytes in 1..5_000_000 &&
                    key(entry.accent, entry.text) == entryKey) { "离线音频索引校验失败。" }
            }
            return AudioInventory(manifest.entries)
        }
    }
}
