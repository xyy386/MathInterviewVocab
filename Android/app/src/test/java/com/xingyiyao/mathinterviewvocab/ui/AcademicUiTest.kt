package com.xingyiyao.mathinterviewvocab.ui

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.xingyiyao.mathinterviewvocab.AppUiState
import com.xingyiyao.mathinterviewvocab.SavedScreen
import com.xingyiyao.mathinterviewvocab.core.Pronunciation
import com.xingyiyao.mathinterviewvocab.core.QuizQuestion
import com.xingyiyao.mathinterviewvocab.core.Term
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import com.xingyiyao.mathinterviewvocab.core.canonicalTerms
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AcademicUiTest {
    @get:Rule(order = 0)
    val activityRegistration = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(context.packageManager).addOrUpdateActivity(
                ActivityInfo().apply {
                    name = ComponentActivity::class.java.name
                    packageName = context.packageName
                },
            )
        }
    }

    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun longChoiceAtTwoHundredPercentRemainsOperable() {
        var chosen = false
        val label = "Lebesgue dominated convergence theorem / 勒贝格控制收敛定理"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                AcademicTheme {
                    Column(Modifier.width(360.dp)) {
                        ChoiceRow(label, false, { chosen = true },
                            supporting = "可出题 326 个词汇，排除已掌握的词汇")
                    }
                }
            }
        }
        compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction().performClick()
        assertTrue(chosen)
    }

    @Test
    fun readingColumnCanScrollLongContentWithoutHorizontalClipping() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                AcademicTheme {
                    ReadingColumn(Modifier.width(360.dp)) {
                        SectionHeading("数学专业面试英文词汇", "手机分屏与大字体")
                        AcademicCard {
                            Text("noncentral chi-squared distribution, probability density function",
                                Modifier.fillMaxWidth())
                            Text("概率密度函数与非中心卡方分布。".repeat(20))
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("数学专业面试英文词汇").assertIsDisplayed()
    }

    @Test
    fun returningFromTermDetailPreservesGlossaryScrollPositionUntilFiltersChange() {
        var screen by mutableStateOf(SavedScreen())
        lateinit var listState: LazyListState
        compose.setContent {
            AcademicTheme {
                listState = rememberGlossaryListState(screen)
                if (screen.selectedTermID != null) {
                    Text("详情页 ${screen.selectedTermID}")
                } else {
                    LazyColumn(Modifier.width(360.dp).height(240.dp), state = listState) {
                        items((1..120).toList(), key = { it }) { id ->
                            Text("词条 $id", Modifier.fillMaxWidth().height(48.dp))
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            runBlocking { listState.scrollToItem(42, 18) }
        }
        compose.runOnIdle {
            assertEquals(42, listState.firstVisibleItemIndex)
            assertEquals(18, listState.firstVisibleItemScrollOffset)
            screen = screen.copy(selectedTermID = 43)
        }
        compose.onNodeWithText("详情页 43").assertIsDisplayed()
        compose.runOnIdle {
            screen = screen.copy(selectedTermID = null)
        }
        compose.onNodeWithText("词条 43").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(42, listState.firstVisibleItemIndex)
            assertEquals(18, listState.firstVisibleItemScrollOffset)
            screen = screen.copy(query = "收敛")
        }
        compose.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun glossaryListItemIndexAccountsForStickySubjectHeaders() {
        val terms = canonicalTerms()
        val subjects = VocabularyCore.subjects(terms)
        assertEquals(1, glossaryListItemIndex(terms, subjects, 1))
        assertEquals(13, glossaryListItemIndex(terms, subjects, 12))
    }

    @Test
    fun expandableSectionAndSnapPhysicsSliderAndElasticTagOperateCleanly() {
        var expanded by mutableStateOf(false)
        var speed by mutableStateOf(1.0)
        var tagSelected by mutableStateOf(false)
        compose.setContent {
            CircularRevealThemeHost(darkTheme = tagSelected, revealOrigin = null) {
                Column(Modifier.width(360.dp)) {
                    ElasticTagChip(
                        label = "数学分析",
                        selected = tagSelected,
                        onClick = { tagSelected = !tagSelected },
                    )
                    SnapPhysicsSlider(
                        options = SpeedOptions,
                        selected = speed,
                        onSelect = { speed = it },
                    )
                    ExpandableSection(
                        title = "折叠详情",
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                    ) {
                        Text("展开后的详细说明内容")
                    }
                }
            }
        }
        compose.onNodeWithText("数学分析").performClick()
        assertTrue(tagSelected)
        compose.onNodeWithText("快速").performClick()
        assertEquals(1.25, speed, 0.0)
        compose.onNodeWithText("折叠详情").performClick()
        compose.onNodeWithText("展开后的详细说明内容").assertIsDisplayed()
    }

    @Test
    fun termRowsRemainFullWidthAndVisibleAfterScrollingPastStickyHeaders() {
        val terms = canonicalTerms()
        val subjects = VocabularyCore.subjects(terms)
        val uiState = AppUiState(loading = false, terms = terms)
        lateinit var listState: LazyListState
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                AcademicTheme {
                    listState = remember { LazyListState() }
                    LazyColumn(Modifier.width(360.dp).height(640.dp), state = listState) {
                        subjects.forEach { subject ->
                            val matching = terms.filter { it.subjectID == subject.id }
                            if (matching.isNotEmpty()) {
                                stickyHeader(key = "subject-" + subject.id) {
                                    Text(subject.name, Modifier.fillMaxWidth().padding(12.dp))
                                }
                                items(matching, key = { it.id }) { term ->
                                    TermRow(
                                        term = term,
                                        state = uiState,
                                        onSpeak = {},
                                        onToggleFavorite = {},
                                        onClick = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            runBlocking {
                listState.scrollToItem(90, 0)
                listState.scrollToItem(74, 40)
            }
        }
        compose.onNodeWithText("隐函数").assertIsDisplayed()
        compose.onNodeWithText("显函数").assertIsDisplayed()
        compose.onNodeWithText("explicit function").assertIsDisplayed()
        val implicitBounds = compose.onNodeWithText("隐函数").getBoundsInRoot()
        val explicitBounds = compose.onNodeWithText("显函数").getBoundsInRoot()
        assertEquals(implicitBounds.left.value, explicitBounds.left.value, 0.5f)
    }

    @Test
    fun compactBottomBarFitsWithinCompactHeightAndSwitchesTabs() {
        var selectedTab by mutableStateOf("glossary")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                AcademicTheme {
                    Column(Modifier.width(360.dp)) {
                        CompactBottomBar(
                            selectedTab = selectedTab,
                            showSettings = false,
                            extraScale = 0f,
                            onSelectTab = { selectedTab = it },
                            modifier = Modifier.testTag("compactBottomBar"),
                        )
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("compactBottomBar").getBoundsInRoot()
        assertTrue(bounds.bottom.value - bounds.top.value <= 60f)
        compose.onNodeWithText("检测").assertIsDisplayed().performClick()
        assertEquals("quiz", selectedTab)
        compose.onNodeWithText("学习记录").assertIsDisplayed().performClick()
        assertEquals("progress", selectedTab)
    }

    @Test
    fun glossaryChromeCollapsesOnScrollDownAndShowsScrollToTopOnlyOnScrollUp() {
        val terms = canonicalTerms()
        val uiState = AppUiState(loading = false, terms = terms)
        var pinToolbar by mutableStateOf(false)
        lateinit var listState: LazyListState
        lateinit var chrome: GlossaryScrollChrome
        val childDispatcher = NestedScrollDispatcher()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                AcademicTheme {
                    listState = remember { LazyListState() }
                    val showScrollToTop by remember(listState) {
                        derivedStateOf { listState.firstVisibleItemIndex > 2 }
                    }
                    val atTop by remember(listState) {
                        derivedStateOf {
                            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24
                        }
                    }
                    chrome = rememberGlossaryScrollChrome(
                        listState = listState,
                        pinToolbar = pinToolbar,
                        atTop = atTop,
                        showScrollToTop = showScrollToTop,
                    )
                    val scope = rememberCoroutineScope()
                    Box(Modifier.width(300.dp).height(360.dp).nestedScroll(chrome.nestedScrollConnection)) {
                        Box(
                            Modifier.fillMaxSize()
                                .nestedScroll(remember { object : NestedScrollConnection {} }, childDispatcher),
                        ) {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                state = listState,
                            ) {
                                items(terms, key = { it.id }) { term ->
                                    TermRow(
                                        term = term,
                                        state = uiState,
                                        onSpeak = {},
                                        onToggleFavorite = {},
                                        onClick = {},
                                    )
                                }
                            }
                        }
                        GlossaryScrollToTopButton(
                            visible = showScrollToTop && chrome.scrollingUp,
                            onClick = {
                                chrome.resetToTop()
                                scope.launch { listState.scrollToItem(0) }
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                        )
                    }
                }
            }
        }

        assertTrue(chrome.toolbarVisible)
        compose.onNodeWithContentDescription("回到顶部").assertDoesNotExist()

        compose.runOnIdle {
            runBlocking { listState.scrollToItem(15, 0) }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            childDispatcher.dispatchPreScroll(Offset(0f, -120f), NestedScrollSource.UserInput)
        }
        compose.waitForIdle()
        assertTrue(listState.firstVisibleItemIndex > 2)
        assertFalse(chrome.toolbarVisible)
        assertFalse(chrome.scrollingUp)
        compose.onNodeWithContentDescription("回到顶部").assertDoesNotExist()

        compose.runOnIdle {
            childDispatcher.dispatchPreScroll(Offset(0f, 80f), NestedScrollSource.UserInput)
        }
        compose.waitForIdle()
        assertTrue(chrome.toolbarVisible)
        assertTrue(chrome.scrollingUp)
        compose.onNodeWithContentDescription("回到顶部").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex)
        compose.onNodeWithContentDescription("回到顶部").assertDoesNotExist()

        compose.runOnIdle {
            pinToolbar = true
            runBlocking { listState.scrollToItem(15, 0) }
        }
        compose.runOnIdle {
            childDispatcher.dispatchPreScroll(Offset(0f, -120f), NestedScrollSource.UserInput)
        }
        compose.waitForIdle()
        assertTrue(chrome.toolbarVisible)
    }

    @Test
    fun quizPromptAndOptionsDoNotBoldImportantTerms() {
        val importantTerm = Term(
            id = 1,
            subjectID = "statistics",
            subjectName = "数理统计",
            subjectNameEn = "Mathematical Statistics",
            chinese = "置信区间",
            english = "Confidence Interval (CI)",
            definition = "区间估计",
            important = true,
            speechText = "Confidence Interval",
            pronunciation = Pronunciation("/a/", "/b/"),
        )
        val regularTerm = importantTerm.copy(
            id = 2,
            chinese = "点估计",
            english = "Point Estimation",
            important = false,
            speechText = "Point Estimation",
        )
        val importantQuestion = QuizQuestion(
            id = "q1",
            term = importantTerm,
            direction = "zhToEn",
            options = listOf(regularTerm, importantTerm),
            correctOptionID = 1,
        )
        val regularQuestion = importantQuestion.copy(id = "q2", term = regularTerm)

        compose.setContent {
            AcademicTheme {
                Column(Modifier.width(360.dp)) {
                    QuizQuestionPromptCard(question = importantQuestion)
                    QuizQuestionPromptCard(question = regularQuestion)
                    QuizOptionCard(
                        index = 0,
                        label = importantTerm.english,
                        chosen = false,
                        right = false,
                        answered = false,
                        enabled = true,
                        onSelect = {},
                    )
                    QuizOptionCard(
                        index = 1,
                        label = regularTerm.english,
                        chosen = false,
                        right = false,
                        answered = false,
                        enabled = true,
                        onSelect = {},
                    )
                }
            }
        }

        fun fontWeightOf(text: String): FontWeight? {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            return layouts.firstOrNull()?.layoutInput?.style?.fontWeight
        }

        assertEquals(FontWeight.Medium, fontWeightOf("置信区间"))
        assertEquals(FontWeight.Medium, fontWeightOf("点估计"))
        assertEquals(FontWeight.Normal, fontWeightOf("Confidence Interval (CI)"))
        assertEquals(FontWeight.Normal, fontWeightOf("Point Estimation"))
    }

    @Test
    fun flashcardFlipsCounterClockwiseAndBackInReverseDirection() {
        val term = Term(
            id = 10,
            subjectID = "analysis",
            subjectName = "数学分析",
            subjectNameEn = "Mathematical Analysis",
            chinese = "一致收敛",
            english = "uniform convergence",
            definition = "函数列在区间上一致趋于极限函数。",
            important = false,
            speechText = "uniform convergence",
            pronunciation = Pronunciation("/ˈjuːnɪfɔːrm/", "/ˈjuːnɪfɔːm/"),
        )
        val question = QuizQuestion(
            id = "fc-10",
            term = term,
            direction = "zhToEn",
            options = emptyList(),
            correctOptionID = term.id,
        )
        compose.setContent {
            AcademicTheme {
                Column(Modifier.width(360.dp)) {
                    FlashcardQuestionCard(
                        question = question,
                        busy = false,
                        onSpeak = {},
                        onSelfGrade = {},
                    )
                }
            }
        }
        assertEquals(-180f, flashcardTargetRotation(flipped = true), 0f)
        assertEquals(0f, flashcardTargetRotation(flipped = false), 0f)
        assertTrue(isFlashcardBackVisible(-91f))
        assertFalse(isFlashcardBackVisible(-89f))
        compose.onNodeWithText(" 翻转闪卡核对答案").assertIsDisplayed()
        compose.onNodeWithText("面试闪卡 · 正面").assertDoesNotExist()
        compose.onNodeWithText(" 翻转闪卡核对答案").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("面试闪卡 · 答案背面").assertDoesNotExist()
        compose.onNodeWithText("uniform convergence").assertIsDisplayed()
        val expectedUs = "US ${VocabularyCore.displayIpa(term.pronunciation.americanIPA, withSlashes = true)}"
        val expectedUk = "UK ${VocabularyCore.displayIpa(term.pronunciation.britishIPA, withSlashes = true)}"
        compose.onNodeWithText(expectedUs, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(expectedUk, useUnmergedTree = true).assertIsDisplayed()
        val usLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(expectedUs, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(usLayouts)
        assertEquals(FontFamily.SansSerif, usLayouts.firstOrNull()?.layoutInput?.style?.fontFamily)
        // 再次点击翻回正面
        compose.onNodeWithText("uniform convergence").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(" 翻转闪卡核对答案").assertIsDisplayed()
    }

    @Test
    fun relatedTermCardOmitsIPAAndShowsEnglishTermOnly() {
        val term = Term(
            id = 64,
            subjectID = "analysis",
            subjectName = "数学分析",
            subjectNameEn = "Mathematical Analysis",
            chinese = "一致连续定理",
            english = "uniform continuity theorem",
            definition = "闭区间上的连续函数一致连续。",
            important = false,
            speechText = "uniform continuity theorem",
            pronunciation = Pronunciation("ˌjuːnɪˈfɔːrm", "ˌjuːnɪˈfɔːm"),
        )
        compose.setContent {
            AcademicTheme {
                Column(Modifier.width(360.dp)) {
                    RelatedTermCard(rel = term, onSelect = {}, onSpeak = {})
                }
            }
        }
        compose.onNodeWithText("一致连续定理").assertIsDisplayed()
        compose.onNodeWithText("uniform continuity theorem").assertIsDisplayed()
        compose.onNodeWithText("US", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }
}
