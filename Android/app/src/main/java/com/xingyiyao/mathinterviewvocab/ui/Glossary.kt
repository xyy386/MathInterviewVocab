package com.xingyiyao.mathinterviewvocab.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xingyiyao.mathinterviewvocab.AppUiState
import com.xingyiyao.mathinterviewvocab.AppViewModel
import com.xingyiyao.mathinterviewvocab.SavedScreen
import com.xingyiyao.mathinterviewvocab.core.SubjectSummary
import com.xingyiyao.mathinterviewvocab.core.Term
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import kotlinx.coroutines.launch

fun glossaryListItemIndex(
    terms: List<Term>,
    subjects: List<SubjectSummary>,
    termID: Int,
    leadingItems: Int = 0,
): Int? {
    var index = leadingItems
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
    val leadingItems = if (screen.query.isEmpty()) 1 else 0
    LaunchedEffect(screen.selectedTermID, targetID, filteredTerms.size, leadingItems) {
        if (targetID == null || filteredTerms.isEmpty() || subjects.isEmpty()) return@LaunchedEffect
        val targetIndex = glossaryListItemIndex(filteredTerms, subjects, targetID, leadingItems)
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
    showInlineHeaderActions: Boolean = false,
    darkTheme: Boolean = state.screen.darkTheme,
    onToggleThemeWithOrigin: ((Offset, Boolean) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            TermIndex(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.width(340.dp).fillMaxHeight(),
                listState = listState,
                showInlineHeaderActions = false,
                darkTheme = darkTheme,
                onToggleThemeWithOrigin = onToggleThemeWithOrigin,
                onOpenSettings = onOpenSettings,
            )
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
                TermIndex(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    showInlineHeaderActions = showInlineHeaderActions,
                    darkTheme = darkTheme,
                    onToggleThemeWithOrigin = onToggleThemeWithOrigin,
                    onOpenSettings = onOpenSettings,
                )
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

internal class GlossaryScrollChrome(
    private val listState: LazyListState,
) {
    var toolbarVisible by mutableStateOf(true)
        private set
    var scrollingUp by mutableStateOf(false)
        private set
    var pinToolbar: Boolean = false
    private var downScrollAccumPx = 0f
    private var upScrollAccumPx = 0f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy < -1f) {
                downScrollAccumPx += -dy
                upScrollAccumPx = 0f
                if (downScrollAccumPx > 48f && listState.canScrollForward) {
                    scrollingUp = false
                    if (!pinToolbar) {
                        toolbarVisible = false
                    }
                }
            } else if (dy > 1f) {
                upScrollAccumPx += dy
                downScrollAccumPx = 0f
                if (upScrollAccumPx > 28f) {
                    toolbarVisible = true
                    scrollingUp = true
                }
            }
            return Offset.Zero
        }
    }

    fun syncBoundary(pin: Boolean, atTop: Boolean, showScrollToTop: Boolean) {
        pinToolbar = pin
        if (pin || atTop) {
            toolbarVisible = true
            downScrollAccumPx = 0f
        }
        if (atTop || !showScrollToTop) {
            scrollingUp = false
        }
    }

    fun resetToTop() {
        scrollingUp = false
        toolbarVisible = true
        downScrollAccumPx = 0f
        upScrollAccumPx = 0f
    }
}

@Composable
internal fun rememberGlossaryScrollChrome(
    listState: LazyListState,
    pinToolbar: Boolean,
    atTop: Boolean,
    showScrollToTop: Boolean,
): GlossaryScrollChrome {
    val chrome = remember(listState) { GlossaryScrollChrome(listState) }
    chrome.pinToolbar = pinToolbar
    LaunchedEffect(pinToolbar, atTop, showScrollToTop) {
        chrome.syncBoundary(pinToolbar, atTop, showScrollToTop)
    }
    return chrome
}

@Composable
internal fun GlossaryScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.8f, animationSpec = tween(180)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)),
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Academic.Surface,
            border = BorderStroke(1.dp, Academic.Blue.copy(alpha = 0.35f)),
            shadowElevation = 4.dp,
            modifier = Modifier.size(42.dp).bounceClick(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "回到顶部",
                    tint = Academic.Blue,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TermIndex(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier,
    listState: LazyListState,
    showInlineHeaderActions: Boolean,
    darkTheme: Boolean,
    onToggleThemeWithOrigin: ((Offset, Boolean) -> Unit)?,
    onOpenSettings: (() -> Unit)?,
) {
    val terms = state.filteredTerms
    val subjects = state.orderedSubjects
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var themeButtonCenter by remember { mutableStateOf(Offset(960f, 96f)) }
    val filterCount = listOf(state.screen.importantOnly, state.screen.favoritesOnly, state.screen.mistakesOnly).count { it }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    val atTop by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24 }
    }
    val browsedCount by remember(listState, terms.size) {
        derivedStateOf {
            if (terms.isEmpty()) 0
            else (listState.firstVisibleItemIndex + 1).coerceIn(1, terms.size)
        }
    }
    val pinToolbar = state.screen.query.isNotEmpty() || filtersExpanded || searchFocused || terms.isEmpty()
    val chrome = rememberGlossaryScrollChrome(listState, pinToolbar, atTop, showScrollToTop)

    fun dismissKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    BoxWithConstraints(modifier.background(Academic.Paper).nestedScroll(chrome.nestedScrollConnection)) {
        val headerMaxHeight = maxHeight * 0.54f
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = chrome.toolbarVisible,
                enter = expandVertically(tween(200), expandFrom = Alignment.Top) + fadeIn(tween(150)),
                exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top) + fadeOut(tween(130)),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Surface(color = Academic.Surface, shadowElevation = 1.dp) {
                        Column(
                            Modifier.heightIn(max = headerMaxHeight)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // 紧凑顶部搜索栏：搜索框 + 筛选 + 可选的主题/设置快捷按钮
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Academic.Paper,
                                    border = BorderStroke(1.dp, if (searchFocused) Academic.Blue else Academic.Grid),
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = Academic.Muted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                            if (state.screen.query.isEmpty()) {
                                                Text(
                                                    "搜索中英文或释义",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Academic.Muted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            BasicTextField(
                                                value = state.screen.query,
                                                onValueChange = viewModel::setQuery,
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Academic.Ink),
                                                cursorBrush = SolidColor(Academic.Blue),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                                keyboardActions = KeyboardActions(onSearch = { dismissKeyboard() }),
                                                modifier = Modifier.fillMaxWidth().onFocusChanged { searchFocused = it.isFocused },
                                            )
                                        }
                                        if (state.screen.query.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.setQuery("") },
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Clear,
                                                    contentDescription = "清空搜索",
                                                    tint = Academic.Muted,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                Surface(
                                    onClick = { dismissKeyboard(); filtersExpanded = !filtersExpanded },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (filterCount > 0 || filtersExpanded) Academic.BlueSoft else Academic.SecondarySurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (filterCount > 0 || filtersExpanded) Academic.Blue else Academic.Grid,
                                    ),
                                    modifier = Modifier.heightIn(min = 40.dp).bounceClick(),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.FilterList,
                                            contentDescription = "筛选条件",
                                            tint = if (filterCount > 0 || filtersExpanded) Academic.Blue else Academic.Ink,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            if (filterCount > 0) "筛选($filterCount)" else "筛选",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (filterCount > 0 || filtersExpanded) Academic.Blue else Academic.Ink,
                                        )
                                    }
                                }
                                if (showInlineHeaderActions) {
                                    IconButton(
                                        onClick = {
                                            dismissKeyboard()
                                            if (onToggleThemeWithOrigin != null) {
                                                onToggleThemeWithOrigin(themeButtonCenter, !darkTheme)
                                            } else {
                                                viewModel.toggleTheme(!darkTheme)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .onGloballyPositioned { coords ->
                                                val pos = coords.positionInRoot()
                                                themeButtonCenter = Offset(
                                                    pos.x + coords.size.width / 2f,
                                                    pos.y + coords.size.height / 2f,
                                                )
                                            },
                                    ) {
                                        Icon(
                                            if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                            contentDescription = if (darkTheme) "切换浅色主题" else "切换深色主题",
                                            tint = Academic.Blue,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    if (onOpenSettings != null) {
                                        IconButton(
                                            onClick = { dismissKeyboard(); onOpenSettings() },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Settings,
                                                contentDescription = "设置",
                                                tint = Academic.Ink,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            // 10. 标签选择：紧凑芯片，点标签时选中项轻微放大，周边标签向两侧让位
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ElasticTagChip(
                                    label = "全部科目",
                                    selected = state.screen.subjectID == null,
                                    compact = true,
                                    onClick = { dismissKeyboard(); viewModel.setSubject(null) },
                                )
                                subjects.forEach { subject ->
                                    ElasticTagChip(
                                        label = subject.name,
                                        selected = state.screen.subjectID == subject.id,
                                        accentColor = Academic.Blue,
                                        compact = true,
                                        onClick = { dismissKeyboard(); viewModel.setSubject(subject.id) },
                                    )
                                }
                            }

                            // 2. 紧凑遮挡自测模式栏 + 进度计数
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                listOf(
                                    "none" to "👁 完整",
                                    "hideEn" to "🙈 遮英文",
                                    "hideZh" to "🙈 遮中文",
                                ).forEach { (mode, label) ->
                                    val active = state.screen.maskMode == mode
                                    Surface(
                                        onClick = { dismissKeyboard(); viewModel.setMaskMode(mode) },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (active) Academic.BlueSoft else Color.Transparent,
                                        border = BorderStroke(1.dp, if (active) Academic.Blue else Academic.Grid),
                                        modifier = Modifier.bounceClick(),
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            color = if (active) Academic.Blue else Academic.Muted,
                                        )
                                    }
                                }
                                Text(
                                    "${terms.size}/${state.terms.size} 词 · 浏览 $browsedCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Academic.Muted,
                                )
                            }

                            // 3. 批量错峰弹性勾选面板（仅在点击“筛选”按钮后平滑展开）
                            AnimatedVisibility(
                                visible = filtersExpanded,
                                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                                exit = shrinkVertically(tween(200)) + fadeOut(tween(140)),
                            ) {
                                Column(Modifier.padding(top = 4.dp)) {
                                    StaggeredBatchFilterPanel(
                                        items = listOf(
                                            BatchFilterItem("important", "仅重点词汇", state.screen.importantOnly, viewModel::setImportantOnly),
                                            BatchFilterItem("favorites", "仅我的收藏（支持右滑飞出取消收藏）", state.screen.favoritesOnly, viewModel::setFavoritesOnly),
                                            BatchFilterItem("mistakes", "仅待复习错题", state.screen.mistakesOnly, viewModel::setMistakesOnly),
                                        ),
                                        onClearAll = viewModel::clearFilters,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Academic.Grid)
                }
            }
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
                    val showDashboard = state.screen.query.isEmpty()
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        if (showDashboard) {
                            item(key = "mini-study-dashboard") {
                                Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    MiniStudyDashboardCard(state = state)
                                }
                            }
                        }
                        subjects.forEach { subject ->
                            val matching = terms.filter { it.subjectID == subject.id }
                            if (matching.isNotEmpty()) {
                                item(key = "subject-" + subject.id) {
                                    Surface(color = Academic.Surface, modifier = Modifier.fillMaxWidth()) {
                                        Column {
                                            Text(
                                                subject.name + " · " + matching.size,
                                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                                                fontFamily = FontFamily.Serif,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Academic.Ink,
                                            )
                                            HorizontalDivider(color = Academic.Grid)
                                        }
                                    }
                                }
                                items(matching, key = { it.id }) { term ->
                                    if (state.screen.favoritesOnly) {
                                        SwipeToFlyCornerCard(
                                            itemKey = term.id,
                                            dismissLabel = "取消收藏",
                                            onDismissed = { viewModel.toggleFavorite(term.id) },
                                        ) {
                                            TermRow(
                                                term = term,
                                                state = state,
                                                onSpeak = { viewModel.speak(term.speechText) },
                                                onToggleFavorite = { viewModel.toggleFavorite(term.id) },
                                                onClick = {
                                                    dismissKeyboard()
                                                    viewModel.selectTerm(term.id)
                                                },
                                            )
                                        }
                                    } else {
                                        TermRow(
                                            term = term,
                                            state = state,
                                            onSpeak = { viewModel.speak(term.speechText) },
                                            onToggleFavorite = { viewModel.toggleFavorite(term.id) },
                                            onClick = {
                                                dismissKeyboard()
                                                viewModel.selectTerm(term.id)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    GlossaryScrollToTopButton(
                        visible = showScrollToTop && chrome.scrollingUp,
                        onClick = {
                            dismissKeyboard()
                            chrome.resetToTop()
                            coroutineScope.launch { listState.scrollToItem(0) }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStudyDashboardCard(state: AppUiState) {
    val totalCount = state.terms.size.coerceAtLeast(1)
    val masteredCount = state.progress.masteredIDs.size
    val importantTerms = state.terms.filter { it.important }
    val masteredImportant = importantTerms.count { it.id in state.progress.masteredIDs }
    val wrongCount = state.progress.wrongIDs.size
    val masteryFraction = (masteredCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Academic.Surface),
        border = BorderStroke(1.dp, Academic.Grid),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                val trackColor = Academic.Grid
                val ringColor = Academic.Blue
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * masteryFraction,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    "${(masteryFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Academic.Ink,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "学习概览 · 已掌握 $masteredCount / ${state.terms.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Academic.Ink,
                )
                Text(
                    "重点掌握 $masteredImportant / ${importantTerms.size} · 待复习错题 $wrongCount · 收藏 ${state.progress.favoriteIDs.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Academic.Muted,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TermRow(
    term: Term,
    state: AppUiState,
    onSpeak: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val selected = state.screen.selectedTermID == term.id
    val recentlyViewed = !selected && state.screen.selectedTermID == null && state.screen.lastViewedTermID == term.id
    val isFavorite = term.id in state.progress.favoriteIDs
    val maskMode = state.screen.maskMode
    var revealed by rememberSaveable(term.id, maskMode) { mutableStateOf(false) }
    val hideChinese = maskMode == "hideZh" && !revealed
    val hideEnglish = maskMode == "hideEn" && !revealed
    val streak = state.progress.correctStreak[term.id.toString()] ?: 0

    val rowBackground = when {
        selected -> Academic.Blue.copy(alpha = if (Academic.IsDark) 0.20f else 0.08f)
        recentlyViewed -> Academic.Blue.copy(alpha = if (Academic.IsDark) 0.12f else 0.05f)
        else -> Academic.Surface
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (hideChinese) {
                    Surface(
                        onClick = { revealed = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Academic.SecondarySurface,
                        border = BorderStroke(1.dp, Academic.Blue.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Outlined.Visibility, null, tint = Academic.Blue, modifier = Modifier.size(15.dp))
                            Text(
                                "点击显词 · 先看英文回忆中文概念",
                                style = MaterialTheme.typography.labelMedium,
                                color = Academic.Blue,
                            )
                        }
                    }
                } else {
                    RichMathText(
                        term.chinese,
                        fontWeight = if (term.important) FontWeight.Bold else FontWeight.Medium,
                        color = Academic.Ink,
                    )
                }

                if (hideEnglish) {
                    Surface(
                        onClick = { revealed = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Academic.SecondarySurface,
                        border = BorderStroke(1.dp, Academic.Blue.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Outlined.Visibility, null, tint = Academic.Blue, modifier = Modifier.size(15.dp))
                            Text(
                                "点击显词 · 先默想英文表达与发音",
                                style = MaterialTheme.typography.labelMedium,
                                color = Academic.Blue,
                            )
                        }
                    }
                } else {
                    RichMathText(
                        term.english,
                        color = Academic.Blue,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = if (term.important) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (recentlyViewed) TermBadge("刚刚浏览")
                    if (term.important) TermBadge("重点")
                    if (isFavorite) TermBadge("已收藏")
                    if (term.id in state.progress.masteredIDs) {
                        TermBadge("已掌握", Academic.Success)
                    } else if (streak > 0) {
                        TermBadge("连对 $streak/3", Academic.Blue)
                    }
                    if (term.id in state.progress.wrongIDs) TermBadge("待复习", Academic.Error)
                    if (maskMode != "none" && revealed) {
                        Surface(
                            onClick = { revealed = false },
                            shape = RoundedCornerShape(999.dp),
                            color = Academic.SecondarySurface,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(Icons.Outlined.VisibilityOff, null, tint = Academic.Muted, modifier = Modifier.size(12.dp))
                                Text("重遮", style = MaterialTheme.typography.labelSmall, color = Academic.Muted)
                            }
                        }
                    }
                }
            }
            // 1. 列表行右侧直接提供「🔊 一键朗读」+「⭐ 一键收藏」快捷按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = "朗读 ${term.english}",
                        tint = Academic.Blue,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    enabled = !state.busy,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏词汇",
                        tint = if (isFavorite) Academic.Blue else Academic.Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = Academic.Grid.copy(alpha = 0.6f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TermDetail(term: Term, state: AppUiState, viewModel: AppViewModel) {
    val previousTerm = state.previousTerm
    val nextTerm = state.nextTerm
    val index = state.selectedTermIndex
    val total = state.filteredTerms.size
    var relatedExpanded by rememberSaveable { mutableStateOf(true) }
    val streak = (state.progress.correctStreak[term.id.toString()] ?: 0).coerceIn(0, 3)
    val isMastered = term.id in state.progress.masteredIDs
    val isWrong = term.id in state.progress.wrongIDs
    val isFavorite = term.id in state.progress.favoriteIDs
    val termAttempts = remember(term.id, state.progress.attempts) {
        state.progress.attempts.filter { it.termID == term.id }
    }
    val termCorrect = remember(termAttempts) {
        termAttempts.count { it.selectedOptionID == it.correctOptionID }
    }
    val relatedTerms = remember(term.id, state.terms) {
        VocabularyCore.relatedTerms(term, state.terms, limit = 4)
    }

    ReadingColumn(resetKey = term.id) {
        SectionHeading(term.chinese, term.subjectName + " · " + term.subjectNameEn)
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
                if (term.important) TermBadge("重点词汇")
                if (isMastered) TermBadge("已掌握", Academic.Success)
                if (isWrong) TermBadge("待复习", Academic.Error)
                if (isFavorite) TermBadge("已收藏")
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
            SelectableMathText(
                term.definition,
                style = MaterialTheme.typography.bodyLarge,
                color = Academic.Ink,
                allowHorizontalScrollOverflow = true,
            )
            OutlinedButton(
                onClick = { viewModel.toggleFavorite(term.id) },
                enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp).bounceClick(!state.busy),
            ) {
                Icon(if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, null)
                Text(if (isFavorite) " 取消收藏" else " 收藏词汇")
            }
        }

        // 4. 本词个人掌握轨迹 + 手动标为已掌握 / 待复习
        AcademicCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("本词掌握轨迹与熟练度", style = MaterialTheme.typography.titleMedium, color = Academic.Ink)
                    Text(
                        if (termAttempts.isEmpty()) "尚未在词汇检测中作答过本词"
                        else "历史作答 ${termAttempts.size} 次 · 答对 $termCorrect 次（正确率 ${(termCorrect * 100) / termAttempts.size}%）",
                        style = MaterialTheme.typography.bodySmall,
                        color = Academic.Muted,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val effectiveDots = if (isMastered) 3 else streak
                    repeat(3) { dotIndex ->
                        val active = dotIndex < effectiveDots
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(
                                    if (active) Academic.Success else Academic.Grid,
                                    CircleShape,
                                ),
                        )
                    }
                    Text(
                        "${if (isMastered) 3 else streak}/3",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isMastered) Academic.Success else Academic.Muted,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.toggleMastered(term.id) },
                    enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(!state.busy),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Text(if (isMastered) " 移出已掌握" else " 标为已掌握")
                }
                OutlinedButton(
                    onClick = { viewModel.toggleWrong(term.id) },
                    enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(!state.busy),
                ) {
                    Text(if (isWrong) "✓ 移出待复习错题" else "⚑ 加入待复习错题")
                }
            }
        }

        // 5. 同科关联概念对比 + 本科目自测
        ExpandableSection(
            title = "同科关联概念对比与本科目自测",
            subtitle = "精选 ${relatedTerms.size} 个同科易混或递进术语，点击可直接跳转对比",
            expanded = relatedExpanded,
            onToggle = { relatedExpanded = !relatedExpanded },
        ) {
            if (relatedTerms.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    relatedTerms.forEach { rel ->
                        RelatedTermCard(
                            rel = rel,
                            onSelect = { viewModel.selectTerm(rel.id) },
                            onSpeak = { viewModel.speak(rel.speechText) },
                        )
                    }
                }
            }
            Text(
                "先根据中文回忆英文，再朗读并核对。连续答对两次可移出错题，连续答对三次标记为已掌握。",
                color = Academic.Muted,
                style = MaterialTheme.typography.bodySmall,
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
            Card(
                onClick = { viewModel.setSubject(subject.id) },
                shape = Academic.Shape,
                colors = CardDefaults.cardColors(containerColor = Academic.Surface),
                border = BorderStroke(if (isDragging) 2.dp else 1.dp, if (isDragging) Academic.Blue else Academic.Grid),
                modifier = Modifier.fillMaxWidth().bounceClick(),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(subject.name, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Academic.Ink)
                        Text(subject.englishName, color = Academic.Blue)
                        Text(subject.count.toString() + " 个词汇", color = Academic.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.DragIndicator, contentDescription = "长按拖动排序", tint = Academic.Muted)
                }
            }
        }
    }
}

@Composable
internal fun RelatedTermCard(
    rel: Term,
    onSelect: () -> Unit,
    onSpeak: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = Academic.SecondarySurface,
        border = BorderStroke(1.dp, Academic.Grid),
        modifier = Modifier.fillMaxWidth().bounceClick(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RichMathText(
                    rel.chinese,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Academic.Ink,
                )
                RichMathText(
                    rel.english,
                    style = MaterialTheme.typography.bodySmall,
                    color = Academic.Blue,
                )
            }
            IconButton(
                onClick = onSpeak,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = "朗读 ${rel.english}",
                    tint = Academic.Blue,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
