package com.xingyiyao.mathinterviewvocab.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xingyiyao.mathinterviewvocab.AppUiState
import com.xingyiyao.mathinterviewvocab.AppViewModel
import com.xingyiyao.mathinterviewvocab.updates.UpdateUiState
import java.util.Locale

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: AppUiState, update: UpdateUiState, viewModel: AppViewModel) {
    val activity = LocalContext.current.activity()
    var resetConfirm by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    val updater = viewModel.updater
    val previewText = voicePreviewText(state.terms)
    val updateBusy = update.status in setOf("checking", "downloading", "verifying", "installing")
    ReadingColumn(maxWidth = 560.dp) {
        SectionHeading("设置", "发音、软件更新与本地数据。")
        AcademicCard {
            Text("英语发音", style = MaterialTheme.typography.titleLarge)
            Text("默认口音", style = MaterialTheme.typography.titleMedium)
            ChoiceRow("美式英语", state.settings.accent == "en-US",
                { viewModel.saveSettings(state.settings.copy(accent = "en-US")) }, !state.busy,
                "American English")
            ChoiceRow("英式英语", state.settings.accent == "en-GB",
                { viewModel.saveSettings(state.settings.copy(accent = "en-GB")) }, !state.busy,
                "British English")
            Text("朗读语速", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75 to "慢速", 1.0 to "正常", 1.25 to "快速").forEach { (speed, label) ->
                    if (state.settings.speed == speed) Button(onClick = {}, enabled = !state.busy,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                    else OutlinedButton(onClick = { viewModel.saveSettings(state.settings.copy(speed = speed)) },
                        enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { previewText?.let { viewModel.speak(it, "en-US") } },
                    enabled = previewText != null,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("试听美式") }
                OutlinedButton(onClick = { previewText?.let { viewModel.speak(it, "en-GB") } },
                    enabled = previewText != null,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("试听英式") }
                TextButton(onClick = viewModel::stopSpeech, modifier = Modifier.heightIn(min = 48.dp)) { Text("停止朗读") }
            }
            HorizontalDivider(color = Academic.Grid)
            Text("Kokoro 自然语音 · 完全离线", style = MaterialTheme.typography.titleMedium)
            Text("内置 2328 段英美音频，学习和发音无需联网。只播放已收录的完整词组和选中片段；缺少音频时会明确提示，不联网补音，也不切换系统机械语音。",
                color = Academic.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        AcademicCard {
            Text("软件更新", style = MaterialTheme.typography.titleLarge)
            Text("当前版本 " + update.currentVersion, color = Academic.Muted)
            if (update.availableVersion != null) Text("GitHub 正式版 " + update.availableVersion,
                style = MaterialTheme.typography.titleMedium)
            Text(update.message.ifBlank { "仅在你点击时检查 GitHub 正式版，不在启动时联网。" },
                color = if (update.status == "error") Academic.Error else Academic.Muted)
            if (update.status in setOf("downloading", "verifying")) {
                if (update.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (update.downloadedBytes.toDouble() / update.totalBytes).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(formatBytes(update.downloadedBytes) + " / " + formatBytes(update.totalBytes),
                        style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
                } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = updater::check, enabled = !updateBusy,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(if (update.status == "error") "重新检查" else "检查更新") }
                if (update.canDownload) Button(onClick = updater::download,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (update.status == "error") "重试下载" else "下载更新")
                }
                if (update.status in setOf("checking", "downloading", "verifying")) {
                    TextButton(onClick = updater::cancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") }
                }
                if (update.canInstall && activity != null) Button(onClick = { updater.requestInstall(activity) },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("安装更新") }
                if (update.needsInstallPermission && activity != null) OutlinedButton(
                    onClick = { updater.openInstallPermission(activity) }, modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("允许此来源安装") }
                if (update.needsUserConfirmation && activity != null) Button(
                    onClick = { updater.continueInstall(activity) }, modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("继续系统安装") }
                if (activity != null) TextButton(onClick = { updater.openReleaseNotes(activity) },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("打开发布页面") }
            }
            if (update.notes.isNotBlank()) {
                TextButton(onClick = { showNotes = !showNotes }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showNotes) "收起更新说明" else "查看更新说明")
                }
                if (showNotes) Text(update.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Text("安装前核对大小、SHA-256、包名、签名与版本。安装由系统请求你确认；拒绝或取消不会清除学习记录。",
                style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
        }
        AcademicCard {
            Text("本地学习数据", style = MaterialTheme.typography.titleLarge)
            Text("收藏、错题、掌握状态和答题记录仅保存在本机。相同签名的覆盖更新会保留记录；卸载应用会删除本机数据。",
                color = Academic.Muted)
            OutlinedButton(onClick = { resetConfirm = true }, enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("重置学习记录", color = Academic.Error) }
        }
        Text("数学面试词汇 · Android\n不含账号、广告或跨端同步。", color = Academic.Muted,
            style = MaterialTheme.typography.bodySmall)
    }
    if (resetConfirm) AlertDialog(
        onDismissRequest = { resetConfirm = false },
        title = { Text("重置学习记录？") },
        text = { Text("将清除本机收藏、错题、掌握状态和答题记录，保留发音设置及检测偏好。此操作不能在应用中撤销。") },
        confirmButton = { TextButton(onClick = { resetConfirm = false; viewModel.resetProgress() }) {
            Text("确认重置", color = Academic.Error)
        } },
        dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("取消") } },
    )
}

private fun formatBytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
