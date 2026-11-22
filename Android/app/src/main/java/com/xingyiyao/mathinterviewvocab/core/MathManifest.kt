package com.xingyiyao.mathinterviewvocab.core

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MathAssetEntry(
    val id: String,
    val tex: String,
    val display: Boolean,
    val widthEm: Double,
    val heightEm: Double,
    val depthEm: Double,
    val svg: String,
    val svgSHA256: String,
)

@Serializable
data class MathManifest(
    val schema: Int,
    val engine: String,
    val termsSHA256: String? = null,
    val entries: List<MathAssetEntry>,
) {
    fun lookup(latex: String, display: Boolean): MathAssetEntry? =
        entries.firstOrNull { it.tex == latex && it.display == display }

    companion object {
        const val EXPECTED_SCHEMA = 1
        const val EXPECTED_ENGINE = "MathJax 4.1.3"
        private val sha256Pattern = Regex("[0-9a-f]{64}")
        private val jsonFormat = Json { ignoreUnknownKeys = true }

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        fun parseAndValidate(json: String, expectedTermsSha: String? = null): MathManifest {
            val manifest = jsonFormat.decodeFromString<MathManifest>(json)
            require(manifest.schema == EXPECTED_SCHEMA) { "公式索引 schema 无效：${manifest.schema}" }
            require(manifest.engine == EXPECTED_ENGINE) { "公式引擎版本不匹配：${manifest.engine}" }
            if (expectedTermsSha != null) {
                require(sha256Pattern.matches(expectedTermsSha) && manifest.termsSHA256 == expectedTermsSha) {
                    "公式索引与当前词库摘要不一致。"
                }
            }
            val seen = HashSet<Pair<String, Boolean>>(manifest.entries.size)
            for (entry in manifest.entries) {
                require(sha256Pattern.matches(entry.id)) { "公式 ID 格式无效：${entry.id}" }
                require(entry.svg == "${entry.id}.svg") { "公式 SVG 文件名异常：${entry.svg}" }
                require(sha256Pattern.matches(entry.svgSHA256)) { "公式 SVG 摘要格式无效：${entry.id}" }
                require(entry.widthEm.isFinite() && entry.widthEm > 0.0) { "公式宽度无效：${entry.id}" }
                require(entry.heightEm.isFinite() && entry.heightEm > 0.0) { "公式高度无效：${entry.id}" }
                require(entry.depthEm.isFinite() && entry.depthEm >= 0.0) { "公式基线深度无效：${entry.id}" }
                require(seen.add(entry.tex to entry.display)) { "存在重复公式条目：${entry.tex} (display=${entry.display})" }
            }
            return manifest
        }
    }
}

interface MathAssetStore {
    fun entry(latex: String, display: Boolean): MathAssetEntry?
    fun document(entry: MathAssetEntry): MathSvgDocument?
}
