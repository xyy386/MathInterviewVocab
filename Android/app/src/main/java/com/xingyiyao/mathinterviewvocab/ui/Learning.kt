package com.xingyiyao.mathinterviewvocab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val totalTerms = state.terms.size.coerceAtLeast(1)
    val masteredIds = state.progress.masteredIDs.toSet()
    val wrongIds = state.progress.wrongIDs.toSet()
    val attemptedIds = state.progress.attempts.map { it.termID }.toSet()
    val learningCount = state.terms.count {
        it.id !in masteredIds && it.id !in wrongIds &&
            (it.id in attemptedIds || (state.progress.correctStreak[it.id.toString()] ?: 0) > 0)
    }
    val unseenCount = (state.terms.size - summary.masteredCount - summary.wrongCount - learningCount).coerceAtLeast(0)
    val favoriteTerms = remember(state.terms, state.progress.favoriteIDs) {
        val favSet = state.progress.favoriteIDs.toSet()
        state.terms.filter { it.id in favSet }
    }
    val recentAttempts = remember(state.progress.attempts) {
        state.progress.attempts.takeLast(30).asReversed()
    }
    var bottomTab by rememberSaveable { mutableStateOf("mistakes") }

    ReadingColumn {
        SectionHeading("学习记录", "学习进度只保存在本机，不上传、不跨端同步。")

        // 9. 多色环形词汇分布看板 + 统计指标
        AcademicCard {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    val masteredColor = Academic.Success
                    val learningColor = Academic.Blue
                    val wrongColor = Academic.Error
                    val unseenColor = Academic.Grid
                    Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                        val stroke = 12.dp.toPx()
                        var startAngle = -90f
                        val segments = listOf(
                            summary.masteredCount to masteredColor,
                            learningCount to learningColor,
                            summary.wrongCount to wrongColor,
                            unseenCount to unseenColor,
                        )
                        segments.forEach { (count, color) ->
                            if (count > 0) {
                                val sweep = 360f * (count.toFloat() / totalTerms.toFloat())
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                                )
                                startAngle += sweep
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(summary.masteredCount * 100) / totalTerms}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = Academic.Ink,
                        )
                        Text(
                            "总掌握率",
                            style = MaterialTheme.typography.labelSmall,
                            color = Academic.Muted,
                        )
                    }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "全词库四态分布（共 ${state.terms.size} 词）",
                        style = MaterialTheme.typography.titleMedium,
                        color = Academic.Ink,
                    )
                    DistributionLegendRow("已掌握（连对 ≥ 3）", summary.masteredCount, Academic.Success)
                    DistributionLegendRow("学习巩固中", learningCount, Academic.Blue)
                    DistributionLegendRow("待复习错题", summary.wrongCount, Academic.Error)
                    DistributionLegendRow("尚未检测", unseenCount, Academic.Muted)
                }
            }
            HorizontalDivider(color = Academic.Grid)
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

        // 9. 学科掌握情况（三态堆叠进度条：已掌握绿 + 错题红 + 学习蓝 + 拖拽排序）
        AcademicCard {
            Text("学科掌握情况", style = MaterialTheme.typography.titleLarge, color = Academic.Ink)
            Text("连续答对三次标记为已掌握；点击学科可查看词汇，长按可拖拽调整学科顺序。", color = Academic.Muted)
            DraggableSpringColumn(
                items = orderedSubjectProgress,
                keySelector = { it.id },
                onMove = viewModel::moveSubject,
            ) { subject, isDragging ->
                val accent = Academic.subjectAccent(subject.id)
                val subjectTerms = state.terms.filter { it.subjectID == subject.id }
                val subjectWrongCount = subjectTerms.count { it.id in wrongIds }
                val subjectLearningCount = subjectTerms.count {
                    it.id !in masteredIds && it.id !in wrongIds &&
                        (it.id in attemptedIds || (state.progress.correctStreak[it.id.toString()] ?: 0) > 0)
                }
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
                                    buildString {
                                        append("${subject.masteredCount}/${subject.count} 掌握")
                                        if (subjectWrongCount > 0) append(" · $subjectWrongCount 错题")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Academic.Muted,
                                )
                                Icon(Icons.Outlined.DragIndicator, contentDescription = "长按拖动排序", tint = Academic.Muted)
                            }
                        }
                        SubjectSegmentedBar(
                            total = subject.count,
                            mastered = subject.masteredCount,
                            learning = subjectLearningCount,
                            wrong = subjectWrongCount,
                        )
                    }
                }
            }
        }

        // 10. 底部三合一面板：待复习错题 / 我的收藏 / 最近答题流水
        AcademicCard {
            Text("个人词库与答题流水", style = MaterialTheme.typography.titleLarge, color = Academic.Ink)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ElasticTagChip(
                    label = "待复习错题 (${summary.wrongTerms.size})",
                    selected = bottomTab == "mistakes",
                    accentColor = Academic.Error,
                    onClick = { bottomTab = "mistakes" },
                )
                ElasticTagChip(
                    label = "我的收藏 (${favoriteTerms.size})",
                    selected = bottomTab == "favorites",
                    accentColor = Academic.Blue,
                    onClick = { bottomTab = "favorites" },
                )
                ElasticTagChip(
                    label = "最近答题流水 (${state.progress.attempts.size})",
                    selected = bottomTab == "attempts",
                    accentColor = Academic.Blue,
                    onClick = { bottomTab = "attempts" },
                )
            }
            when (bottomTab) {
                "mistakes" -> {
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
                        Text(
                            "连续答对两次或右滑卡片可移出错题；点击词汇可查看概念与发音。",
                            color = Academic.Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = viewModel::reviewMistakes,
                            enabled = !state.busy,
                            modifier = Modifier.heightIn(min = 48.dp).bounceClick(!state.busy),
                        ) { Text("复习全部错题") }
                    }
                }
                "favorites" -> {
                    if (favoriteTerms.isEmpty()) {
                        Text("还没有收藏任何词汇。在词表行右侧或详情页点击 ⭐ 即可收藏重点概念。", color = Academic.Muted)
                    } else {
                        Text(
                            "右滑卡片可飞出取消收藏；点击喇叭可直接听发音，或发起收藏专测。",
                            color = Academic.Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = {
                                viewModel.startQuiz(
                                    state.progress.quizPreferences.copy(scope = "favorites", includeMastered = true),
                                )
                            },
                            enabled = !state.busy,
                            modifier = Modifier.heightIn(min = 48.dp).bounceClick(!state.busy),
                        ) { Text("专项检测我的收藏") }
                    }
                }
                "attempts" -> {
                    if (recentAttempts.isEmpty()) {
                        Text("暂无答题记录。完成检测后，这里会按时间倒序展示最近 30 条作答明细。", color = Academic.Muted)
                    } else {
                        Text(
                            "展示最近 ${recentAttempts.size} 次答题明细，点击任一记录可跳转词条详情。",
                            color = Academic.Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        when (bottomTab) {
            "mistakes" -> {
                summary.wrongTerms.forEach { term ->
                    SwipeToFlyCornerCard(
                        itemKey = "wrong-${term.id}",
                        dismissLabel = "移出错题",
                        onDismissed = { viewModel.toggleWrong(term.id) },
                    ) {
                        Card(
                            onClick = { viewModel.selectTerm(term.id) },
                            shape = Academic.Shape,
                            border = BorderStroke(1.dp, Academic.Grid),
                            colors = CardDefaults.cardColors(containerColor = Academic.Surface),
                            modifier = Modifier.fillMaxWidth().bounceClick(),
                        ) {
                            Row(
                                Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RichMathText(
                                        term.chinese,
                                        fontWeight = if (term.important) FontWeight.Bold else FontWeight.Medium,
                                        color = Academic.Ink,
                                    )
                                    RichMathText(
                                        "${term.english} · US ${VocabularyCore.displayIpa(term.pronunciation.americanIPA, withSlashes = true)}",
                                        color = Academic.Blue,
                                        fontWeight = if (term.important) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(term.subjectName, style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
                                }
                                IconButton(onClick = { viewModel.speak(term.speechText) }) {
                                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "朗读", tint = Academic.Blue)
                                }
                            }
                        }
                    }
                }
            }
            "favorites" -> {
                favoriteTerms.forEach { term ->
                    SwipeToFlyCornerCard(
                        itemKey = "fav-${term.id}",
                        dismissLabel = "取消收藏",
                        onDismissed = { viewModel.toggleFavorite(term.id) },
                    ) {
                        Card(
                            onClick = { viewModel.selectTerm(term.id) },
                            shape = Academic.Shape,
                            border = BorderStroke(1.dp, Academic.Grid),
                            colors = CardDefaults.cardColors(containerColor = Academic.Surface),
                            modifier = Modifier.fillMaxWidth().bounceClick(),
                        ) {
                            Row(
                                Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RichMathText(
                                        term.chinese,
                                        fontWeight = if (term.important) FontWeight.Bold else FontWeight.Medium,
                                        color = Academic.Ink,
                                    )
                                    RichMathText(
                                        "${term.english} · US ${VocabularyCore.displayIpa(term.pronunciation.americanIPA, withSlashes = true)}",
                                        color = Academic.Blue,
                                        fontWeight = if (term.important) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(term.subjectName, style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
                                }
                                IconButton(onClick = { viewModel.speak(term.speechText) }) {
                                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "朗读", tint = Academic.Blue)
                                }
                            }
                        }
                    }
                }
            }
            "attempts" -> {
                recentAttempts.forEach { attempt ->
                    val term = state.terms.find { it.id == attempt.termID } ?: return@forEach
                    val isRight = attempt.selectedOptionID == attempt.correctOptionID
                    val badgeColor = if (isRight) Academic.Success else Academic.Error
                    val formattedTime = remember(attempt.answeredAt) {
                        attempt.answeredAt.replace('T', ' ').take(19)
                    }
                    Card(
                        onClick = { viewModel.selectTerm(term.id) },
                        shape = Academic.Shape,
                        border = BorderStroke(1.dp, Academic.Grid),
                        colors = CardDefaults.cardColors(containerColor = Academic.Surface),
                        modifier = Modifier.fillMaxWidth().bounceClick(),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TermBadge(if (isRight) "✓ 答对" else "✗ 答错", badgeColor)
                                    TermBadge(if (attempt.direction == "zhToEn") "中 → 英" else "英 → 中")
                                    Text(formattedTime, style = MaterialTheme.typography.labelSmall, color = Academic.Muted)
                                }
                                RichMathText(
                                    "${term.chinese} · ${term.english}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Academic.Ink,
                                )
                            }
                            IconButton(onClick = { viewModel.speak(term.speechText) }) {
                                Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "朗读", tint = Academic.Blue)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionLegendRow(label: String, count: Int, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
        }
        Text(
            "$count 词",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Academic.Ink,
        )
    }
}

@Composable
private fun SubjectSegmentedBar(
    total: Int,
    mastered: Int,
    learning: Int,
    wrong: Int,
) {
    val safeTotal = total.coerceAtLeast(1).toFloat()
    val masteredWeight = (mastered / safeTotal).coerceIn(0f, 1f)
    val learningWeight = (learning / safeTotal).coerceIn(0f, (1f - masteredWeight).coerceAtLeast(0f))
    val wrongWeight = (wrong / safeTotal).coerceIn(0f, (1f - masteredWeight - learningWeight).coerceAtLeast(0f))
    val remainWeight = (1f - masteredWeight - learningWeight - wrongWeight).coerceAtLeast(0f)

    Row(
        Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Academic.Grid),
    ) {
        if (masteredWeight > 0f) {
            Box(Modifier.weight(masteredWeight).fillMaxHeight().background(Academic.Success))
        }
        if (learningWeight > 0f) {
            Box(Modifier.weight(learningWeight).fillMaxHeight().background(Academic.Blue))
        }
        if (wrongWeight > 0f) {
            Box(Modifier.weight(wrongWeight).fillMaxHeight().background(Academic.Error))
        }
        if (remainWeight > 0f) {
            Box(Modifier.weight(remainWeight).fillMaxHeight().background(Color.Transparent))
        }
    }
}
