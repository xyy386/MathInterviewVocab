package com.xingyiyao.mathinterviewvocab.ui

import android.content.Context
import android.graphics.Typeface
import android.text.Selection
import android.text.Spannable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.viewinterop.AndroidView
import com.xingyiyao.mathinterviewvocab.core.MathAssetEntry
import com.xingyiyao.mathinterviewvocab.core.MathAssetStore
import com.xingyiyao.mathinterviewvocab.core.MathSegment
import com.xingyiyao.mathinterviewvocab.core.MathSvgDocument
import com.xingyiyao.mathinterviewvocab.core.MathTextDocument
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import com.xingyiyao.mathinterviewvocab.data.rememberMathAssets

enum class MathOverflow {
    ScaleToFit,
    ScrollOverflow,
}

private sealed interface MathRun {
    data class Inline(val source: String) : MathRun
    data class ScrollBlock(
        val segment: MathSegment,
        val entry: MathAssetEntry?,
        val doc: MathSvgDocument?,
    ) : MathRun
}

/**
 * 离线矢量公式混排组件：
 * 无公式时零开销直出原生 `Text`；含公式时按精确基线度量嵌入矢量 `Canvas`，
 * 并按容器策略处理超宽公式（卡片内 `ScaleToFit`、阅读区 `ScrollOverflow`）。
 */
@Composable
fun RichMathText(
    source: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    overflow: MathOverflow = MathOverflow.ScaleToFit,
) {
    val mergedStyle = style.merge(
        TextStyle(
            fontWeight = fontWeight,
            fontFamily = fontFamily,
        ),
    )
    if (!MathTextDocument.containsMath(source)) {
        Text(
            text = source.replace("`", ""),
            modifier = modifier,
            style = mergedStyle,
            color = color,
        )
        return
    }

    val resolvedColor = color.takeOrElse {
        mergedStyle.color.takeOrElse { LocalContentColor.current }
    }
    val errorColor = Academic.Error
    val store = rememberMathAssets()
    val density = LocalDensity.current
    val resolvedFontSize = mergedStyle.fontSize.takeOrElse { 16.sp }
    val textStyle = mergedStyle.copy(fontSize = resolvedFontSize)
    val emPx = with(density) { resolvedFontSize.toPx() }
    val centerAboveBaselineEm = remember(emPx, mergedStyle.fontFamily, mergedStyle.fontWeight) {
        measureTextCenterAboveBaselineEm(emPx, mergedStyle.fontFamily, mergedStyle.fontWeight)
    }
    val accessibleLabel = remember(source) { VocabularyCore.displayMathNotation(source) }

    BoxWithConstraints(
        modifier = modifier.semantics { contentDescription = accessibleLabel },
    ) {
        val boundedMaxPx = if (constraints.hasBoundedWidth && constraints.maxWidth != Constraints.Infinity) {
            constraints.maxWidth.toFloat()
        } else {
            null
        }
        val runs = remember(source, overflow, boundedMaxPx, emPx, store) {
            splitMathRuns(
                source = source,
                store = store,
                emPx = emPx,
                maxWidthPx = if (overflow == MathOverflow.ScrollOverflow) boundedMaxPx else null,
            )
        }
        if (runs.size == 1 && runs[0] is MathRun.Inline) {
            RichInlineMathText(
                source = (runs[0] as MathRun.Inline).source,
                store = store,
                style = textStyle,
                color = resolvedColor,
                errorColor = errorColor,
                emPx = emPx,
                maxWidthPx = if (overflow == MathOverflow.ScaleToFit) boundedMaxPx else null,
                centerAboveBaselineEm = centerAboveBaselineEm,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                runs.forEach { run ->
                    when (run) {
                        is MathRun.Inline -> RichInlineMathText(
                            source = run.source,
                            store = store,
                            style = textStyle,
                            color = resolvedColor,
                            errorColor = errorColor,
                            emPx = emPx,
                            maxWidthPx = if (overflow == MathOverflow.ScaleToFit) boundedMaxPx else null,
                            centerAboveBaselineEm = centerAboveBaselineEm,
                        )
                        is MathRun.ScrollBlock -> ScrollableFormulaBlock(
                            segment = run.segment,
                            entry = run.entry,
                            doc = run.doc,
                            emPx = emPx,
                            color = resolvedColor,
                            errorColor = errorColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RichInlineMathText(
    source: String,
    store: MathAssetStore,
    style: TextStyle,
    color: Color,
    errorColor: Color,
    emPx: Float,
    maxWidthPx: Float?,
    centerAboveBaselineEm: Float,
    modifier: Modifier = Modifier,
) {
    val fontSizeSp = style.fontSize.takeOrElse { 16.sp }
    val segments = remember(source) { MathTextDocument.segments(source) }
    val inlineContent = remember(segments, store, emPx, maxWidthPx, centerAboveBaselineEm, color, fontSizeSp) {
        val map = LinkedHashMap<String, InlineTextContent>()
        segments.forEachIndexed { index, segment ->
            val latex = segment.latex ?: return@forEachIndexed
            val entry = store.entry(latex, segment.display) ?: return@forEachIndexed
            val doc = store.document(entry) ?: return@forEachIndexed
            val metrics = computeInlineBoxMetrics(
                entry = entry,
                emPx = emPx,
                maxWidthPx = maxWidthPx,
                textCenterAboveBaselineEm = centerAboveBaselineEm,
            )
            val id = "math_$index"
            map[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = (fontSizeSp.value * metrics.widthEm).sp,
                    height = (fontSizeSp.value * metrics.boxHeightEm).sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val topOffset = size.height * metrics.svgTopInBoxFraction
                    val svgHeight = size.height * (metrics.svgBottomInBoxFraction - metrics.svgTopInBoxFraction)
                    drawMath(
                        doc = doc,
                        topOffset = topOffset,
                        drawWidthPx = size.width,
                        drawHeightPx = svgHeight,
                        color = color,
                    )
                }
            }
        }
        map
    }
    val annotated = remember(segments, inlineContent, errorColor) {
        buildAnnotatedString {
            segments.forEachIndexed { index, segment ->
                val latex = segment.latex
                if (latex == null) {
                    append(segment.text)
                } else {
                    val id = "math_$index"
                    if (id in inlineContent) {
                        appendInlineContent(id, alternateText = "\uFFFD")
                    } else {
                        withStyle(SpanStyle(color = errorColor)) {
                            append("[公式显示异常：${segment.source}]")
                        }
                    }
                }
            }
        }
    }
    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = style,
        color = color,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollableFormulaBlock(
    segment: MathSegment,
    entry: MathAssetEntry?,
    doc: MathSvgDocument?,
    emPx: Float,
    color: Color,
    errorColor: Color,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val copyAction = {
        if (copyLatexToClipboard(context, segment.source)) {
            Toast.makeText(context, "已复制 LaTeX", Toast.LENGTH_SHORT).show()
        }
    }
    if (entry != null && doc != null) {
        val widthDp = with(density) { (entry.widthEm.toFloat() * emPx).toDp() }
        val heightDp = with(density) { (entry.heightEm.toFloat() * emPx).toDp() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("math-scroll")
                .semantics { contentDescription = VocabularyCore.displayMathNotation(segment.source) }
                .combinedClickable(
                    onClick = copyAction,
                    onLongClick = copyAction,
                )
                .padding(vertical = 4.dp),
        ) {
            if (segment.display) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Canvas(Modifier.width(widthDp).height(heightDp)) {
                        drawMath(doc = doc, color = color)
                    }
                }
            } else {
                Canvas(Modifier.width(widthDp).height(heightDp)) {
                    drawMath(doc = doc, color = color)
                }
            }
        }
    } else {
        Text(
            text = "[公式显示异常：${segment.source}]",
            color = errorColor,
            modifier = Modifier
                .combinedClickable(onClick = copyAction, onLongClick = copyAction)
                .padding(vertical = 2.dp),
        )
    }
}

/**
 * 原生可选文本组件（用于概念释义等阅读区）：
 * 支持划选并重写系统复制动作还原原始 LaTeX；块级或超宽公式拆出为自然字号局部横滚块并支持点按/长按复制 LaTeX。
 */
internal class MathTextView(context: Context) : TextView(context) {
    internal var installedSource: String? = null
    internal var installedFontScale: Float = 0f
    internal var installedFontSizeSp: Float = 0f
    internal var installedErrorColor: Int = 0
    internal var installedStore: MathAssetStore? = null

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.copy) {
            if (copyMathSelectionToClipboard(context, text, selectionStart, selectionEnd)) {
                return true
            }
        }
        return super.onTextContextMenuItem(id)
    }
}

@Composable
fun SelectableMathText(
    source: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    allowHorizontalScrollOverflow: Boolean = true,
) {
    val mergedStyle = style.merge(
        TextStyle(
            fontWeight = fontWeight,
            fontFamily = fontFamily,
        ),
    )
    val resolvedColor = color.takeOrElse {
        mergedStyle.color.takeOrElse { Academic.Ink }
    }
    val errorColor = Academic.Error
    val store = rememberMathAssets()
    val density = LocalDensity.current
    val resolvedFontSize = mergedStyle.fontSize.takeOrElse { 16.sp }
    val emPx = with(density) { resolvedFontSize.toPx() }
    val accessibleLabel = remember(source) { VocabularyCore.displayMathNotation(source) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibleLabel },
    ) {
        val boundedMaxPx = if (constraints.hasBoundedWidth && constraints.maxWidth != Constraints.Infinity) {
            constraints.maxWidth.toFloat()
        } else {
            null
        }
        val runs = remember(source, allowHorizontalScrollOverflow, boundedMaxPx, emPx, store) {
            if (!MathTextDocument.containsMath(source)) {
                listOf(MathRun.Inline(source))
            } else {
                splitMathRuns(
                    source = source,
                    store = store,
                    emPx = emPx,
                    maxWidthPx = if (allowHorizontalScrollOverflow) boundedMaxPx else null,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            runs.forEach { run ->
                when (run) {
                    is MathRun.Inline -> InlineSelectableTextView(
                        source = run.source,
                        store = store,
                        fontSizeSp = resolvedFontSize.value,
                        textColor = resolvedColor.toArgb(),
                        errorColor = errorColor.toArgb(),
                        fontFamily = mergedStyle.fontFamily,
                        fontWeight = mergedStyle.fontWeight,
                    )
                    is MathRun.ScrollBlock -> ScrollableFormulaBlock(
                        segment = run.segment,
                        entry = run.entry,
                        doc = run.doc,
                        emPx = emPx,
                        color = resolvedColor,
                        errorColor = errorColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineSelectableTextView(
    source: String,
    store: MathAssetStore,
    fontSizeSp: Float,
    textColor: Int,
    errorColor: Int,
    fontFamily: FontFamily?,
    fontWeight: FontWeight?,
) {
    val fontScale = LocalDensity.current.fontScale
    val weightStyle = if ((fontWeight?.weight ?: 400) >= FontWeight.SemiBold.weight) Typeface.BOLD else Typeface.NORMAL
    val resolvedTypeface = Typeface.create(
        if (fontFamily == FontFamily.Serif) Typeface.SERIF else Typeface.SANS_SERIF,
        weightStyle,
    )
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            MathTextView(context).apply {
                setTextIsSelectable(true)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                setLineSpacing(0f, 1.35f)
                gravity = Gravity.START
                typeface = resolvedTypeface
                installedSource = source
                installedFontScale = fontScale
                installedFontSizeSp = fontSizeSp
                installedErrorColor = errorColor
                installedStore = store
                contentDescription = if (MathTextDocument.containsMath(source)) {
                    VocabularyCore.displayMathNotation(source)
                } else null
                setText(
                    buildMathSpannable(source, store, errorColor = errorColor),
                    TextView.BufferType.SPANNABLE,
                )
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.typeface = resolvedTypeface
            if (view.installedFontScale != fontScale || view.installedFontSizeSp != fontSizeSp || view.textSize == 0f) {
                view.installedFontScale = fontScale
                view.installedFontSizeSp = fontSizeSp
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            }
            if (view.installedSource != source || view.installedStore !== store || view.installedErrorColor != errorColor) {
                val previousSource = view.installedSource
                view.installedSource = source
                view.installedStore = store
                view.installedErrorColor = errorColor
                view.contentDescription = if (MathTextDocument.containsMath(source)) {
                    VocabularyCore.displayMathNotation(source)
                } else null
                val s = view.selectionStart
                val e = view.selectionEnd
                view.setText(
                    buildMathSpannable(source, store, errorColor = errorColor),
                    TextView.BufferType.SPANNABLE,
                )
                if (previousSource != source) {
                    (view.text as? Spannable)?.let { Selection.removeSelection(it) }
                } else if (s in 0..view.text.length && e in 0..view.text.length && s != e) {
                    (view.text as? Spannable)?.let { Selection.setSelection(it, s, e) }
                }
            }
        },
    )
}

private fun splitMathRuns(
    source: String,
    store: MathAssetStore,
    emPx: Float,
    maxWidthPx: Float?,
): List<MathRun> {
    val segments = MathTextDocument.segments(source)
    if (segments.isEmpty()) return listOf(MathRun.Inline(source))
    val runs = mutableListOf<MathRun>()
    val inlineBuffer = StringBuilder()

    fun flushInline() {
        if (inlineBuffer.isNotEmpty()) {
            val buffered = inlineBuffer.toString().trim('\n')
            if (buffered.isNotEmpty()) {
                runs += MathRun.Inline(buffered)
            }
            inlineBuffer.clear()
        }
    }

    for (segment in segments) {
        val latex = segment.latex
        if (latex == null) {
            inlineBuffer.append(segment.text)
            continue
        }
        val entry = store.entry(latex, segment.display)
        val doc = entry?.let { store.document(it) }
        val overflows = maxWidthPx != null && entry != null && doc != null &&
            (entry.widthEm.toFloat() * emPx > maxWidthPx)
        if (maxWidthPx != null && (segment.display || overflows)) {
            flushInline()
            runs += MathRun.ScrollBlock(segment, entry, doc)
        } else {
            inlineBuffer.append(segment.source)
        }
    }
    flushInline()
    return if (runs.isEmpty()) listOf(MathRun.Inline(source)) else runs
}
