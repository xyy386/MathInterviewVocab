package com.xingyiyao.mathinterviewvocab.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

sealed interface MathPathOp {
    data class MoveTo(val x: Float, val y: Float) : MathPathOp
    data class LineTo(val x: Float, val y: Float) : MathPathOp
    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
    ) : MathPathOp

    data class QuadTo(val x1: Float, val y1: Float, val x: Float, val y: Float) : MathPathOp
    data object Close : MathPathOp
}

data class MathSvgDocument(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float,
    val ops: List<MathPathOp>,
)

/**
 * 纯 Kotlin MathJax SVG 矢量解析器。
 * 1. 使用引号感知标签扫描器，安全容忍 `data-latex="f(a)f(b)<0"` 等属性值内未转义 `<`；
 * 2. 拒绝 DOCTYPE / ENTITY / CDATA 及任何未知扩展元素（defs/use/script/text 等）；
 * 3. 将全部 `<g>` / `<path>` / `<rect>` 仿射变换与相对路径指令预先烘焙到根 `viewBox` 坐标系。
 */
object MathSvgParser {
    private data class ElementFrame(val tag: String, val matrix: Affine2D)

    private data class Affine2D(
        val a: Float = 1f,
        val b: Float = 0f,
        val c: Float = 0f,
        val d: Float = 1f,
        val e: Float = 0f,
        val f: Float = 0f,
    ) {
        operator fun times(o: Affine2D): Affine2D = Affine2D(
            a = a * o.a + c * o.b,
            b = b * o.a + d * o.b,
            c = a * o.c + c * o.d,
            d = b * o.c + d * o.d,
            e = a * o.e + c * o.f + e,
            f = b * o.e + d * o.f + f,
        )

        fun mapX(x: Float, y: Float): Float = a * x + c * y + e
        fun mapY(x: Float, y: Float): Float = b * x + d * y + f
    }

    private val allowedTags = setOf("svg", "g", "path", "rect")
    private val unsupportedTransformPattern = Regex("""([a-zA-Z]+)\s*\(([^)]*)\)""")
    private val numberSplitPattern = Regex("""[\s,]+""")

    fun parse(svgText: String): MathSvgDocument {
        val stack = ArrayDeque<ElementFrame>()
        val ops = mutableListOf<MathPathOp>()
        var rootViewBox: FloatArray? = null
        var seenRootSvg = false

        var pos = 0
        val len = svgText.length
        while (pos < len) {
            val ch = svgText[pos]
            if (ch != '<') {
                if (!ch.isWhitespace()) {
                    throw IllegalArgumentException("SVG 包含非预期的文本内容：'${svgText.substring(pos, minOf(pos + 16, len))}'")
                }
                pos++
                continue
            }

            // 处理 XML 声明 / 注释 / 安全禁令
            if (svgText.startsWith("<?", pos)) {
                val endPi = svgText.indexOf("?>", pos + 2)
                require(endPi >= 0) { "未闭合的 XML 处理指令。" }
                pos = endPi + 2
                continue
            }
            if (svgText.startsWith("<!--", pos)) {
                val endComment = svgText.indexOf("-->", pos + 4)
                require(endComment >= 0) { "未闭合的 XML 注释。" }
                pos = endComment + 3
                continue
            }
            if (svgText.startsWith("<!", pos)) {
                throw IllegalArgumentException("SVG 不允许 DOCTYPE / ENTITY / CDATA 声明。")
            }

            // 闭合标签 </tag>
            if (pos + 1 < len && svgText[pos + 1] == '/') {
                pos += 2
                pos = skipWhitespace(svgText, pos)
                val nameStart = pos
                while (pos < len && isNameChar(svgText[pos])) pos++
                val tag = svgText.substring(nameStart, pos)
                require(tag.isNotEmpty()) { "无效的闭合标签。" }
                pos = skipWhitespace(svgText, pos)
                require(pos < len && svgText[pos] == '>') { "闭合标签 </$tag> 语法错误。" }
                pos++
                val top = stack.removeLastOrNull()
                    ?: throw IllegalArgumentException("多余的闭合标签 </$tag>。")
                require(top.tag == tag) { "标签嵌套不匹配：期望 </${top.tag}>，实际 </$tag>。" }
                continue
            }

            // 开始标签 <tag ...> 或 <tag .../>
            pos++
            val nameStart = pos
            while (pos < len && isNameChar(svgText[pos])) pos++
            val rawTag = svgText.substring(nameStart, pos)
            val tag = rawTag.substringAfter(':')
            require(tag.isNotEmpty() && tag in allowedTags) { "不支持的 SVG 元素：<$rawTag>" }

            if (stack.isEmpty()) {
                require(!seenRootSvg && tag == "svg") { "SVG 根元素必须是唯一的 <svg>，实际为 <$rawTag>。" }
                seenRootSvg = true
            } else {
                require(tag != "svg") { "不支持嵌套 <svg> 元素。" }
            }

            val attrs = LinkedHashMap<String, String>()
            var selfClosing = false
            while (true) {
                pos = skipWhitespace(svgText, pos)
                require(pos < len) { "未闭合的标签 <$rawTag>。" }
                if (svgText[pos] == '>') {
                    pos++
                    break
                }
                if (svgText[pos] == '/' && pos + 1 < len && svgText[pos + 1] == '>') {
                    selfClosing = true
                    pos += 2
                    break
                }
                val attrStart = pos
                while (pos < len && isAttrNameChar(svgText[pos])) pos++
                val attrName = svgText.substring(attrStart, pos)
                require(attrName.isNotEmpty()) { "标签 <$rawTag> 属性名无效。" }
                pos = skipWhitespace(svgText, pos)
                require(pos < len && svgText[pos] == '=') { "属性 $attrName 缺少 '='。" }
                pos++
                pos = skipWhitespace(svgText, pos)
                require(pos < len && (svgText[pos] == '"' || svgText[pos] == '\'')) {
                    "属性 $attrName 的值必须使用引号。"
                }
                val quote = svgText[pos]
                pos++
                val valStart = pos
                while (pos < len && svgText[pos] != quote) pos++
                require(pos < len) { "属性 $attrName 缺少闭合引号。" }
                val rawValue = svgText.substring(valStart, pos)
                pos++ // skip closing quote
                // data-latex 仅含预览来源并可能包含原始 '<'，不用于几何解析
                attrs[attrName] = if (attrName == "data-latex") rawValue else decodeEntities(rawValue)
            }

            val parentMatrix = stack.lastOrNull()?.matrix ?: Affine2D()
            val elementMatrix = attrs["transform"]?.let { parentMatrix * parseTransform(it) } ?: parentMatrix

            when (tag) {
                "svg" -> {
                    val vb = attrs["viewBox"] ?: throw IllegalArgumentException("<svg> 缺少 viewBox 属性。")
                    rootViewBox = parseViewBox(vb)
                }
                "path" -> {
                    val d = attrs["d"]
                    if (!d.isNullOrBlank()) {
                        parsePathData(d, elementMatrix, ops)
                    }
                }
                "rect" -> {
                    parseRect(attrs, elementMatrix, ops)
                }
            }

            if (!selfClosing) {
                stack.addLast(ElementFrame(tag, elementMatrix))
            }
        }

        require(seenRootSvg && stack.isEmpty()) { "SVG 标签未正确闭合。" }
        val vb = rootViewBox ?: throw IllegalArgumentException("SVG 缺少有效 viewBox。")
        return MathSvgDocument(
            minX = vb[0],
            minY = vb[1],
            width = vb[2],
            height = vb[3],
            ops = ops,
        )
    }

    private fun parseViewBox(text: String): FloatArray {
        val parts = text.trim().split(numberSplitPattern).filter { it.isNotEmpty() }
        require(parts.size == 4) { "viewBox 格式无效：$text" }
        val values = FloatArray(4) { i ->
            parts[i].toFloatOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("viewBox 数值无效：$text")
        }
        require(values[2] > 0f && values[3] > 0f) { "viewBox 宽高必须为正：$text" }
        return values
    }

    private fun parseTransform(text: String): Affine2D {
        var result = Affine2D()
        var lastEnd = 0
        for (match in unsupportedTransformPattern.findAll(text)) {
            require(text.substring(lastEnd, match.range.first).all { it.isWhitespace() || it == ',' }) {
                "无法解析 transform 字符串：$text"
            }
            lastEnd = match.range.last + 1
            val fn = match.groupValues[1]
            val args = match.groupValues[2].trim()
                .takeIf { it.isNotEmpty() }
                ?.split(numberSplitPattern)
                ?.map {
                    it.toFloatOrNull()?.takeIf { v -> v.isFinite() }
                        ?: throw IllegalArgumentException("transform 参数无效：$text")
                }
                ?: emptyList()
            val step = when (fn) {
                "translate" -> {
                    require(args.size in 1..2) { "translate 参数数量无效：$text" }
                    Affine2D(e = args[0], f = args.getOrElse(1) { 0f })
                }
                "scale" -> {
                    require(args.size in 1..2) { "scale 参数数量无效：$text" }
                    Affine2D(a = args[0], d = args.getOrElse(1) { args[0] })
                }
                "matrix" -> {
                    require(args.size == 6) { "matrix 参数数量无效：$text" }
                    Affine2D(args[0], args[1], args[2], args[3], args[4], args[5])
                }
                "rotate" -> {
                    require(args.size == 1 || args.size == 3) { "rotate 参数数量无效：$text" }
                    val rad = Math.toRadians(args[0].toDouble())
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    val rot = Affine2D(a = cosA, b = sinA, c = -sinA, d = cosA)
                    if (args.size == 3) {
                        val cx = args[1]
                        val cy = args[2]
                        Affine2D(e = cx, f = cy) * rot * Affine2D(e = -cx, f = -cy)
                    } else rot
                }
                "skewX" -> {
                    require(args.size == 1) { "skewX 参数数量无效：$text" }
                    Affine2D(c = tan(Math.toRadians(args[0].toDouble())).toFloat())
                }
                "skewY" -> {
                    require(args.size == 1) { "skewY 参数数量无效：$text" }
                    Affine2D(b = tan(Math.toRadians(args[0].toDouble())).toFloat())
                }
                else -> throw IllegalArgumentException("不支持的 transform 函数：$fn")
            }
            result *= step
        }
        require(text.substring(lastEnd).all { it.isWhitespace() || it == ',' }) {
            "无法解析 transform 末尾内容：$text"
        }
        return result
    }

    private fun parseRect(attrs: Map<String, String>, matrix: Affine2D, out: MutableList<MathPathOp>) {
        val x = (attrs["x"]?.toFloatOrNull() ?: 0f).also { require(it.isFinite()) }
        val y = (attrs["y"]?.toFloatOrNull() ?: 0f).also { require(it.isFinite()) }
        val w = attrs["width"]?.toFloatOrNull() ?: 0f
        val h = attrs["height"]?.toFloatOrNull() ?: 0f
        require(w.isFinite() && w >= 0f && h.isFinite() && h >= 0f) { "rect 尺寸无效" }
        if (w == 0f || h == 0f) return
        out += MathPathOp.MoveTo(matrix.mapX(x, y), matrix.mapY(x, y))
        out += MathPathOp.LineTo(matrix.mapX(x + w, y), matrix.mapY(x + w, y))
        out += MathPathOp.LineTo(matrix.mapX(x + w, y + h), matrix.mapY(x + w, y + h))
        out += MathPathOp.LineTo(matrix.mapX(x, y + h), matrix.mapY(x, y + h))
        out += MathPathOp.Close
    }

    private fun parsePathData(d: String, matrix: Affine2D, out: MutableList<MathPathOp>) {
        val reader = PathNumberReader(d)
        var cmd = '\u0000'
        var cx = 0f
        var cy = 0f
        var sx = 0f
        var sy = 0f
        var lastCubicX = 0f
        var lastCubicY = 0f
        var lastQuadX = 0f
        var lastQuadY = 0f
        var prevCmd = '\u0000'

        fun emitMove(x: Float, y: Float) {
            out += MathPathOp.MoveTo(matrix.mapX(x, y), matrix.mapY(x, y))
            cx = x; cy = y; sx = x; sy = y
        }

        fun emitLine(x: Float, y: Float) {
            out += MathPathOp.LineTo(matrix.mapX(x, y), matrix.mapY(x, y))
            cx = x; cy = y
        }

        fun emitCubic(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
            out += MathPathOp.CubicTo(
                matrix.mapX(x1, y1), matrix.mapY(x1, y1),
                matrix.mapX(x2, y2), matrix.mapY(x2, y2),
                matrix.mapX(x, y), matrix.mapY(x, y),
            )
            lastCubicX = x2; lastCubicY = y2; cx = x; cy = y
        }

        fun emitQuad(x1: Float, y1: Float, x: Float, y: Float) {
            out += MathPathOp.QuadTo(
                matrix.mapX(x1, y1), matrix.mapY(x1, y1),
                matrix.mapX(x, y), matrix.mapY(x, y),
            )
            lastQuadX = x1; lastQuadY = y1; cx = x; cy = y
        }

        while (reader.skipSeparators()) {
            val next = reader.peek()
            if (next.isLetter()) {
                cmd = reader.consumeChar()
                if (cmd == 'Z' || cmd == 'z') {
                    out += MathPathOp.Close
                    cx = sx; cy = sy
                    prevCmd = cmd
                    continue
                }
            } else {
                require(cmd != '\u0000') { "path 缺少起始命令：$d" }
                if (cmd == 'M') cmd = 'L'
                else if (cmd == 'm') cmd = 'l'
            }

            when (cmd) {
                'M', 'm' -> {
                    val x = reader.readFloat()
                    val y = reader.readFloat()
                    if (cmd == 'm') emitMove(cx + x, cy + y) else emitMove(x, y)
                    prevCmd = cmd
                    cmd = if (cmd == 'm') 'l' else 'L'
                }
                'L', 'l' -> {
                    val x = reader.readFloat()
                    val y = reader.readFloat()
                    if (cmd == 'l') emitLine(cx + x, cy + y) else emitLine(x, y)
                    prevCmd = cmd
                }
                'H', 'h' -> {
                    val x = reader.readFloat()
                    emitLine(if (cmd == 'h') cx + x else x, cy)
                    prevCmd = cmd
                }
                'V', 'v' -> {
                    val y = reader.readFloat()
                    emitLine(cx, if (cmd == 'v') cy + y else y)
                    prevCmd = cmd
                }
                'C', 'c' -> {
                    val x1 = reader.readFloat()
                    val y1 = reader.readFloat()
                    val x2 = reader.readFloat()
                    val y2 = reader.readFloat()
                    val x = reader.readFloat()
                    val y = reader.readFloat()
                    if (cmd == 'c') emitCubic(cx + x1, cy + y1, cx + x2, cy + y2, cx + x, cy + y)
                    else emitCubic(x1, y1, x2, y2, x, y)
                    prevCmd = cmd
                }
                'S', 's' -> {
                    val x1 = if (prevCmd in charArrayOf('C', 'c', 'S', 's')) 2f * cx - lastCubicX else cx
                    val y1 = if (prevCmd in charArrayOf('C', 'c', 'S', 's')) 2f * cy - lastCubicY else cy
                    val x2 = reader.readFloat()
                    val y2 = reader.readFloat()
                    val x = reader.readFloat()
                    val y = reader.readFloat()
                    if (cmd == 's') emitCubic(x1, y1, cx + x2, cy + y2, cx + x, cy + y)
                    else emitCubic(x1, y1, x2, y2, x, y)
                    prevCmd = cmd
                }
                'Q', 'q' -> {
                    val x1 = reader.readFloat()
                    val y1 = reader.readFloat()
                    val x = reader.readFloat()
                    val y = reader.readFloat()
                    if (cmd == 'q') emitQuad(cx + x1, cy + y1, cx + x, cy + y)
                    else emitQuad(x1, y1, x, y)
                    prevCmd = cmd
                }
                'T', 't' -> {
                    val x1 = if (prevCmd in charArrayOf('Q', 'q', 'T', 't')) 2f * cx - lastQuadX else cx
                    val y1 = if (prevCmd in charArrayOf('Q', 'q', 'T', 't')) 2f * cy - lastQuadY else cy
                    val x = reader.readFloat()
                    val y = reader.readFloat()
                    if (cmd == 't') emitQuad(x1, y1, cx + x, cy + y)
                    else emitQuad(x1, y1, x, y)
                    prevCmd = cmd
                }
                else -> throw IllegalArgumentException("不支持的 SVG path 指令：$cmd")
            }
        }
    }

    private class PathNumberReader(private val source: String) {
        private var pos = 0
        private val len = source.length

        fun skipSeparators(): Boolean {
            while (pos < len && (source[pos].isWhitespace() || source[pos] == ',')) pos++
            return pos < len
        }

        fun peek(): Char = source[pos]

        fun consumeChar(): Char = source[pos++]

        fun readFloat(): Float {
            require(skipSeparators()) { "path 参数不足：$source" }
            val start = pos
            if (pos < len && (source[pos] == '+' || source[pos] == '-')) pos++
            var hasDigits = false
            while (pos < len && source[pos].isDigit()) {
                hasDigits = true
                pos++
            }
            if (pos < len && source[pos] == '.') {
                pos++
                while (pos < len && source[pos].isDigit()) {
                    hasDigits = true
                    pos++
                }
            }
            require(hasDigits) { "path 数值无效，位于位置 $start：$source" }
            if (pos < len && (source[pos] == 'e' || source[pos] == 'E')) {
                val expPos = pos
                pos++
                if (pos < len && (source[pos] == '+' || source[pos] == '-')) pos++
                val expDigitsStart = pos
                while (pos < len && source[pos].isDigit()) pos++
                if (pos == expDigitsStart) {
                    pos = expPos // 回退：不是指数标记
                }
            }
            val value = source.substring(start, pos).toFloatOrNull()
                ?: throw IllegalArgumentException("path 数值解析失败：${source.substring(start, pos)}")
            require(value.isFinite()) { "path 数值非有限：$value" }
            return value
        }
    }

    private fun decodeEntities(value: String): String {
        if ('&' !in value) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            if (value[i] == '&') {
                val semi = value.indexOf(';', i + 1)
                if (semi > i + 1) {
                    val decoded = when (val entity = value.substring(i + 1, semi)) {
                        "lt" -> "<"
                        "gt" -> ">"
                        "amp" -> "&"
                        "quot" -> "\""
                        "apos" -> "'"
                        else -> if (entity.startsWith("#")) {
                            val cp = if (entity.startsWith("#x", ignoreCase = true)) {
                                entity.substring(2).toIntOrNull(16)
                            } else {
                                entity.substring(1).toIntOrNull(10)
                            }
                            cp?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
                        } else null
                    }
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(value[i])
            i++
        }
        return sb.toString()
    }

    private fun skipWhitespace(s: String, start: Int): Int {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun isNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == ':' || c == '-' || c == '_'
    private fun isAttrNameChar(c: Char): Boolean = isNameChar(c) || c == '.'
}
