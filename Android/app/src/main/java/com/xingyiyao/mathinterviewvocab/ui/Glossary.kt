package com.xingyiyao.mathinterviewvocab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xingyiyao.mathinterviewvocab.AppUiState
import com.xingyiyao.mathinterviewvocab.AppViewModel
import com.xingyiyao.mathinterviewvocab.core.SubjectSummary
import com.xingyiyao.mathinterviewvocab.core.Term
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore

@Composable
fun GlossaryScreen(state: AppUiState, viewModel: AppViewModel, expanded: Boolean) {
    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            TermIndex(state, viewModel, Modifier.width(320.dp).fillMaxHeight())
            Box(Modifier.width(1.dp).fillMaxHeight().background(Academic.Grid))
            Box(Modifier.weight(1f)) { GlossaryReading(state, viewModel) }
        }
    } else if (state.selectedTerm != null) {
        TermDetail(state.selectedTerm!!, state, viewModel)
    } else {
        TermIndex(state, viewModel, Modifier.fillMaxSize())
    }
}

@Composable
private fun GlossaryReading(state: AppUiState, viewModel: AppViewModel) {
    val term = state.selectedTerm
    if (term == null) WelcomePage(state, viewModel) else TermDetail(term, state, viewModel)
}

@Composable
fun SubjectDropdown(
    subjects: List<SubjectSummary>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allowAll: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(subjects.find { it.id == selected }?.name ?: "全部科目", modifier = Modifier.weight(1f))
            Text("▾", modifier = Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowAll) DropdownMenuItem(
                text = { Text("全部科目") }, onClick = { open = false; onSelect(null) },
                trailingIcon = { if (selected == null) Icon(Icons.Outlined.Check, null) },
            )
            subjects.forEach { subject ->
                DropdownMenuItem(
                    text = { Text(subject.name) },
                    onClick = { open = false; onSelect(subject.id) },
                    trailingIcon = { if (selected == subject.id) Icon(Icons.Outlined.Check, null) },
                )
            }
        }
    }
}

@Composable
private fun TermIndex(state: AppUiState, viewModel: AppViewModel, modifier: Modifier) {
    val terms = state.filteredTerms
    val subjects = remember(state.terms) { VocabularyCore.subjects(state.terms) }
    var filtersOpen by remember { mutableStateOf(false) }
    val filterCount = listOf(state.screen.importantOnly, state.screen.favoritesOnly, state.screen.mistakesOnly).count { it }
    BoxWithConstraints(modifier.background(Color.White)) {
      val headerMaxHeight = maxHeight * 0.55f
      Column(Modifier.fillMaxSize()) {
        Column(Modifier.heightIn(max = headerMaxHeight).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.screen.query, onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索中英文或释义") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (state.screen.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Outlined.Clear, "清空搜索")
                    }
                }, singleLine = true, shape = RoundedCornerShape(12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SubjectDropdown(subjects, state.screen.subjectID, viewModel::setSubject, Modifier.weight(1f))
                Box {
                    IconButton(onClick = { filtersOpen = true }) {
                        Icon(Icons.Outlined.FilterList, if (filterCount == 0) "筛选" else "筛选，已启用 $filterCount 项",
                            tint = if (filterCount == 0) Academic.Muted else Academic.Blue)
                    }
                    DropdownMenu(expanded = filtersOpen, onDismissRequest = { filtersOpen = false }) {
                        FilterMenuItem("仅重点词汇", state.screen.importantOnly) { viewModel.setImportantOnly(!state.screen.importantOnly) }
                        FilterMenuItem("仅我的收藏", state.screen.favoritesOnly) { viewModel.setFavoritesOnly(!state.screen.favoritesOnly) }
                        FilterMenuItem("仅待复习错题", state.screen.mistakesOnly) { viewModel.setMistakesOnly(!state.screen.mistakesOnly) }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("清除筛选") }, onClick = { viewModel.clearFilters(); filtersOpen = false })
                    }
                }
            }
            Text("显示 " + terms.size + " / " + state.terms.size + " 个词汇" +
                if (filterCount > 0) " · $filterCount 项筛选" else "",
                style = MaterialTheme.typography.labelMedium, color = Academic.Muted)
        }
        HorizontalDivider(color = Academic.Grid)
        if (terms.isEmpty()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("没有符合条件的词汇", style = MaterialTheme.typography.titleMedium)
                Text("试试其他关键词，或清除科目和筛选条件。", color = Academic.Muted)
                TextButton(onClick = { viewModel.setQuery(""); viewModel.clearFilters() }) { Text("显示全部词汇") }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                subjects.forEach { subject ->
                    val matching = terms.filter { it.subjectID == subject.id }
                    if (matching.isNotEmpty()) {
                        item(key = "subject-" + subject.id) {
                            Text(subject.name, Modifier.fillMaxWidth().background(Academic.Paper).padding(16.dp),
                                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall, color = Academic.Ink)
                        }
                        items(matching, key = { it.id }) { term ->
                            TermRow(term, state, onClick = { viewModel.selectTerm(term.id) })
                        }
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun FilterMenuItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick,
        trailingIcon = { if (checked) Icon(Icons.Outlined.Check, null) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TermRow(term: Term, state: AppUiState, onClick: () -> Unit) {
    val selected = state.screen.selectedTermID == term.id
    Column(
        Modifier.fillMaxWidth().background(if (selected) Academic.Blue.copy(alpha = 0.08f) else Color.White)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(VocabularyCore.displayMathNotation(term.chinese),
            fontWeight = if (term.important) FontWeight.Bold else FontWeight.Medium,
            color = Academic.Ink)
        Text(VocabularyCore.displayMathNotation(term.english), color = Academic.Blue,
            fontFamily = FontFamily.SansSerif,
            fontWeight = if (term.important) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (term.important) TermBadge("重点")
            if (term.id in state.progress.favoriteIDs) TermBadge("已收藏")
            if (term.id in state.progress.masteredIDs) TermBadge("已掌握", Academic.Success)
            if (term.id in state.progress.wrongIDs) TermBadge("待复习", Academic.Error)
        }
    }
    HorizontalDivider(color = Academic.Grid.copy(alpha = 0.6f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TermDetail(term: Term, state: AppUiState, viewModel: AppViewModel) {
    ReadingColumn {
        SectionHeading(VocabularyCore.displayMathNotation(term.chinese), term.subjectName + " · " + term.subjectNameEn)
        AcademicCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (term.important) TermBadge("重点词汇")
                if (term.id in state.progress.masteredIDs) TermBadge("已掌握", Academic.Success)
                if (term.id in state.progress.wrongIDs) TermBadge("待复习", Academic.Error)
                if (term.id in state.progress.favoriteIDs) TermBadge("已收藏")
            }
            PronunciationPanel(term, onSpeak = { text, accent ->
                if (accent == null) viewModel.speak(text) else viewModel.speak(text, accent)
            }, onStop = viewModel::stopSpeech)
            HorizontalDivider(color = Academic.Grid)
            Text("概念释义", style = MaterialTheme.typography.titleMedium)
            Text(VocabularyCore.displayMathNotation(term.definition), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(
                onClick = { viewModel.toggleFavorite(term.id) },
                enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(if (term.id in state.progress.favoriteIDs) Icons.Outlined.Star else Icons.Outlined.StarBorder, null)
                Text(if (term.id in state.progress.favoriteIDs) " 取消收藏" else " 收藏词汇")
            }
        }
        AcademicCard {
            Text("学习提示", style = MaterialTheme.typography.titleMedium)
            Text("先根据中文回忆英文，再朗读并核对。连续答对两次可移出错题，连续答对三次标记为已掌握。",
                color = Academic.Muted)
            Button(onClick = {
                viewModel.startQuiz(state.progress.quizPreferences.copy(
                    scope = "subject", selectedSubjectID = term.subjectID, includeMastered = true,
                ))
            }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("检测本科目")
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomePage(state: AppUiState, viewModel: AppViewModel) {
    ReadingColumn {
        SectionHeading("数学专业面试\n英文专业词汇", "从概念出发，以英文表达。选择左侧词汇，开始学习。")
        AcademicCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric(state.terms.size.toString(), "完整词库")
                Metric(state.terms.count { it.important }.toString(), "重点词汇")
                Metric(state.progress.masteredIDs.size.toString(), "已掌握")
            }
        }
        Text("按学科浏览", style = MaterialTheme.typography.titleLarge)
        VocabularyCore.subjects(state.terms).forEach { subject ->
            Card(onClick = { viewModel.setSubject(subject.id) }, shape = Academic.Shape,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Academic.Grid), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(subject.name, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Text(subject.englishName, color = Academic.Blue)
                    Text(subject.count.toString() + " 个词汇", color = Academic.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
