package com.xingyiyao.mathinterviewvocab.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.text.Selection
import android.text.Spannable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.xingyiyao.mathinterviewvocab.AppUiState
import com.xingyiyao.mathinterviewvocab.core.MathAssetEntry
import com.xingyiyao.mathinterviewvocab.core.MathAssetStore
import com.xingyiyao.mathinterviewvocab.core.MathManifest
import com.xingyiyao.mathinterviewvocab.core.MathSvgDocument
import com.xingyiyao.mathinterviewvocab.core.MathTextDocument
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import com.xingyiyao.mathinterviewvocab.core.canonicalTerms
import com.xingyiyao.mathinterviewvocab.data.AndroidMathAssets
import com.xingyiyao.mathinterviewvocab.data.LocalMathAssets
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class MathUiTest {
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

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun repositoryRoot(): File =
        System.getProperty("math.repositoryRoot")?.let(::File)
            ?: generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
                .first { File(it, "Resources/terms.json").isFile }

    private fun fixtureStore(folder: String = "Resources/Math"): MathAssetStore {
        val dir = File(repositoryRoot(), folder)
        return AndroidMathAssets.fromBytesLoader { rel ->
            File(dir, rel.removePrefix("math/")).readBytes()
        }
    }

    @Test
    fun bundledAndroidAssetsLoadAndVerifyChecksum() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val bundled = AndroidMathAssets.get(context)
        val entry = bundled.entry("H_0", display = false)
        assertNotNull("Bundled APK assets should contain H_0", entry)
        val doc = bundled.document(entry!!)
        assertNotNull(doc)
        assertTrue(doc!!.ops.isNotEmpty())

        // 篡改 SVG SHA256 时必须拒绝加载
        val dir = File(repositoryRoot(), "Resources/Math")
        val corruptedStore = AndroidMathAssets.fromBytesLoader { rel ->
            val bytes = File(dir, rel.removePrefix("math/")).readBytes()
            if (rel.endsWith(".svg")) bytes + byteArrayOf(' '.code.toByte()) else bytes
        }
        val badEntry = corruptedStore.entry("H_0", display = false)!!
        assertNull(corruptedStore.document(badEntry))
    }

    @Test
    fun all44FormulaTermsRenderUnderBothThemesAnd200PercentFontScale() {
        val terms = canonicalTerms().filter { term ->
            MathTextDocument.containsMath(term.chinese) ||
                MathTextDocument.containsMath(term.english) ||
                MathTextDocument.containsMath(term.definition)
        }
        assertEquals(44, terms.size)
        val store = fixtureStore()
        var dark by mutableStateOf(false)
        var fontScale by mutableStateOf(1f)
        var useSelectable by mutableStateOf(false)

        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalMathAssets provides store,
            ) {
                AcademicTheme(darkTheme = dark) {
                    Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) {
                        terms.forEach { term ->
                            if (useSelectable) {
                                SelectableMathText(term.definition)
                            } else {
                                RichMathText(term.english)
                                RichMathText(term.definition)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            dark = true
            fontScale = 2f
        }
        compose.waitForIdle()
        compose.runOnIdle {
            dark = false
            fontScale = 1f
            useSelectable = true
        }
        compose.waitForIdle()
    }

    @Test
    fun wideDimensionFormulaScrollsInSelectableViewAndScalesInCards() {
        val store = fixtureStore()
        val wideTerm = canonicalTerms().first { it.definition.contains("\\dim(U+W)") }
        val dimEntry = store.entry("\\dim(U+W)=\\dim U+\\dim W-\\dim(U\\cap W)", display = false)!!

        // 1. ScaleToFit 度量断言（卡片场景不裁剪、不超宽、无负数偏移）
        val metrics = computeInlineBoxMetrics(
            entry = dimEntry,
            emPx = 32f,
            maxWidthPx = 300f,
            textCenterAboveBaselineEm = 0.34f,
        )
        assertTrue(metrics.scale < 1f)
        assertTrue(metrics.widthEm * 32f <= 300.5f)
        assertTrue(metrics.svgTopInBoxFraction >= 0f && metrics.svgBottomInBoxFraction <= 1f)

        // 2. Detail 释义：SelectableMathText 自动拆出横滚容器；切换至卡片模式则收缩不出现横滚
        var showCards by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1.5f),
                LocalMathAssets provides store,
            ) {
                AcademicTheme {
                    Column(Modifier.width(320.dp)) {
                        if (!showCards) {
                            SelectableMathText(wideTerm.definition, allowHorizontalScrollOverflow = true)
                        } else {
                            QuizOptionCard(
                                index = 0,
                                label = "Null Hypothesis（\\(H_0\\)）",
                                chosen = false,
                                right = false,
                                answered = false,
                                enabled = true,
                                onSelect = {},
                            )
                            TermRow(
                                term = canonicalTerms().first { it.id == 323 },
                                state = AppUiState(loading = false),
                                onSpeak = {},
                                onToggleFavorite = {},
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("math-scroll").assertIsDisplayed()

        compose.runOnIdle { showCards = true }
        compose.waitForIdle()
        compose.onNodeWithTag("math-scroll").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Null Hypothesis（H_0）", substring = true, useUnmergedTree = true)
            .onFirst().assertIsDisplayed()
    }

    @Test
    fun nativeViewsExtractAndCopyOriginalLatexAndKeepPlainWordSelection() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val store = fixtureStore()
        val mixedSource = "中文🙂 \\(H_0\\) 与 \\(H_a\\)。"

        // 1. 直接验证 buildMathSpannable 与 extractLatexSelection
        val built = buildMathSpannable(mixedSource, store)
        assertTrue(built.toString().contains('\uFFFC'))
        assertEquals(mixedSource, extractLatexSelection(built, 0, built.length))
        val objIndex = built.indexOf('\uFFFC')
        assertEquals("\\(H_0\\)", extractLatexSelection(built, objIndex, objIndex + 1))

        // 2. 验证复杂夹具全部能构建并通过选区还原源码
        val fixtureDir = File(repositoryRoot(), "Tests/Fixtures/Math")
        val fixtureStore = fixtureStore("Tests/Fixtures/Math")
        val fixtureManifest = MathManifest.parseAndValidate(File(fixtureDir, "manifest.json").readText())
        for (entry in fixtureManifest.entries) {
            val source = "前文 \\[${entry.tex}\\] 后文"
            val spanText = buildMathSpannable(source, fixtureStore)
            assertEquals(source, extractLatexSelection(spanText, 0, spanText.length))
        }

        // 3. 验证 MathTextView 拦截系统 copy 写入剪贴板
        val mathView = MathTextView(context).apply {
            setText(built, android.widget.TextView.BufferType.SPANNABLE)
        }
        Selection.setSelection(mathView.text as Spannable, objIndex, objIndex + 1)
        assertTrue(mathView.onTextContextMenuItem(android.R.id.copy))
        assertEquals("\\(H_0\\)", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        Selection.setSelection(mathView.text as Spannable, 0, mathView.text.length)
        assertTrue(mathView.onTextContextMenuItem(android.R.id.copy))
        assertEquals(mixedSource, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // 4. 验证 PronunciationTextView 混排选中单词 vs 选中公式
        val enSource = "Null Hypothesis（\\(H_0\\)）"
        var lastSelection: String? = null
        val pronunciationView = PronunciationTextView(context).apply {
            selectionChanged = { s, e -> lastSelection = pronunciationSelection(text, s, e) }
            setText(buildMathSpannable(enSource, store), android.widget.TextView.BufferType.SPANNABLE)
        }
        // 选中普通英文单词 "Null"
        Selection.setSelection(pronunciationView.text as Spannable, 0, 4)
        assertEquals("Null", lastSelection)
        assertNull(MathTextDocument.mathSpeechBlockMessage(lastSelection!!))

        // 选中数学公式 "\uFFFC"
        val mathPos = pronunciationView.text.indexOf('\uFFFC')
        Selection.setSelection(pronunciationView.text as Spannable, mathPos, mathPos + 1)
        assertEquals("\\(H_0\\)", lastSelection)
        assertNotNull(MathTextDocument.mathSpeechBlockMessage(lastSelection!!))
        assertTrue(pronunciationView.onTextContextMenuItem(android.R.id.copy))
        assertEquals("\\(H_0\\)", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun missingFormulaRendersFallbackNoticeWhileKeepingSurroundingText() {
        val emptyStore = object : MathAssetStore {
            override fun entry(latex: String, display: Boolean): MathAssetEntry? = null
            override fun document(entry: MathAssetEntry): MathSvgDocument? = null
        }
        compose.setContent {
            CompositionLocalProvider(LocalMathAssets provides emptyStore) {
                AcademicTheme {
                    Column(Modifier.width(360.dp)) {
                        RichMathText("测试 \\(unbundled_{formula}\\) 结尾")
                    }
                }
            }
        }
        compose.onNodeWithText("公式显示异常", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("unbundled_{formula}", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("结尾", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }
}
