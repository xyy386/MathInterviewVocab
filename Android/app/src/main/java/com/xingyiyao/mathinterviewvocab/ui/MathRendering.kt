package com.xingyiyao.mathinterviewvocab.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.xingyiyao.mathinterviewvocab.core.MathAssetEntry
import com.xingyiyao.mathinterviewvocab.core.MathAssetStore
import com.xingyiyao.mathinterviewvocab.core.MathPathOp
import com.xingyiyao.mathinterviewvocab.core.MathSvgDocument
import com.xingyiyao.mathinterviewvocab.core.MathTextDocument
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.ceil

data class InlineBoxMetrics(
    val widthEm: Float,
    val boxHeightEm: Float,
    val svgWidthEm: Float,
    val svgHeightEm: Float,
    val svgTopInBoxFraction: Float,
    val svgBottomInBoxFraction: Float,
    val scale: Float,
)

/**
 * 为 Compose `PlaceholderVerticalAlign.TextCenter` 计算无裁切且严格对齐文本基线的内联盒尺寸。
 */
fun computeInlineBoxMetrics(
    entry: MathAssetEntry,
    emPx: Float,
    maxWidthPx: Float?,
    textCenterAboveBaselineEm: Float = 0.34f,
): InlineBoxMetrics {
    val naturalWidthPx = entry.widthEm.toFloat() * emPx
    val scale = if (maxWidthPx != null && maxWidthPx > 0f && naturalWidthPx > maxWidthPx) {
        (maxWidthPx / naturalWidthPx).coerceAtLeast(0.1f)
    } else {
        1f
    }
    val scaledW = entry.widthEm.toFloat() * scale
    val ascentEm = (entry.heightEm - entry.depthEm).toFloat() * scale
    val depthEm = entry.depthEm.toFloat() * scale
    val scaledH = entry.heightEm.toFloat() * scale
    val center = textCenterAboveBaselineEm.coerceIn(0.20f, 0.48f)
    val aboveCenter = maxOf(0.01f, ascentEm - center)
    val belowCenter = maxOf(0.01f, depthEm + center)
    val halfBox = maxOf(aboveCenter, belowCenter)
    val boxHeightEm = 2f * halfBox
    val svgTopEm = halfBox + center - ascentEm
    val svgBottomEm = svgTopEm + scaledH
    return InlineBoxMetrics(
        widthEm = scaledW,
        boxHeightEm = boxHeightEm,
        svgWidthEm = scaledW,
        svgHeightEm = scaledH,
        svgTopInBoxFraction = (svgTopEm / boxHeightEm).coerceIn(0f, 1f),
        svgBottomInBoxFraction = (svgBottomEm / boxHeightEm).coerceIn(0f, 1f),
        scale = scale,
    )
}

internal fun measureTextCenterAboveBaselineEm(
    textSizePx: Float,
    fontFamily: FontFamily?,
    fontWeight: FontWeight?,
): Float {
    if (textSizePx <= 0f) return 0.34f
    val paint = Paint().apply {
        textSize = textSizePx
        val weightStyle = if ((fontWeight?.weight ?: 400) >= FontWeight.SemiBold.weight) Typeface.BOLD else Typeface.NORMAL
        typeface = Typeface.create(
            if (fontFamily == FontFamily.Serif) Typeface.SERIF else Typeface.SANS_SERIF,
            weightStyle,
        )
    }
    val fm = paint.fontMetrics
    if (fm.ascent == 0f && fm.descent == 0f) return 0.34f
    return (-(fm.ascent + fm.descent) / (2f * textSizePx))
        .takeIf { it.isFinite() }
        ?.coerceIn(0.20f, 0.48f)
        ?: 0.34f
}

object MathPathCache {
    private val nativePaths = Collections.synchronizedMap(WeakHashMap<MathSvgDocument, Path>())
    private val composePaths = Collections.synchronizedMap(
        WeakHashMap<MathSvgDocument, androidx.compose.ui.graphics.Path>(),
    )

    fun nativePath(doc: MathSvgDocument): Path {
        nativePaths[doc]?.let { return it }
        val path = Path().apply {
            fillType = Path.FillType.WINDING
            for (op in doc.ops) {
                when (op) {
                    is MathPathOp.MoveTo -> moveTo(op.x, op.y)
                    is MathPathOp.LineTo -> lineTo(op.x, op.y)
                    is MathPathOp.CubicTo -> cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
                    is MathPathOp.QuadTo -> quadTo(op.x1, op.y1, op.x, op.y)
                    is MathPathOp.Close -> close()
                }
            }
        }
        nativePaths[doc] = path
        return path
    }

    fun composePath(doc: MathSvgDocument): androidx.compose.ui.graphics.Path {
        composePaths[doc]?.let { return it }
        val composePath = nativePath(doc).asComposePath()
        composePaths[doc] = composePath
        return composePath
    }
}

fun drawMath(
    canvas: Canvas,
    doc: MathSvgDocument,
    left: Float,
    top: Float,
    widthPx: Float,
    heightPx: Float,
    color: Int,
) {
    if (widthPx <= 0f || heightPx <= 0f || doc.width <= 0f || doc.height <= 0f) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }
    val path = MathPathCache.nativePath(doc)
    canvas.save()
    canvas.translate(left, top)
    canvas.scale(widthPx / doc.width, heightPx / doc.height)
    canvas.translate(-doc.minX, -doc.minY)
    canvas.drawPath(path, paint)
    canvas.restore()
}

fun DrawScope.drawMath(
    doc: MathSvgDocument,
    topOffset: Float = 0f,
    drawWidthPx: Float = size.width,
    drawHeightPx: Float = size.height,
    color: Color,
) {
    if (drawWidthPx <= 0f || drawHeightPx <= 0f || doc.width <= 0f || doc.height <= 0f) return
    val path = MathPathCache.composePath(doc)
    withTransform({
        translate(left = 0f, top = topOffset)
        scale(scaleX = drawWidthPx / doc.width, scaleY = drawHeightPx / doc.height, pivot = Offset.Zero)
        translate(left = -doc.minX, top = -doc.minY)
    }) {
        drawPath(path = path, color = color)
    }
}

class MathVectorSpan(
    val entry: MathAssetEntry,
    val doc: MathSvgDocument,
    val source: String,
    private val maxWidthPx: Float? = null,
) : ReplacementSpan() {
    private fun scaleFor(paint: Paint): Float {
        val em = paint.textSize
        val naturalW = entry.widthEm.toFloat() * em
        val maxW = maxWidthPx
        return if (maxW != null && maxW > 0f && naturalW > maxW) (maxW / naturalW).coerceAtLeast(0.1f) else 1f
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val em = paint.textSize
        val scale = scaleFor(paint)
        if (fm != null) {
            val ascentEm = (entry.heightEm - entry.depthEm).toFloat()
            val depthEm = entry.depthEm.toFloat()
            val neededAscent = -ceil((ascentEm * em * scale).toDouble()).toInt()
            val neededDescent = ceil((depthEm * em * scale).toDouble()).toInt()
            if (neededAscent < fm.ascent) fm.ascent = neededAscent
            if (neededAscent < fm.top) fm.top = neededAscent
            if (neededDescent > fm.descent) fm.descent = neededDescent
            if (neededDescent > fm.bottom) fm.bottom = neededDescent
        }
        return ceil(entry.widthEm * em * scale).toInt().coerceAtLeast(1)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val em = paint.textSize
        val scale = scaleFor(paint)
        val drawWidth = entry.widthEm.toFloat() * em * scale
        val drawHeight = entry.heightEm.toFloat() * em * scale
        val drawAscent = (entry.heightEm - entry.depthEm).toFloat() * em * scale
        drawMath(
            canvas = canvas,
            doc = doc,
            left = x,
            top = y.toFloat() - drawAscent,
            widthPx = drawWidth,
            heightPx = drawHeight,
            color = paint.color,
        )
    }
}

class MathFormulaSourceSpan(val source: String)

fun buildMathSpannable(
    source: String,
    store: MathAssetStore,
    maxWidthPx: Float? = null,
    errorColor: Int = AndroidColor.rgb(0xB8, 0x3A, 0x3A),
): SpannableStringBuilder {
    val builder = SpannableStringBuilder()
    for (segment in MathTextDocument.segments(source)) {
        val latex = segment.latex
        if (latex == null) {
            builder.append(segment.text)
            continue
        }
        val entry = store.entry(latex, segment.display)
        val doc = entry?.let { store.document(it) }
        val start = builder.length
        if (entry != null && doc != null) {
            builder.append('\uFFFC')
            val end = builder.length
            builder.setSpan(MathVectorSpan(entry, doc, segment.source, maxWidthPx), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(MathFormulaSourceSpan(segment.source), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            builder.append("[公式显示异常：${segment.source}]")
            val end = builder.length
            builder.setSpan(ForegroundColorSpan(errorColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(MathFormulaSourceSpan(segment.source), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return builder
}

fun extractLatexSelection(
    text: CharSequence,
    rawStart: Int,
    rawEnd: Int,
): String {
    val len = text.length
    var start = minOf(rawStart, rawEnd).coerceIn(0, len)
    var end = maxOf(rawStart, rawEnd).coerceIn(0, len)
    if (start >= end) return ""
    val spanned = text as? Spanned ?: return text.subSequence(start, end).toString()

    // 若选区触及任一公式附件或错误提示片段，将边界扩展至完整覆盖该公式。
    val spans = spanned.getSpans(start, end, MathFormulaSourceSpan::class.java)
        .filter { spanned.getSpanStart(it) < end && spanned.getSpanEnd(it) > start }
        .sortedBy { spanned.getSpanStart(it) }
    if (spans.isEmpty()) return text.subSequence(start, end).toString()

    for (span in spans) {
        start = minOf(start, spanned.getSpanStart(span))
        end = maxOf(end, spanned.getSpanEnd(span))
    }

    val out = StringBuilder()
    var cursor = start
    for (span in spans) {
        val spanStart = spanned.getSpanStart(span)
        val spanEnd = spanned.getSpanEnd(span)
        if (spanStart > cursor) {
            out.append(text.subSequence(cursor, spanStart))
        }
        out.append(span.source)
        cursor = maxOf(cursor, spanEnd)
    }
    if (cursor < end) {
        out.append(text.subSequence(cursor, end))
    }
    return out.toString()
}

internal fun copyLatexToClipboard(context: Context, latex: String): Boolean {
    if (latex.isEmpty()) return false
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("LaTeX", latex))
    return true
}

internal fun copyMathSelectionToClipboard(
    context: Context,
    text: CharSequence,
    start: Int,
    end: Int,
): Boolean {
    val latex = extractLatexSelection(text, start, end)
    return copyLatexToClipboard(context, latex)
}

internal fun pronunciationSelection(
    text: CharSequence,
    start: Int,
    end: Int,
): String? {
    val raw = extractLatexSelection(text, start, end)
    if (raw.isEmpty()) return null
    if (MathTextDocument.containsMath(raw)) return raw.trim()
    return VocabularyCore.sanitizeSelection(raw)
}
