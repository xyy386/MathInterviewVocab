package com.xingyiyao.mathinterviewvocab.ui

import android.content.Context
import android.graphics.Typeface
import android.text.Selection
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xingyiyao.mathinterviewvocab.core.Term
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore

private const val READ_SELECTION = 0x6d617468

// 原生 TextView 保留拖动选区、复制和辅助功能；选区变化只更新状态，不触发播放。
private class PronunciationTextView(context: Context) : TextView(context) {
    var selectionChanged: ((Int, Int) -> Unit)? = null
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        selectionChanged?.invoke(selStart, selEnd)
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
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            PronunciationTextView(context).apply {
                setTextIsSelectable(true)
                setTextColor(Academic.Blue.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setPadding(0, 8, 0, 8)
                setLineSpacing(0f, 1.15f)
                typeface = Typeface.create("sans-serif", if (important) Typeface.BOLD else Typeface.NORMAL)
                selectionChanged = { start, end ->
                    selectionCallback(VocabularyCore.selectedText(this.text.toString(), minOf(start, end), maxOf(start, end)))
                }
                customSelectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        menu.add(Menu.NONE, READ_SELECTION, Menu.NONE, "朗读所选")
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                        return true
                    }
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                        menu.findItem(READ_SELECTION)?.isEnabled =
                            VocabularyCore.selectedText(this@apply.text.toString(), minOf(selectionStart, selectionEnd),
                                maxOf(selectionStart, selectionEnd)) != null
                        return true
                    }
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                        if (item.itemId != READ_SELECTION) return false
                        VocabularyCore.selectedText(this@apply.text.toString(), minOf(selectionStart, selectionEnd),
                            maxOf(selectionStart, selectionEnd))?.let(speakCallback)
                        return true
                    }
                    override fun onDestroyActionMode(mode: ActionMode) = Unit
                }
            }
        },
        update = { view ->
            if (view.text.toString() != text) {
                view.text = text
                Selection.removeSelection(view.text as? android.text.Spannable ?: return@AndroidView)
                selectionCallback(null)
            }
            view.typeface = Typeface.create("sans-serif", if (important) Typeface.BOLD else Typeface.NORMAL)
        },
        onRelease = { view -> view.selectionChanged = null },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PronunciationPanel(
    term: Term,
    onSpeak: (String, String?) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    key(term.id) {
        var selected by remember { mutableStateOf<String?>(null) }
        DisposableEffect(term.id) { onDispose { onStop() } }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectablePronunciationText(
                text = VocabularyCore.displayMathNotation(term.english),
                important = term.important,
                onSelection = { selected = it },
                onSpeakSelection = { onSpeak(it, null) },
            )
            Text("长按英文可选择单词或词组；选中文字不会自动播放。",
                style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSpeak(term.speechText, null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null)
                    Text(" 朗读完整词组")
                }
                OutlinedButton(onClick = { selected?.let { onSpeak(it, null) } }, enabled = selected != null,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("朗读所选") }
            }
            Text(selected?.let { "已选择：$it" } ?: "尚未选择文字",
                style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSpeak(term.speechText, "en-US") },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("美式  " + term.pronunciation.americanIPA)
                }
                OutlinedButton(onClick = { onSpeak(term.speechText, "en-GB") },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("英式  " + term.pronunciation.britishIPA)
                }
            }
        }
    }
}
