package com.xingyiyao.mathinterviewvocab.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.xingyiyao.mathinterviewvocab.AppUiState
import com.xingyiyao.mathinterviewvocab.AppViewModel
import com.xingyiyao.mathinterviewvocab.SavedScreen
import com.xingyiyao.mathinterviewvocab.core.SubjectSummary
import com.xingyiyao.mathinterviewvocab.core.Term
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import kotlinx.coroutines.launch

fun glossaryListItemIndex(terms: List<Term>, subjects: List<SubjectSummary>, termID: Int): Int? {
    var index = 0
    for (subject in subjects) {
        val matching = terms.filter { it.subjectID == subject.id }
        if (matching.isEmpty()) continue
        index += 1 // 科目吸顶标题
        for (term in matching) {
            if (term.id == termID) return index
            index += 1
        }
    }
    return null
}

@Composable
fun rememberGlossaryListState(
    screen: SavedScreen,
    filteredTerms: List<Term> = emptyList(),
    subjects: List<SubjectSummary> = emptyList(),
): LazyListState {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val filterKey = listOf(
        screen.query,
        screen.subjectID.orEmpty(),
        screen.importantOnly.toString(),
        screen.favoritesOnly.toString(),
        screen.mistakesOnly.toString(),
        screen.subjectOrder.joinToString(","),
    )
    var previousFilterKey by rememberSaveable { mutableStateOf(filterKey) }
    LaunchedEffect(filterKey) {
        if (previousFilterKey != filterKey) {
            previousFilterKey = filterKey
            listState.scrollToItem(0)
        }
    }
    val targetID = screen.selectedTermID ?: screen.lastViewedTermID
    LaunchedEffect(screen.selectedTermID, targetID, filteredTerms.size) {
        if (targetID == null || filteredTerms.isEmpty() || subjects.isEmpty()) return@LaunchedEffect
        val targetIndex = glossaryListItemIndex(filteredTerms, subjects, targetID)
            ?: return@LaunchedEffect
        withFrameNanos {}
        val info = listState.layoutInfo
        if (info.totalItemsCount > 0 && info.visibleItemsInfo.none { it.key == targetID }) {
            listState.scrollToItem(targetIndex)
        }
    }
    return listState
}

@Composable
fun GlossaryScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    expanded: Boolean,
    listState: LazyListState = rememberGlossaryListState(state.screen, state.filteredTerms, state.orderedSubjects),
) {
    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            TermIndex(state, viewModel, Modifier.width(340.dp).fillMaxHeight(), listState)
            Box(Modifier.width(1.dp).fillMaxHeight().background(Academic.Grid))
            Box(Modifier.weight(1f)) { GlossaryReading(state, viewModel) }
        }
    } else {
        AnimatedContent(
            targetState = state.selectedTerm,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
            label = "glossaryDetailTransition",
        ) { selected ->
            if (selected != null) {
                TermDetail(selected, state, viewModel)
            } else {
                TermIndex(state, viewModel, Modifier.fillMaxSize(), listState)
            }
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TermIndex(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier,
    listState: LazyListState,
) {
    val terms = state.filteredTerms
    val subjects = state.orderedSubjects
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val filterCount = listOf(state.screen.importantOnly, state.screen.favoritesOnly, state.screen.mistakesOnly).count { it }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    val browsedCount by remember(listState, terms.size) {
        derivedStateOf {
            if (terms.isEmpty()) 0
            else (listState.firstVisibleItemIndex + 1).coerceIn(1, terms.size)
        }
    }

    fun dismissKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    BoxWithConstraints(modifier.background(Academic.Paper)) {
        val headerMaxHeight = maxHeight * 0.56f
        Column(Modifier.fillMaxSize()) {
            Surface(color = Academic.Surface, shadowElevation = 1.dp) {
                Column(
                    Modifier.heightIn(max = headerMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = state.screen.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索中英文或释义") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (state.screen.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Outlined.Clear, "清空搜索")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { dismissKeyboard() }),
                        shape = RoundedCornerShape(14.dp),
                    )
                    // 10. 标签选择：点标签时选中项轻微放大，周边标签自动向两侧平移让位
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ElasticTagChip(
                            label = "全部科目",
                            selected = state.screen.subjectID == null,
                            onClick = { dismissKeyboard(); viewModel.setSubject(null) },
                        )
                        subjects.forEach { subject ->
                            ElasticTagChip(
                                label = subject.name,
                                selected = state.screen.subjectID == subject.id,
                                accentColor = Academic.subjectAccent(subject.id),
                                onClick = { dismissKeyboard(); viewModel.setSubject(subject.id) },
                            )
                        }
                    }
                    // 5. 文本展开 + 3. 批量错峰弹性勾选
                    ExpandableSection(
                        title = if (filterCount == 0) "筛选条件（重点 / 收藏 / 错题）" else "筛选条件 · 已启用 $filterCount 项",
                        expanded = filtersExpanded,
                        onToggle = { dismissKeyboard(); filtersExpanded = !filtersExpanded },
                    ) {
                        StaggeredBatchFilterPanel(
                            items = listOf(
                                BatchFilterItem("important", "仅重点词汇", state.screen.importantOnly, viewModel::setImportantOnly),
                                BatchFilterItem("favorites", "仅我的收藏（支持右滑飞出取消收藏）", state.screen.favoritesOnly, viewModel::setFavoritesOnly),
                                BatchFilterItem("mistakes", "仅待复习错题", state.screen.mistakesOnly, viewModel::setMistakesOnly),
                            ),
                            onClearAll = viewModel::clearFilters,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "显示 ${terms.size} / ${state.terms.size} 个词汇" +
                                if (filterCount > 0) " · $filterCount 项筛选" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = Academic.Muted,
                        )
                        if (terms.isNotEmpty()) {
                            Text(
                                "浏览进度 $browsedCount / ${terms.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Academic.Blue,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = Academic.Grid)
            if (terms.isEmpty()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("没有符合条件的词汇", style = MaterialTheme.typography.titleMedium, color = Academic.Ink)
                    Text("试试其他关键词，或清除科目和筛选条件。", color = Academic.Muted)
                    TextButton(onClick = { dismissKeyboard(); viewModel.setQuery(""); viewModel.clearFilters() }) {
                        Text("显示全部词汇")
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    var flatIndex = 0
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        subjects.forEach { subject ->
                            val matching = terms.filter { it.subjectID == subject.id }
                            if (matching.isNotEmpty()) {
                                val headerListIndex = flatIndex
                                flatIndex += matching.size + 1
                                stickyHeader(key = "subject-" + subject.id) {
                                    Surface(
                                        color = Academic.Paper.copy(alpha = 0.96f),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                subject.name + " · " + matching.size,
                                                fontFamily = FontFamily.Serif,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Academic.subjectAccent(subject.id),
                                            )
                                            Text(
                                                subject.englishName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Academic.Muted,
                                            )
                                        }
                                    }
                                }
                                itemsIndexed(matching, key = { _, term -> term.id }) { idxInSubject, term ->
                                    val absoluteItemIndex = headerListIndex + 1 + idxInSubject
                                    // 9. 卡片堆叠：上滑列表时顶部卡片先停留，被后续卡片逐步压缩堆叠
                                    val stackProgress by remember(listState, absoluteItemIndex) {
                                        derivedStateOf {
                                            val firstIndex = listState.firstVisibleItemIndex
                                            val offset = listState.firstVisibleItemScrollOffset
                                            if (absoluteItemIndex == firstIndex && offset > 0) {
                                                (offset / 220f).coerceIn(0f, 1f)
                                            } else 0f
                                        }
                                    }
                                    val stackedModifier = Modifier.graphicsLayer {
                                        if (stackProgress > 0f) {
                                            scaleX = 1f - stackProgress * 0.055f
                                            scaleY = 1f - stackProgress * 0.085f
                                            translationY = stackProgress * 26f
                                            alpha = 1f - stackProgress * 0.32f
                                        }
                                    }
                                    if (state.screen.favoritesOnly) {
                                        SwipeToFlyCornerCard(
                                            itemKey = term.id,
                                            dismissLabel = "取消收藏",
                                            onDismissed = { viewModel.toggleFavorite(term.id) },
                                            modifier = stackedModifier,
                                        ) {
                                            TermRow(term, state, onClick = {
                                                dismissKeyboard()
                                                viewModel.selectTerm(term.id)
                                            })
                                        }
                                    } else {
                                        Box(stackedModifier) {
                                            TermRow(term, state, onClick = {
                                                dismissKeyboard()
                                                viewModel.selectTerm(term.id)
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (showScrollToTop) {
                        FilledTonalButton(
                            onClick = {
                                dismissKeyboard()
                                coroutineScope.launch { listState.scrollToItem(0) }
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null)
                            Text(" 回到顶部")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TermRow(term: Term, state: AppUiState, onClick: () -> Unit) {
    val selected = state.screen.selectedTermID == term.id
    val recentlyViewed = !selected && state.screen.selectedTermID == null && state.screen.lastViewedTermID == term.id
    val accent = Academic.subjectAccent(term.subjectID)
    val rowBackground = when {
        selected -> Academic.Blue.copy(alpha = if (Academic.IsDark) 0.22f else 0.09f)
        recentlyViewed -> Academic.Blue.copy(alpha = if (Academic.IsDark) 0.14f else 0.05f)
        else -> Academic.Surface
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = rowBackground),
        border = BorderStroke(
            width = if (selected || recentlyViewed) 1.5.dp else 1.dp,
            color = if (selected) Academic.Blue else if (recentlyViewed) accent.copy(alpha = 0.6f) else Academic.Grid,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (Academic.IsDark) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth().bounceClick(),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier.width(5.dp).fillMaxHeight().background(accent),
            )
            Column(
                Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    VocabularyCore.displayMathNotation(term.chinese),
                    fontWeight = if (term.important) FontWeight.Bold else FontWeight.SemiBold,
                    color = Academic.Ink,
                )
                Text(
                    VocabularyCore.displayMathNotation(term.english),
                    color = Academic.Blue,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (term.important) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (recentlyViewed) TermBadge("刚刚浏览", accent)
                    if (term.important) TermBadge("重点", accent)
                    if (term.id in state.progress.favoriteIDs) TermBadge("已收藏")
                    if (term.id in state.progress.masteredIDs) TermBadge("已掌握", Academic.Success)
                    if (term.id in state.progress.wrongIDs) TermBadge("待复习", Academic.Error)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TermDetail(term: Term, state: AppUiState, viewModel: AppViewModel) {
    val previousTerm = state.previousTerm
    val nextTerm = state.nextTerm
    val index = state.selectedTermIndex
    val total = state.filteredTerms.size
    var tipsExpanded by rememberSaveable { mutableStateOf(true) }
    val accent = Academic.subjectAccent(term.subjectID)
    ReadingColumn(resetKey = term.id) {
        SectionHeading(VocabularyCore.displayMathNotation(term.chinese), term.subjectName + " · " + term.subjectNameEn)
        if (index >= 0 && total > 0) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { viewModel.selectAdjacentTerm(-1) },
                    enabled = previousTerm != null,
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(previousTerm != null),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    Text(" 上一个")
                }
                Text(
                    "第 ${index + 1} / $total 个",
                    style = MaterialTheme.typography.labelLarge,
                    color = Academic.Muted,
                )
                OutlinedButton(
                    onClick = { viewModel.selectAdjacentTerm(1) },
                    enabled = nextTerm != null,
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(nextTerm != null),
                ) {
                    Text("下一个 ")
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                }
            }
        }
        AcademicCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TermBadge(term.subjectName, accent)
                if (term.important) TermBadge("重点词汇")
                if (term.id in state.progress.masteredIDs) TermBadge("已掌握", Academic.Success)
                if (term.id in state.progress.wrongIDs) TermBadge("待复习", Academic.Error)
                if (term.id in state.progress.favoriteIDs) TermBadge("已收藏")
            }
            PronunciationPanel(
                term,
                onSpeak = { text, selectedAccent ->
                    if (selectedAccent == null) viewModel.speak(text) else viewModel.speak(text, selectedAccent)
                },
                onStop = viewModel::stopSpeech,
            )
            HorizontalDivider(color = Academic.Grid)
            Text("概念释义", style = MaterialTheme.typography.titleMedium, color = Academic.Ink)
            Text(
                VocabularyCore.displayMathNotation(term.definition),
                style = MaterialTheme.typography.bodyLarge,
                color = Academic.Ink,
            )
            OutlinedButton(
                onClick = { viewModel.toggleFavorite(term.id) },
                enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp).bounceClick(!state.busy),
            ) {
                Icon(if (term.id in state.progress.favoriteIDs) Icons.Outlined.Star else Icons.Outlined.StarBorder, null)
                Text(if (term.id in state.progress.favoriteIDs) " 取消收藏" else " 收藏词汇")
            }
        }
        ExpandableSection(
            title = "学习提示与本科目自测",
            subtitle = "点击展开或收起记忆建议",
            expanded = tipsExpanded,
            onToggle = { tipsExpanded = !tipsExpanded },
        ) {
            Text(
                "先根据中文回忆英文，再朗读并核对。连续答对两次可移出错题，连续答对三次标记为已掌握。",
                color = Academic.Muted,
            )
            Button(
                onClick = {
                    viewModel.startQuiz(
                        state.progress.quizPreferences.copy(
                            scope = "subject",
                            selectedSubjectID = term.subjectID,
                            includeMastered = true,
                        ),
                    )
                },
                enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp).bounceClick(!state.busy),
            ) {
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
        Text("按学科浏览（长按卡片可拖拽排序）", style = MaterialTheme.typography.titleLarge, color = Academic.Ink)
        DraggableSpringColumn(
            items = state.orderedSubjects,
            keySelector = { it.id },
            onMove = viewModel::moveSubject,
        ) { subject, isDragging ->
            val accent = Academic.subjectAccent(subject.id)
            Card(
                onClick = { viewModel.setSubject(subject.id) },
                shape = Academic.Shape,
                colors = CardDefaults.cardColors(containerColor = Academic.Surface),
                border = BorderStroke(if (isDragging) 2.dp else 1.dp, if (isDragging) accent else Academic.Grid),
                modifier = Modifier.fillMaxWidth().bounceClick(),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(subject.name, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Academic.Ink)
                        Text(subject.englishName, color = accent)
                        Text(subject.count.toString() + " 个词汇", color = Academic.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.DragIndicator, contentDescription = "长按拖动排序", tint = Academic.Muted)
                }
            }
        }
    }
}
