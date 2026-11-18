package com.xingyiyao.mathinterviewvocab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val orderedSubjectProgress = state.orderedSubjects.mapNotNull { ordered ->
        summary.subjects.find { it.id == ordered.id }
    }
    ReadingColumn {
        SectionHeading("学习记录", "学习进度只保存在本机，不上传、不跨端同步。")
        AcademicCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric(
                    summary.attemptedCount.toString(),
                    "已检测词汇 ›",
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.openFilteredGlossary() }
                        .padding(6.dp),
                )
                Metric(
                    summary.masteredCount.toString(),
                    "已掌握 ›",
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.openFilteredGlossary() }
                        .padding(6.dp),
                )
                Metric(
                    summary.wrongCount.toString(),
                    "待复习 ›",
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.openFilteredGlossary(mistakesOnly = true) }
                        .padding(6.dp),
                )
                Metric(summary.accuracyPercentage.toString() + "%", "累计正确率", Modifier.padding(6.dp))
                Metric(summary.totalAttemptCount.toString(), "累计答题次数", Modifier.padding(6.dp))
                Metric(
                    state.progress.favoriteIDs.size.toString(),
                    "我的收藏 ›",
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.openFilteredGlossary(favoritesOnly = true) }
                        .padding(6.dp),
                )
            }
        }
        AcademicCard {
            Text("学科掌握情况", style = MaterialTheme.typography.titleLarge, color = Academic.Ink)
            Text("连续答对三次标记为已掌握；点击学科可查看词汇，长按可拖拽调整学科顺序。", color = Academic.Muted)
            DraggableSpringColumn(
                items = orderedSubjectProgress,
                keySelector = { it.id },
                onMove = viewModel::moveSubject,
            ) { subject, isDragging ->
                val accent = Academic.subjectAccent(subject.id)
                Card(
                    onClick = { viewModel.openFilteredGlossary(subjectID = subject.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Academic.Surface),
                    border = BorderStroke(if (isDragging) 1.5.dp else 1.dp, if (isDragging) accent else Academic.Grid),
                    modifier = Modifier.fillMaxWidth().bounceClick(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(subject.name + " ›", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Academic.Ink)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    subject.masteredCount.toString() + " / " + subject.count + " 已掌握",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Academic.Muted,
                                )
                                Icon(Icons.Outlined.DragIndicator, contentDescription = "长按拖动排序", tint = Academic.Muted)
                            }
                        }
                        MasteryLine(subject.fraction)
                    }
                }
            }
        }
        AcademicCard {
            Text("待复习词汇", style = MaterialTheme.typography.titleLarge, color = Academic.Ink)
            if (summary.wrongTerms.isEmpty()) {
                Text(
                    if (summary.totalAttemptCount == 0) "完成一次检测后，这里会显示需要复习的词汇。"
                    else "当前没有错题。继续保持，也可以挑战新的检测范围。",
                    color = Academic.Muted,
                )
                Button(
                    onClick = { viewModel.selectTab("quiz") },
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(),
                ) {
                    Text("开始检测")
                }
            } else {
                Text("连续答对两次后移出错题；点击词汇可查看概念与发音。", color = Academic.Muted)
                Button(
                    onClick = viewModel::reviewMistakes,
                    enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(!state.busy),
                ) { Text("复习全部错题") }
            }
        }
        summary.wrongTerms.forEach { term ->
            Card(
                onClick = { viewModel.selectTerm(term.id) },
                shape = Academic.Shape,
                border = BorderStroke(1.dp, Academic.Grid),
                colors = CardDefaults.cardColors(containerColor = Academic.Surface),
                modifier = Modifier.fillMaxWidth().bounceClick(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        VocabularyCore.displayMathNotation(term.chinese),
                        fontWeight = if (term.important) FontWeight.Bold else FontWeight.Medium,
                        color = Academic.Ink,
                    )
                    Text(
                        VocabularyCore.displayMathNotation(term.english),
                        color = Academic.Blue,
                        fontWeight = if (term.important) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(term.subjectName, style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
                }
            }
        }
    }
}
