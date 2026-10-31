package com.xingyiyao.mathinterviewvocab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xingyiyao.mathinterviewvocab.AppUiState
import com.xingyiyao.mathinterviewvocab.AppViewModel
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearningScreen(state: AppUiState, viewModel: AppViewModel) {
    val summary = VocabularyCore.summarizeProgress(state.terms, state.progress)
    ReadingColumn {
        SectionHeading("学习记录", "学习进度只保存在本机，不上传、不跨端同步。")
        AcademicCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric(summary.attemptedCount.toString(), "已检测词汇")
                Metric(summary.masteredCount.toString(), "已掌握")
                Metric(summary.wrongCount.toString(), "待复习")
                Metric(summary.accuracyPercentage.toString() + "%", "累计正确率")
                Metric(summary.totalAttemptCount.toString(), "累计答题次数")
                Metric(state.progress.favoriteIDs.size.toString(), "我的收藏")
            }
        }
        AcademicCard {
            Text("学科掌握情况", style = MaterialTheme.typography.titleLarge)
            Text("连续答对三次标记为已掌握；答错后重新积累。收藏与掌握状态分别保留。", color = Academic.Muted)
            summary.subjects.forEach { subject ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(subject.name, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Text(subject.masteredCount.toString() + " / " + subject.count + " 已掌握",
                        style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
                    MasteryLine(subject.fraction)
                }
            }
        }
        AcademicCard {
            Text("待复习词汇", style = MaterialTheme.typography.titleLarge)
            if (summary.wrongTerms.isEmpty()) {
                Text(if (summary.totalAttemptCount == 0) "完成一次检测后，这里会显示需要复习的词汇。"
                    else "当前没有错题。继续保持，也可以挑战新的检测范围。", color = Academic.Muted)
                Button(onClick = { viewModel.selectTab("quiz") }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("开始检测")
                }
            } else {
                Text("连续答对两次后移出错题；点击词汇可查看概念与发音。", color = Academic.Muted)
                Button(onClick = viewModel::reviewMistakes, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("复习全部错题") }
            }
        }
        summary.wrongTerms.forEach { term ->
            Card(onClick = { viewModel.selectTerm(term.id) }, shape = Academic.Shape,
                border = BorderStroke(1.dp, Academic.Grid), colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(VocabularyCore.displayMathNotation(term.chinese),
                        fontWeight = if (term.important) FontWeight.Bold else FontWeight.Medium)
                    Text(VocabularyCore.displayMathNotation(term.english), color = Academic.Blue,
                        fontWeight = if (term.important) FontWeight.Bold else FontWeight.Normal)
                    Text(term.subjectName, style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
                }
            }
        }
    }
}
