package com.xingyiyao.mathinterviewvocab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xingyiyao.mathinterviewvocab.AppViewModel

private data class Destination(val id: String, val title: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("glossary", "词汇", Icons.AutoMirrored.Outlined.MenuBook),
    Destination("quiz", "检测", Icons.Outlined.Quiz),
    Destination("progress", "学习记录", Icons.Outlined.BarChart),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val speech by viewModel.speechState.collectAsStateWithLifecycle()
    val update by viewModel.updateState.collectAsStateWithLifecycle()
    // Material 3 Adaptive 依据当前 Activity 窗口计算断点，分屏和旋转时自动重算。
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val layout = when {
        sizeClass.isWidthAtLeastBreakpoint(ExpandedWidthDp) -> AppWindowLayout.Expanded
        sizeClass.isWidthAtLeastBreakpoint(MediumWidthDp) -> AppWindowLayout.Medium
        else -> AppWindowLayout.Compact
    }
    val compact = layout == AppWindowLayout.Compact
    val fontScale = LocalDensity.current.fontScale.coerceAtMost(2.5f)
    val extraScale = (fontScale - 1).coerceAtLeast(0f)
    val detail = state.screen.tab == "glossary" && state.selectedTerm != null
    val showBack = state.screen.showSettings || (detail && layout != AppWindowLayout.Expanded)
    val title = if (state.screen.showSettings) "设置"
        else if (detail && layout != AppWindowLayout.Expanded) "词汇详情"
        else destinations.find { it.id == state.screen.tab }?.title ?: "词汇"
    BackHandler(showBack) {
        if (state.screen.showSettings) viewModel.showSettings(false) else viewModel.selectTerm(null)
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopSpeech() } }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        containerColor = Academic.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title, fontFamily = FontFamily.Serif) },
                navigationIcon = {
                    if (showBack) IconButton(onClick = {
                        if (state.screen.showSettings) viewModel.showSettings(false) else viewModel.selectTerm(null)
                    }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    if (!state.screen.showSettings) IconButton(onClick = { viewModel.showSettings(true) }) {
                        Icon(Icons.Outlined.Settings, "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        bottomBar = {
            if (compact) {
                NavigationBar(containerColor = Color.White, windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.height((80 + extraScale * 32).dp)) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = state.screen.tab == destination.id && !state.screen.showSettings,
                            onClick = { viewModel.selectTab(destination.id) },
                            icon = { Icon(destination.icon, null) },
                            label = { Text(destination.title) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            if (!compact) {
                NavigationRail(containerColor = Color.White, windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.width((104 + extraScale * 48).dp).fillMaxHeight()
                        .verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(16.dp))
                    destinations.forEach { destination ->
                        NavigationRailItem(
                            selected = state.screen.tab == destination.id && !state.screen.showSettings,
                            onClick = { viewModel.selectTab(destination.id) },
                            icon = { Icon(destination.icon, null) },
                            label = { Text(destination.title) },
                            modifier = Modifier.padding(vertical = 8.dp).heightIn(min = (80 + extraScale * 32).dp),
                        )
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Academic.Grid))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                HorizontalDivider(color = Academic.Grid)
                if (state.operationError != null && state.loadError == null) {
                    StatusMessage(state.operationError!!, error = true, onDismiss = viewModel::dismissError)
                }
                if (speech.status in setOf("playing", "missing", "error")) {
                    StatusMessage(
                        speech.message.ifBlank { if (speech.status == "playing") "正在朗读…" else "无法播放这段音频。" },
                        error = speech.status in setOf("missing", "error"),
                        onDismiss = viewModel::stopSpeech,
                        action = if (speech.status == "playing") "停止" else "知道了",
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.loading -> Column(Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text("正在读取本地词库与学习记录…", color = Academic.Muted)
                        }
                        state.loadError != null -> DataErrorPage(state.loadError!!, viewModel)
                        state.screen.showSettings -> SettingsScreen(state, update, viewModel)
                        state.screen.tab == "quiz" -> QuizScreen(state, viewModel)
                        state.screen.tab == "progress" -> LearningScreen(state, viewModel)
                        else -> GlossaryScreen(state, viewModel, layout == AppWindowLayout.Expanded)
                    }
                }
            }
        }
    }
    if (state.screen.pendingQuizPreferences != null) AlertDialog(
        onDismissRequest = viewModel::cancelQuizReplacement,
        title = { Text("开始新的检测？") },
        text = { Text("当前还有未完成的检测。已成功保存的答案会保留；开始新检测将替换当前题目，尚未保存的答案不计分。") },
        confirmButton = { TextButton(onClick = viewModel::confirmQuizReplacement, enabled = !state.busy) {
            Text("开始新检测")
        } },
        dismissButton = { TextButton(onClick = viewModel::cancelQuizReplacement) { Text("继续当前检测") } },
    )
}

@Composable
private fun StatusMessage(message: String, error: Boolean, onDismiss: () -> Unit, action: String = "关闭") {
    val color = if (error) Academic.Error else Academic.Blue
    Surface(color = color.copy(alpha = 0.08f)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = color, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(action) }
        }
    }
}

@Composable
private fun DataErrorPage(message: String, viewModel: AppViewModel) {
    var recovery by remember { mutableStateOf<String?>(null) }
    ReadingColumn(maxWidth = 560.dp) {
        SectionHeading("本地数据未能读取", "原文件不会被自动重置或覆盖。")
        AcademicCard {
            Text(message, color = Academic.Error)
            OutlinedButton(onClick = viewModel::load) { Text("重新读取") }
            if (viewModel.canRecoverData) {
                Text("若文件已损坏，可在确认后单独恢复。恢复前会将原文件备份到应用私有目录。",
                    color = Academic.Muted)
                if (message.contains("学习记录")) TextButton(onClick = { recovery = "progress" }) {
                    Text("备份并重置学习记录", color = Academic.Error)
                }
                if (message.contains("发音设置")) TextButton(onClick = { recovery = "settings" }) {
                    Text("备份并恢复默认发音设置")
                }
            }
        }
    }
    if (recovery != null) AlertDialog(
        onDismissRequest = { recovery = null },
        title = { Text(if (recovery == "progress") "重置学习记录？" else "恢复发音设置？") },
        text = { Text(if (recovery == "progress")
            "将备份原文件并清除收藏、错题、掌握状态和答题记录。此操作不会修改发音设置。"
            else "将备份原设置文件并恢复美式英语、正常语速。此操作不会修改学习记录。") },
        confirmButton = { TextButton(onClick = {
            val resetProgress = recovery == "progress"
            recovery = null
            viewModel.recoverData(resetProgress)
        }) { Text("确认恢复") } },
        dismissButton = { TextButton(onClick = { recovery = null }) { Text("取消") } },
    )
}
