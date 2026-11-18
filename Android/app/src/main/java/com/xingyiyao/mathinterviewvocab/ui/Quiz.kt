package com.xingyiyao.mathinterviewvocab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xingyiyao.mathinterviewvocab.AppUiState
import com.xingyiyao.mathinterviewvocab.AppViewModel
import com.xingyiyao.mathinterviewvocab.core.QuizPreferences
import com.xingyiyao.mathinterviewvocab.core.QuizQuestion
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

val QuizScopes = listOf(
    "important" to "重点词汇", "all" to "全部词汇", "subject" to "指定科目",
    "mistakes" to "错题复习", "favorites" to "我的收藏",
)
val QuizDirections = listOf("zhToEn" to "中文 → 英文", "enToZh" to "英文 → 中文", "mixed" to "双向混合")
val QuizPracticeModes = listOf(
    "choice" to ("四选一客观题" to "从 4 个干扰项中辨析准确术语"),
    "flashcard" to ("面试闪卡翻转自测" to "先主动口述回忆，再 3D 翻面核对并自评"),
)
val QuizLengths = listOf("ten" to "10 题", "twenty" to "20 题", "fifty" to "50 题", "all" to "全部")

@Composable
fun QuizScreen(state: AppUiState, viewModel: AppViewModel) {
    val quiz = state.screen.quiz
    when {
        quiz == null -> QuizSetup(state, viewModel)
        quiz.stage == "summary" -> QuizSummary(state, viewModel)
        else -> ActiveQuiz(state, viewModel)
    }
}

/**
 * 7. 开关联动：点组内开关，同组其他开关仅做涟漪式震动，状态功能不变。
 */
@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true,
    supporting: String? = null,
    rippleSignal: Int = 0,
    rippleDistance: Int = 0,
) {
    val shakeX = remember { Animatable(0f) }
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(rippleSignal) {
        if (rippleSignal > 0 && rippleDistance > 0) {
            delay(rippleDistance * 28L)
            pulseScale.snapTo(0.985f)
            shakeX.animateTo(7f, tween(45))
            shakeX.animateTo(-5f, tween(45))
            shakeX.animateTo(2.5f, tween(40))
            shakeX.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 500f))
            pulseScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 450f))
        } else if (rippleSignal > 0 && rippleDistance == 0) {
            pulseScale.animateTo(1.02f, tween(70))
            pulseScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 450f))
        }
    }

    Card(
        onClick = onSelect,
        enabled = enabled,
        shape = Academic.Shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Academic.Blue.copy(alpha = if (Academic.IsDark) 0.18f else 0.07f) else Academic.Surface,
        ),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Academic.Blue else Academic.Grid),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).bounceClick(enabled).graphicsLayer {
            translationX = shakeX.value
            scaleX = pulseScale.value
            scaleY = pulseScale.value
        },
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, color = Academic.Ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                if (supporting != null) Text(supporting, color = Academic.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizSetup(state: AppUiState, viewModel: AppViewModel) {
    val preferences = state.progress.quizPreferences
    val pool = VocabularyCore.makePool(state.terms, preferences, state.progress)
    val count = VocabularyCore.resolveQuestionCount(preferences.questionLength, pool.size)
    var modeRipple by remember { mutableIntStateOf(0) }
    var modeOrigin by remember { mutableIntStateOf(0) }
    var directionRipple by remember { mutableIntStateOf(0) }
    var directionOrigin by remember { mutableIntStateOf(0) }
    var scopeRipple by remember { mutableIntStateOf(0) }
    var scopeOrigin by remember { mutableIntStateOf(0) }

    fun save(value: QuizPreferences) { viewModel.setPreferences(value) }
    ReadingColumn {
        SectionHeading("检测", "支持四选一客观检测与面试闪卡 3D 翻转自测；每次答题立即保存到本机。")
        AcademicCard {
            Text("训练模式", style = MaterialTheme.typography.titleLarge, color = Academic.Ink)
            QuizPracticeModes.forEachIndexed { index, (id, pair) ->
                val (label, subtitle) = pair
                ChoiceRow(
                    label = label,
                    selected = state.screen.quizPracticeMode == id,
                    onSelect = {
                        modeOrigin = index
                        modeRipple += 1
                        viewModel.setQuizPracticeMode(id)
                    },
                    enabled = !state.busy,
                    supporting = subtitle,
                    rippleSignal = modeRipple,
                    rippleDistance = abs(index - modeOrigin),
                )
            }
        }
        AcademicCard {
            Text("检测方向", style = MaterialTheme.typography.titleLarge, color = Academic.Ink)
            QuizDirections.forEachIndexed { index, (id, label) ->
                ChoiceRow(
                    label = label,
                    selected = preferences.direction == id,
                    onSelect = {
                        directionOrigin = index
                        directionRipple += 1
                        save(preferences.copy(direction = id))
                    },
                    enabled = !state.busy,
                    rippleSignal = directionRipple,
                    rippleDistance = abs(index - directionOrigin),
                )
            }
        }
        AcademicCard {
            Text("检测范围", style = MaterialTheme.typography.titleLarge, color = Academic.Ink)
            QuizScopes.forEachIndexed { index, (id, label) ->
                val available = VocabularyCore.makePool(state.terms, preferences.copy(scope = id), state.progress).size
                ChoiceRow(
                    label = label,
                    selected = preferences.scope == id,
                    onSelect = {
                        scopeOrigin = index
                        scopeRipple += 1
                        save(preferences.copy(scope = id))
                    },
                    enabled = !state.busy,
                    supporting = "可出题 $available 个词汇",
                    rippleSignal = scopeRipple,
                    rippleDistance = abs(index - scopeOrigin),
                )
            }
            if (preferences.scope == "subject") SubjectDropdown(
                subjects = state.orderedSubjects,
                selected = preferences.selectedSubjectID,
                onSelect = { id -> if (id != null) save(preferences.copy(selectedSubjectID = id)) },
                allowAll = false,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = preferences.includeMastered,
                    onCheckedChange = { save(preferences.copy(includeMastered = it)) },
                    enabled = !state.busy,
                )
                Text("包含已掌握词汇", Modifier.weight(1f), color = Academic.Ink)
            }
            Text(
                if (preferences.includeMastered) "范围中包含所有符合条件的词汇。"
                else "已从可出题数量中扣除已掌握词汇。",
                color = Academic.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        AcademicCard {
            Text("题目数量（拖动滑杆或点击刻度）", style = MaterialTheme.typography.titleLarge, color = Academic.Ink)
            SnapPhysicsSlider(
                options = QuizLengths,
                selected = preferences.questionLength,
                onSelect = { save(preferences.copy(questionLength = it)) },
                enabled = !state.busy,
            )
            HorizontalDivider(color = Academic.Grid)
            Text("本次检测 $count 题", style = MaterialTheme.typography.titleMedium, color = Academic.Ink)
            if (count == 0) Text("当前范围没有可检测词汇。请选择其他范围，或包含已掌握词汇。", color = Academic.Muted)
            Button(
                onClick = { viewModel.startQuiz() },
                enabled = count > 0 && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).bounceClick(count > 0 && !state.busy),
            ) {
                Text(if (state.busy) "正在保存…" else "开始检测")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveQuiz(state: AppUiState, viewModel: AppViewModel) {
    val quiz = state.screen.quiz ?: return
    val question = state.currentQuestion ?: return
    val answered = quiz.selectedOptionID != null
    val correct = quiz.selectedOptionID == question.correctOptionID
    val isFlashcard = quiz.mode == "flashcard"
    var confirmEnd by remember { mutableStateOf(false) }

    BackHandler { confirmEnd = true }
    ReadingColumn(resetKey = question.id) {
        val subtitleText = buildString {
            append("第 ${quiz.index + 1} / ${quiz.questions.size} 题 · ")
            if (isFlashcard) append("面试闪卡自测 · ")
            append(if (question.direction == "zhToEn") "选择对应的英文术语" else "选择对应的中文术语")
        }
        SectionHeading("检测进行中", subtitleText)
        MasteryLine((quiz.index + if (answered) 1.0 else 0.0) / quiz.questions.size)

        if (isFlashcard && !answered) {
            FlashcardQuestionCard(
                question = question,
                busy = state.busy,
                onSpeak = { accent -> viewModel.speak(question.term.speechText, accent) },
                onSelfGrade = { knewIt -> viewModel.answerFlashcard(knewIt) },
            )
        } else {
            QuizQuestionPromptCard(question = question, isFlashcard = isFlashcard)
            if (!isFlashcard) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    question.options.forEachIndexed { index, term ->
                        val chosen = term.id == quiz.selectedOptionID
                        val right = term.id == question.correctOptionID
                        QuizOptionCard(
                            index = index,
                            label = VocabularyCore.displayMathNotation(
                                if (question.direction == "zhToEn") term.english else term.chinese,
                            ),
                            chosen = chosen,
                            right = right,
                            answered = answered,
                            enabled = !answered && !state.busy,
                            onSelect = { viewModel.answer(term.id) },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = answered,
            enter = expandVertically(spring(dampingRatio = 0.76f, stiffness = 340f)) + fadeIn(tween(180)),
            exit = shrinkVertically() + fadeOut(),
        ) {
            AcademicCard {
                Text(
                    if (correct) "回答正确" else "再记一次",
                    color = if (correct) Academic.Success else Academic.Error,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    VocabularyCore.displayMathNotation(question.term.chinese),
                    style = MaterialTheme.typography.titleMedium,
                    color = Academic.Ink,
                )
                PronunciationPanel(
                    question.term,
                    onSpeak = { text, accent ->
                        if (accent == null) viewModel.speak(text) else viewModel.speak(text, accent)
                    },
                    onStop = viewModel::stopSpeech,
                    emphasizeImportant = false,
                )
                Text(VocabularyCore.displayMathNotation(question.term.definition), color = Academic.Muted)
                OutlinedButton(
                    onClick = { viewModel.toggleFavorite(question.term.id) },
                    enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp).bounceClick(!state.busy),
                ) {
                    Icon(
                        if (question.term.id in state.progress.favoriteIDs) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                    )
                    Text(if (question.term.id in state.progress.favoriteIDs) " 取消收藏" else " 收藏词汇")
                }
                if (!quiz.answerSaved) {
                    Text(
                        if (state.busy) "正在保存答题记录…" else "此题尚未保存。请重试，保存成功后再继续。",
                        color = if (state.busy) Academic.Muted else Academic.Error,
                    )
                    if (!state.busy) OutlinedButton(
                        onClick = viewModel::retryAnswerSave,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("重试保存") }
                }
                Button(
                    onClick = viewModel::nextQuestion,
                    enabled = quiz.answerSaved && !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).bounceClick(quiz.answerSaved && !state.busy),
                ) {
                    Text(if (quiz.index + 1 == quiz.questions.size) "查看结果" else "下一题")
                }
            }
        }
        TextButton(
            onClick = { confirmEnd = true },
            enabled = !state.busy,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("结束本次检测") }
    }
    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        title = { Text("结束检测？") },
        text = { Text("已成功保存的答题记录会保留。当前未完成的题目不计分。") },
        confirmButton = { TextButton(onClick = { confirmEnd = false; viewModel.finishQuiz() }) { Text("结束检测") } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("继续答题") } },
    )
}

/**
 * 7. 面试闪卡（Flashcard）3D 翻转自测卡片
 */
internal fun flashcardTargetRotation(flipped: Boolean): Float = if (flipped) -180f else 0f

internal fun isFlashcardBackVisible(rotation: Float): Boolean = rotation < -90f

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlashcardQuestionCard(
    question: QuizQuestion,
    busy: Boolean,
    onSpeak: (String) -> Unit,
    onSelfGrade: (Boolean) -> Unit,
) {
    var flipped by rememberSaveable(question.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = flashcardTargetRotation(flipped),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
        label = "flashcardFlipRotation",
    )
    val showBack = isFlashcardBackVisible(rotation)

    Card(
        onClick = { flipped = !flipped },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Academic.Surface),
        border = BorderStroke(1.5.dp, if (showBack) Academic.Blue else Academic.Grid),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    if (showBack) rotationY = -180f
                }
                .padding(22.dp),
        ) {
            if (!showBack) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TermBadge(question.term.subjectName, Academic.Blue)
                    Text(
                        VocabularyCore.displayMathNotation(
                            if (question.direction == "zhToEn") question.term.chinese else question.term.english,
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = if (question.direction == "zhToEn") FontFamily.Serif else FontFamily.SansSerif,
                        color = if (question.direction == "zhToEn") Academic.Ink else Academic.Blue,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        color = Academic.SecondarySurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "💡 面试模拟提示：先在心中或出声拼读目标术语与核心定义，再点击卡片翻转核对。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Academic.Muted,
                        )
                    }
                    FilledTonalButton(
                        onClick = { flipped = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Text(" 翻转闪卡核对答案")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TermBadge(question.term.subjectName, Academic.Blue)
                    Text(
                        VocabularyCore.displayMathNotation(question.term.chinese),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Academic.Ink,
                    )
                    Text(
                        VocabularyCore.displayMathNotation(question.term.english),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Academic.Blue,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "US ${VocabularyCore.displayIpa(question.term.pronunciation.americanIPA, withSlashes = true)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.SansSerif,
                            color = Academic.Muted,
                        )
                        Text(
                            "UK ${VocabularyCore.displayIpa(question.term.pronunciation.britishIPA, withSlashes = true)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.SansSerif,
                            color = Academic.Muted,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onSpeak("en-US") },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null)
                            Text(" 美式发音")
                        }
                        OutlinedButton(
                            onClick = { onSpeak("en-GB") },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null)
                            Text(" 英式发音")
                        }
                    }
                    HorizontalDivider(color = Academic.Grid)
                    Text(
                        VocabularyCore.displayMathNotation(question.term.definition),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Academic.Ink,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onSelfGrade(false) },
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp).bounceClick(!busy),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null, tint = Academic.Error)
                            Text(" 记错了·需复习", color = Academic.Error)
                        }
                        Button(
                            onClick = { onSelfGrade(true) },
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp).bounceClick(!busy),
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                            Text(" 熟练答出")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizSummary(state: AppUiState, viewModel: AppViewModel) {
    val quiz = state.screen.quiz ?: return
    val attempts = state.progress.attempts.filter { it.id in quiz.attemptIDs }
    val correctCount = attempts.count { it.selectedOptionID == it.correctOptionID }
    val wrongIDs = attempts.filter { it.selectedOptionID != it.correctOptionID }.map { it.termID }.toSet()
    val accuracy = if (attempts.isEmpty()) 0 else (100.0 * correctCount / attempts.size).roundToInt()
    BackHandler { viewModel.finishQuiz() }
    ReadingColumn {
        SectionHeading("检测完成", "每一次回忆，都让表达更清晰。")
        AcademicCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric(accuracy.toString() + "%", "本次正确率")
                Metric(correctCount.toString() + " / " + attempts.size, "答对题目")
                Metric(wrongIDs.size.toString(), "需要巩固")
            }
            Button(
                onClick = viewModel::finishQuiz,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).bounceClick(),
            ) {
                Text("返回检测设置")
            }
        }
        if (wrongIDs.isNotEmpty()) {
            Text("本次待巩固词汇", style = MaterialTheme.typography.titleLarge, color = Academic.Ink)
            state.terms.filter { it.id in wrongIDs }.forEach { term ->
                Card(
                    onClick = { viewModel.selectTerm(term.id) },
                    shape = Academic.Shape,
                    colors = CardDefaults.cardColors(containerColor = Academic.Surface),
                    border = BorderStroke(1.dp, Academic.Grid),
                    modifier = Modifier.fillMaxWidth().bounceClick(),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(VocabularyCore.displayMathNotation(term.chinese), fontWeight = FontWeight.Bold, color = Academic.Ink)
                        Text(
                            VocabularyCore.displayMathNotation(term.english),
                            color = Academic.Blue,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
        } else {
            AcademicCard {
                Text("本次全部答对", style = MaterialTheme.typography.titleMedium, color = Academic.Success)
                Text("可以尝试其他科目，或切换检测方向继续练习。", color = Academic.Muted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuizQuestionPromptCard(
    question: QuizQuestion,
    isFlashcard: Boolean = false,
) {
    AcademicCard {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TermBadge(question.term.subjectName, Academic.Blue)
            if (isFlashcard) TermBadge("面试闪卡", Academic.Blue)
        }
        Text(
            VocabularyCore.displayMathNotation(
                if (question.direction == "zhToEn") question.term.chinese else question.term.english,
            ),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = if (question.direction == "zhToEn") FontFamily.Serif else FontFamily.SansSerif,
            color = if (question.direction == "zhToEn") Academic.Ink else Academic.Blue,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun QuizOptionCard(
    index: Int,
    label: String,
    chosen: Boolean,
    right: Boolean,
    answered: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val color = when {
        answered && right -> Academic.Success
        answered && chosen -> Academic.Error
        else -> Academic.Grid
    }
    val bgColor = if (answered && (right || chosen)) color.copy(alpha = 0.08f) else Academic.Surface
    Card(
        onClick = onSelect,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).bounceClick(enabled),
        shape = Academic.Shape,
        border = BorderStroke(if (answered && (right || chosen)) 1.5.dp else 1.dp, color),
        colors = CardDefaults.cardColors(
            containerColor = bgColor,
            disabledContainerColor = bgColor,
            disabledContentColor = Academic.Ink,
        ),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                ('A' + index).toString(),
                color = Academic.Blue,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    label,
                    fontWeight = FontWeight.Normal,
                    color = Academic.Ink,
                )
                if (answered && right) Text("正确答案", color = Academic.Success, style = MaterialTheme.typography.labelMedium)
                else if (answered && chosen) Text("你的选择", color = Academic.Error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
