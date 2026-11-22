package com.xingyiyao.mathinterviewvocab.core

import java.text.Normalizer
import java.util.Locale

data class MathSegment(
    val text: String,
    val latex: String?,
    val display: Boolean,
    val source: String,
)

/**
 * 与桌面版同源的 LaTeX 分段器：只识别 `\(...\)` 与 `\[...\]`，
 * 绝不把正文中的普通下划线或反引号字符猜成数学公式。
 */
object MathTextDocument {
    const val SPEECH_MATH_BLOCK_MESSAGE =
        "所选内容含数学公式，暂无对应内置语音；可选取英文单词或朗读完整词条。"

    private val pattern = Regex("""\\\(([\s\S]*?)\\\)|\\\[([\s\S]*?)\\\]""")
    private val delimiterEscapePattern = Regex("""\\[()\[\]{}]""")
    private val notationSyntaxPattern = Regex("""[_^{}]""")

    fun segments(source: String): List<MathSegment> {
        val result = mutableListOf<MathSegment>()
        var cursor = 0
        for (match in pattern.findAll(source)) {
            val start = match.range.first
            if (start > cursor) {
                val plain = source.substring(cursor, start).replace("`", "")
                result += MathSegment(text = plain, latex = null, display = false, source = plain)
            }
            val blockLatex = match.groups[2]?.value
            val display = blockLatex != null
            val latex = blockLatex ?: match.groups[1]?.value.orEmpty()
            result += MathSegment(text = match.value, latex = latex, display = display, source = match.value)
            cursor = match.range.last + 1
        }
        if (cursor < source.length) {
            val plain = source.substring(cursor).replace("`", "")
            result += MathSegment(text = plain, latex = null, display = false, source = plain)
        }
        return result
    }

    fun containsMath(source: String): Boolean = pattern.containsMatchIn(source)

    fun mathSpeechBlockMessage(text: String): String? =
        if (containsMath(text)) SPEECH_MATH_BLOCK_MESSAGE else null

    /**
     * 规范化搜索文本（NFKC + 清除 `\( \) \[ \] \{ \}` 与 `_ ^ { }` + 小写），
     * 使 `H_0`、`H₀`、`H0` 可互相检索。
     */
    fun searchText(source: String): String {
        val normalized = Normalizer.normalize(source, Normalizer.Form.NFKC)
        return normalized
            .replace(delimiterEscapePattern, "")
            .replace(notationSyntaxPattern, "")
            .lowercase(Locale.ROOT)
    }
}
