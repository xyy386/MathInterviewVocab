package com.xingyiyao.mathinterviewvocab.ui

import android.content.Context
import android.graphics.Typeface
import android.text.Selection
import android.text.Spannable
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xingyiyao.mathinterviewvocab.core.MathAssetStore
import com.xingyiyao.mathinterviewvocab.core.MathTextDocument
import com.xingyiyao.mathinterviewvocab.core.Term
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import com.xingyiyao.mathinterviewvocab.data.rememberMathAssets

private const val READ_SELECTION = 0x6d617468

// 原生 TextView 保留拖动选区、矢量公式混排、复制还原 LaTeX 和辅助功能；选区变化只更新状态，不触发播放。
internal class PronunciationTextView(context: Context) : TextView(context) {
    var selectionChanged: ((Int, Int) -> Unit)? = null
    internal var installedSource: String? = null
    internal var installedStore: MathAssetStore? = null
    internal var installedErrorColor: Int = 0
    internal var installedMaxWidthPx: Float? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        selectionChanged?.invoke(selStart, selEnd)
    }

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
fun SelectablePronunciationText(
    text: String,
    important: Boolean,
    onSelection: (String?) -> Unit,
    onSpeakSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionCallback by rememberUpdatedState(onSelection)
    val speakCallback by rememberUpdatedState(onSpeakSelection)
    val fontScale = LocalDensity.current.fontScale
    val textColor = Academic.Blue.toArgb()
    val errorColor = Academic.Error.toArgb()
    val store = rememberMathAssets()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = if (constraints.hasBoundedWidth && constraints.maxWidth != Constraints.Infinity) {
            constraints.maxWidth.toFloat()
        } else {
            null
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                PronunciationTextView(context).apply {
                    setTextIsSelectable(true)
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                    tag = fontScale
                    setPadding(0, 8, 0, 8)
                    setLineSpacing(0f, 1.15f)
                    typeface = Typeface.create("sans-serif", if (important) Typeface.BOLD else Typeface.NORMAL)
                    installedSource = text
                    installedStore = store
                    installedErrorColor = errorColor
                    installedMaxWidthPx = maxWidthPx
                    contentDescription = if (MathTextDocument.containsMath(text)) {
                        VocabularyCore.displayMathNotation(text)
                    } else null
                    setText(
                        buildMathSpannable(text, store, maxWidthPx = maxWidthPx, errorColor = errorColor),
                        TextView.BufferType.SPANNABLE,
                    )
                    selectionChanged = { start, end ->
                        selectionCallback(pronunciationSelection(this.text, start, end))
                    }
                    customSelectionActionModeCallback = object : ActionMode.Callback {
                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            menu.add(Menu.NONE, READ_SELECTION, Menu.NONE, "朗读所选")
                                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                            return true
                        }
                        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                            menu.findItem(READ_SELECTION)?.isEnabled =
                                pronunciationSelection(this@apply.text, selectionStart, selectionEnd) != null
                            return true
                        }
                        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                            if (item.itemId != READ_SELECTION) return false
                            pronunciationSelection(this@apply.text, selectionStart, selectionEnd)?.let(speakCallback)
                            return true
                        }
                        override fun onDestroyActionMode(mode: ActionMode) = Unit
                    }
                }
            },
            update = { view ->
                view.setTextColor(textColor)
                if (view.tag != fontScale) {
                    view.tag = fontScale
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                }
                val rebuild = view.installedSource != text ||
                    view.installedStore !== store ||
                    view.installedErrorColor != errorColor ||
                    view.installedMaxWidthPx != maxWidthPx
                if (rebuild) {
                    val previousSource = view.installedSource
                    val s = view.selectionStart
                    val e = view.selectionEnd
                    view.installedSource = text
                    view.installedStore = store
                    view.installedErrorColor = errorColor
                    view.installedMaxWidthPx = maxWidthPx
                    view.contentDescription = if (MathTextDocument.containsMath(text)) {
                        VocabularyCore.displayMathNotation(text)
                    } else null
                    view.setText(
                        buildMathSpannable(text, store, maxWidthPx = maxWidthPx, errorColor = errorColor),
                        TextView.BufferType.SPANNABLE,
                    )
                    if (previousSource != text) {
                        (view.text as? Spannable)?.let { Selection.removeSelection(it) }
                        selectionCallback(null)
                    } else if (s in 0..view.text.length && e in 0..view.text.length && s != e) {
                        (view.text as? Spannable)?.let { Selection.setSelection(it, s, e) }
                    }
                }
                view.typeface = Typeface.create("sans-serif", if (important) Typeface.BOLD else Typeface.NORMAL)
            },
            onRelease = { view -> view.selectionChanged = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PronunciationPanel(
    term: Term,
    onSpeak: (String, String?) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    emphasizeImportant: Boolean = true,
) {
    key(term.id) {
        var selected by remember { mutableStateOf<String?>(null) }
        val words = remember(term.id, term.speechText) { VocabularyCore.extractWords(term.speechText) }
        val accentColor = Academic.subjectAccent(term.subjectID)
        DisposableEffect(term.id) { onDispose { onStop() } }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectablePronunciationText(
                text = term.english,
                important = emphasizeImportant && term.important,
                onSelection = { selected = it },
                onSpeakSelection = { onSpeak(it, null) },
            )
            Text(
                "长按英文可选择单词或词组，复制公式时保留 LaTeX；选中文字不会自动播放。",
                style = MaterialTheme.typography.bodySmall,
                color = Academic.Muted,
            )
            if (words.size > 1) {
                Text("单词快捷点读：", style = MaterialTheme.typography.labelMedium, color = Academic.Muted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    words.forEach { word ->
                        val isChosen = selected.equals(word, ignoreCase = true)
                        ElasticTagChip(
                            label = word,
                            selected = isChosen,
                            accentColor = accentColor,
                            onClick = {
                                selected = word
                                onSpeak(word, null)
                            },
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onSpeak(term.speechText, null) },
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null)
                    Text(" 朗读完整词组")
                }
                OutlinedButton(
                    onClick = { selected?.let { onSpeak(it, null) } },
                    enabled = selected != null,
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(selected != null),
                ) { Text("朗读所选") }
            }
            Text(
                selected?.let { "已选择：$it" } ?: "尚未选择文字",
                style = MaterialTheme.typography.bodySmall,
                color = Academic.Muted,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onSpeak(term.speechText, "en-US") },
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(),
                ) {
                    Text("美式  ${VocabularyCore.displayIpa(term.pronunciation.americanIPA, withSlashes = true)}")
                }
                OutlinedButton(
                    onClick = { onSpeak(term.speechText, "en-GB") },
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(),
                ) {
                    Text("英式  ${VocabularyCore.displayIpa(term.pronunciation.britishIPA, withSlashes = true)}")
                }
            }
        }
    }
}
