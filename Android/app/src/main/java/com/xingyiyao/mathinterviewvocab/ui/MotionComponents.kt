package com.xingyiyao.mathinterviewvocab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 通用轻量化按压回弹修饰符，阻尼柔和。 */
fun Modifier.bounceClick(enabled: Boolean = true): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "bounceScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 1. 主题切换：以点击触点为圆心圆形扩散换页，深浅页预加载仅裁切，元素无变形缩放。
 */
@Composable
fun CircularRevealThemeHost(
    darkTheme: Boolean,
    revealOrigin: Offset?,
    content: @Composable (Boolean) -> Unit,
) {
    var baseTheme by remember { mutableStateOf(darkTheme) }
    var overlayTheme by remember { mutableStateOf<Boolean?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val revealRadius = remember { Animatable(0f) }

    LaunchedEffect(darkTheme) {
        if (darkTheme == baseTheme && overlayTheme == null) return@LaunchedEffect
        val width = containerSize.width.toFloat().coerceAtLeast(1080f)
        val height = containerSize.height.toFloat().coerceAtLeast(1920f)
        val origin = revealOrigin ?: Offset(width - 72f, 72f)
        val maxRadius = maxOf(
            hypot(origin.x, origin.y),
            hypot(width - origin.x, origin.y),
            hypot(origin.x, height - origin.y),
            hypot(width - origin.x, height - origin.y),
        ).coerceAtLeast(1200f)
        overlayTheme = darkTheme
        revealRadius.snapTo(0f)
        revealRadius.animateTo(
            targetValue = maxRadius,
            animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        )
        baseTheme = darkTheme
        overlayTheme = null
    }

    Box(
        Modifier.fillMaxSize().onGloballyPositioned { containerSize = it.size },
    ) {
        AcademicTheme(darkTheme = baseTheme) {
            content(baseTheme)
        }
        val activeOverlay = overlayTheme
        if (activeOverlay != null) {
            val width = containerSize.width.toFloat().coerceAtLeast(1080f)
            val height = containerSize.height.toFloat().coerceAtLeast(1920f)
            val origin = revealOrigin ?: Offset(width - 72f, 72f)
            val radius = revealRadius.value
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    val path = Path().apply {
                        addOval(Rect(center = origin, radius = radius))
                    }
                    clipPath(path) {
                        this@drawWithContent.drawContent()
                    }
                },
            ) {
                AcademicTheme(darkTheme = activeOverlay) {
                    content(activeOverlay)
                }
            }
        }
    }
}

/**
 * 5. 文本展开：点展开容器高度平滑拉伸，右侧箭头同步转 180 度，无突兀闪现。
 */
@Composable
fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
        label = "arrowRotation",
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Academic.Shape,
        border = BorderStroke(1.dp, Academic.Grid),
        colors = CardDefaults.cardColors(containerColor = Academic.Surface),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Academic.Ink)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Academic.Muted)
                    }
                }
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = Academic.Blue,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 340f),
                    expandFrom = Alignment.Top,
                ) + fadeIn(tween(180)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(tween(140)),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * 10. 标签选择：点标签时选中项轻微放大，周边标签自动向两侧平移让位。
 */
@Composable
fun ElasticTagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Academic.Blue,
    compact: Boolean = false,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) (if (compact) 1.04f else 1.08f) else 1f,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 420f),
        label = "chipScale",
    )
    val sideSpacer by animateDpAsState(
        targetValue = if (selected) (if (compact) 4.dp else 6.dp) else 0.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "chipShiftAside",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(sideSpacer))
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            color = if (selected) accentColor else Academic.Surface,
            border = BorderStroke(1.dp, if (selected) accentColor else Academic.Grid),
            shadowElevation = if (selected) (if (compact) 2.dp else 3.dp) else 0.dp,
            modifier = modifier.heightIn(min = if (compact) 32.dp else 40.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        ) {
            Box(
                Modifier.padding(
                    horizontal = if (compact) 11.dp else 14.dp,
                    vertical = if (compact) 5.dp else 8.dp,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.White else Academic.Ink,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.width(sideSpacer))
    }
}

/**
 * 4. 滑杆吸附：拖滑杆松手后带惯性过冲，再回弹吸附到最近刻度，有物理重量感。
 */
@Composable
fun <T> SnapPhysicsSlider(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return
    val count = options.size
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex) {
        if (!dragging) {
            val current = position.value
            val delta = selectedIndex - current
            if (abs(delta) > 0.01f) {
                val overshoot = (selectedIndex + delta.coerceIn(-1f, 1f) * 0.14f)
                    .coerceIn(-0.18f, (count - 1) + 0.18f)
                position.animateTo(overshoot, tween(110, easing = FastOutSlowInEasing))
                position.animateTo(
                    selectedIndex.toFloat(),
                    spring(dampingRatio = 0.52f, stiffness = 360f),
                )
            }
        }
    }

    val primaryColor = Academic.Blue
    val gridColor = Academic.Grid
    val surfaceColor = Academic.Surface

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(44.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val density = LocalDensity.current
            val padPx = with(density) { 18.dp.toPx() }
            val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val usableWidthPx = (totalWidthPx - padPx * 2f).coerceAtLeast(1f)
            val stepPx = if (count > 1) usableWidthPx / (count - 1) else 0f

            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(enabled, count, usableWidthPx) {
                        if (!enabled || count <= 1) return@pointerInput
                        val tracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                tracker.resetTracking()
                                val rawIndex = ((offset.x - padPx) / stepPx).coerceIn(0f, (count - 1).toFloat())
                                scope.launch { position.snapTo(rawIndex) }
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val rawIndex = ((change.position.x - padPx) / stepPx)
                                    .coerceIn(-0.15f, (count - 1) + 0.15f)
                                scope.launch { position.snapTo(rawIndex) }
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch {
                                    position.animateTo(
                                        selectedIndex.toFloat(),
                                        spring(dampingRatio = 0.55f, stiffness = 380f),
                                    )
                                }
                            },
                            onDragEnd = {
                                val velocityX = tracker.calculateVelocity().x
                                val velocityIndex = (velocityX / stepPx).coerceIn(-2.5f, 2.5f)
                                val projected = (position.value + velocityIndex * 0.12f)
                                val targetIndex = projected.roundToInt().coerceIn(0, count - 1)
                                val overshoot = (targetIndex + (velocityIndex * 0.08f).coerceIn(-0.22f, 0.22f))
                                    .coerceIn(-0.22f, (count - 1) + 0.22f)
                                dragging = false
                                onSelect(options[targetIndex].first)
                                scope.launch {
                                    position.animateTo(overshoot, tween(95, easing = FastOutSlowInEasing))
                                    position.animateTo(
                                        targetIndex.toFloat(),
                                        spring(dampingRatio = 0.48f, stiffness = 340f),
                                    )
                                }
                            },
                        )
                    }
                    .pointerInput(enabled, count, usableWidthPx) {
                        if (!enabled || count <= 1) return@pointerInput
                        detectTapGestures { offset ->
                            val targetIndex = ((offset.x - padPx) / stepPx).roundToInt().coerceIn(0, count - 1)
                            val direction = (targetIndex - position.value).coerceIn(-1f, 1f)
                            val overshoot = (targetIndex + direction * 0.15f)
                                .coerceIn(-0.18f, (count - 1) + 0.18f)
                            onSelect(options[targetIndex].first)
                            scope.launch {
                                position.animateTo(overshoot, tween(105, easing = FastOutSlowInEasing))
                                position.animateTo(
                                    targetIndex.toFloat(),
                                    spring(dampingRatio = 0.50f, stiffness = 350f),
                                )
                            }
                        }
                    },
            ) {
                val centerY = size.height / 2f
                val startX = padPx
                val endX = size.width - padPx
                val thumbX = (startX + position.value * stepPx).coerceIn(startX - 18f, endX + 18f)

                drawLine(
                    color = gridColor,
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = 6.dp.toPx(),
                )
                drawLine(
                    color = primaryColor,
                    start = Offset(startX, centerY),
                    end = Offset(thumbX.coerceIn(startX, endX), centerY),
                    strokeWidth = 6.dp.toPx(),
                )
                for (i in 0 until count) {
                    val tickX = startX + i * stepPx
                    val passed = tickX <= thumbX + 2f
                    drawCircle(
                        color = if (passed) primaryColor else gridColor,
                        radius = 5.dp.toPx(),
                        center = Offset(tickX, centerY),
                    )
                }
                drawCircle(
                    color = primaryColor.copy(alpha = 0.18f),
                    radius = 16.dp.toPx(),
                    center = Offset(thumbX, centerY),
                )
                drawCircle(
                    color = primaryColor,
                    radius = 11.dp.toPx(),
                    center = Offset(thumbX, centerY),
                )
                drawCircle(
                    color = surfaceColor,
                    radius = 4.5.dp.toPx(),
                    center = Offset(thumbX, centerY),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, (value, label) ->
                val active = value == selected
                ElasticTagChip(
                    label = label,
                    selected = active,
                    onClick = {
                        if (!enabled) return@ElasticTagChip
                        val direction = (index - position.value).coerceIn(-1f, 1f)
                        val overshoot = (index + direction * 0.15f)
                            .coerceIn(-0.18f, (count - 1) + 0.18f)
                        onSelect(value)
                        scope.launch {
                            position.animateTo(overshoot, tween(105, easing = FastOutSlowInEasing))
                            position.animateTo(
                                index.toFloat(),
                                spring(dampingRatio = 0.50f, stiffness = 350f),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * 3. 批量勾选：点全选后选项从上到下依次错峰勾选，每项勾选带轻微弹性放大。
 */
data class BatchFilterItem(
    val id: String,
    val label: String,
    val checked: Boolean,
    val onToggle: (Boolean) -> Unit,
)

@Composable
fun StaggeredBatchFilterPanel(
    items: List<BatchFilterItem>,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scales = remember(items.size) { List(items.size) { Animatable(1f) } }
    val allChecked = items.all { it.checked }
    val anyChecked = items.any { it.checked }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "组合筛选（支持错峰全选）",
                style = MaterialTheme.typography.labelLarge,
                color = Academic.Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = {
                        val target = !allChecked
                        scope.launch {
                            items.forEachIndexed { index, item ->
                                launch {
                                    delay(index * 55L)
                                    if (item.checked != target) {
                                        item.onToggle(target)
                                    }
                                    scales[index].animateTo(1.16f, tween(90))
                                    scales[index].animateTo(
                                        1f,
                                        spring(dampingRatio = 0.46f, stiffness = 440f),
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Text(if (allChecked) "取消全选" else "全选筛选")
                }
                if (anyChecked) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                items.indices.reversed().forEachIndexed { step, index ->
                                    launch {
                                        delay(step * 45L)
                                        scales[index].animateTo(0.92f, tween(70))
                                        scales[index].animateTo(
                                            1f,
                                            spring(dampingRatio = 0.55f, stiffness = 420f),
                                        )
                                    }
                                }
                                onClearAll()
                            }
                        },
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Text("清除筛选")
                    }
                }
            }
        }
        items.forEachIndexed { index, item ->
            val scale = scales.getOrNull(index)?.value ?: 1f
            Surface(
                onClick = {
                    val next = !item.checked
                    item.onToggle(next)
                    scope.launch {
                        scales.getOrNull(index)?.let { anim ->
                            anim.animateTo(if (next) 1.14f else 0.94f, tween(85))
                            anim.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 440f))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                color = if (item.checked) Academic.Blue.copy(alpha = 0.09f) else Academic.Surface,
                border = BorderStroke(1.dp, if (item.checked) Academic.Blue else Academic.Grid),
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = null,
                        modifier = Modifier.padding(4.dp),
                    )
                    Text(
                        item.label,
                        Modifier.weight(1f).padding(start = 6.dp),
                        color = Academic.Ink,
                        fontWeight = if (item.checked) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * 8. 卡片删除：划走卡片沿曲线飞向角落删除图标，同步缩小旋转降透明度。
 */
@Composable
fun SwipeToFlyCornerCard(
    itemKey: Any,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "移除",
    content: @Composable (triggerDismiss: () -> Unit) -> Unit,
) {
    key(itemKey) {
        val scope = rememberCoroutineScope()
        val dragX = remember { Animatable(0f) }
        val flyProgress = remember { Animatable(0f) }
        var flying by remember { mutableStateOf(false) }

        fun startFlyOut(initialX: Float = 0f) {
            if (flying) return
            flying = true
            scope.launch {
                dragX.snapTo(initialX)
                flyProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
                )
                onDismissed()
            }
        }

        val t = flyProgress.value
        // 二次贝塞尔曲线：从当前位置抛弧线飞向右上角移除区，同步缩小、旋转、降透明度
        val startX = dragX.value
        val targetCornerX = 360f
        val controlX = (startX + targetCornerX) * 0.5f
        val curveX = (1f - t) * (1f - t) * startX + 2f * (1f - t) * t * controlX + t * t * targetCornerX
        val curveY = -140f * t * (1f - t) - 70f * t * t
        val scale = (1f - 0.78f * t).coerceAtLeast(0.15f)
        val alpha = (1f - t * 0.95f).coerceIn(0f, 1f)
        val rotation = (dragX.value / 28f).coerceIn(0f, 10f) * (1f - t) + 22f * t

        Box(modifier.fillMaxWidth()) {
            if (dragX.value > 12f || flying) {
                Row(
                    Modifier.matchParentSize()
                        .clip(Academic.Shape)
                        .background(Academic.Error.copy(alpha = 0.12f))
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = dismissLabel, tint = Academic.Error)
                    Spacer(Modifier.width(6.dp))
                    Text(dismissLabel, color = Academic.Error, style = MaterialTheme.typography.labelLarge)
                }
            }
            Box(
                Modifier.fillMaxWidth()
                    .graphicsLayer {
                        translationX = if (flying) curveX else dragX.value
                        translationY = if (flying) curveY else 0f
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                        this.alpha = alpha
                    }
                    .pointerInput(itemKey) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId = down.id
                            var totalDx = 0f
                            var totalDy = 0f
                            var horizontalLocked = false
                            var cancelled = false
                            val touchSlop = viewConfiguration.touchSlop

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) {
                                    if (horizontalLocked) change.consume()
                                    break
                                }
                                if (change.isConsumed || flying) {
                                    cancelled = true
                                    break
                                }
                                val delta = change.positionChange()
                                if (!horizontalLocked) {
                                    totalDx += delta.x
                                    totalDy += delta.y
                                    if (totalDx < -touchSlop || (abs(totalDy) > touchSlop && totalDx <= abs(totalDy) * 1.5f)) {
                                        cancelled = true
                                        break
                                    }
                                    if (totalDx > touchSlop && totalDx > abs(totalDy) * 1.5f) {
                                        horizontalLocked = true
                                        change.consume()
                                        scope.launch {
                                            dragX.snapTo(totalDx.coerceIn(0f, size.width * 0.65f))
                                        }
                                    }
                                } else if (delta != Offset.Zero) {
                                    change.consume()
                                    scope.launch {
                                        dragX.snapTo((dragX.value + delta.x).coerceIn(0f, size.width * 0.65f))
                                    }
                                }
                            }

                            if (horizontalLocked && !flying) {
                                if (!cancelled && dragX.value > size.width * 0.35f) {
                                    startFlyOut(dragX.value)
                                } else {
                                    scope.launch {
                                        dragX.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.62f, stiffness = 400f),
                                        )
                                    }
                                }
                            }
                        }
                    },
            ) {
                content { startFlyOut(0f) }
            }
        }
    }
}

/**
 * 2. 拖拽排序：长按条目拖动，其余条目实时弹簧动画让位，手指暂停动画同步停。
 */
@Composable
fun <T> DraggableSpringColumn(
    items: List<T>,
    keySelector: (T) -> Any,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, isDragging: Boolean) -> Unit,
) {
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember(items.size) {
        mutableStateListOf<Int>().apply { repeat(items.size) { add(180) } }
    }
    val spacingPx = with(LocalDensity.current) { 12.dp.toPx() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEachIndexed { index, item ->
            key(keySelector(item)) {
                val isDragging = index == draggingIndex
                val displacement = remember { Animatable(0f) }
                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.03f else 1f,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
                    label = "dragScale",
                )
                Box(
                    Modifier.fillMaxWidth()
                        .zIndex(if (isDragging) 10f else 0f)
                        .onGloballyPositioned { coordinates ->
                            if (index in itemHeights.indices) {
                                itemHeights[index] = coordinates.size.height
                            }
                        }
                        .offset {
                            // 被拖动项直接跟随手指（手指停则立即停），其余条目用弹簧平滑归位让位
                            IntOffset(
                                x = 0,
                                y = if (isDragging) dragOffsetY.roundToInt() else displacement.value.roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = if (isDragging) 16f else 0f
                        }
                        .pointerInput(items.size, index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffsetY = 0f
                                },
                                onDragEnd = {
                                    draggingIndex = -1
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val current = draggingIndex
                                    if (current < 0) return@detectDragGesturesAfterLongPress
                                    dragOffsetY += dragAmount.y
                                    val step = (itemHeights.getOrElse(current) { 180 } + spacingPx).coerceAtLeast(80f)
                                    if (dragOffsetY > step * 0.55f && current < items.lastIndex) {
                                        onMove(current, current + 1)
                                        draggingIndex = current + 1
                                        dragOffsetY -= step
                                    } else if (dragOffsetY < -step * 0.55f && current > 0) {
                                        onMove(current, current - 1)
                                        draggingIndex = current - 1
                                        dragOffsetY += step
                                    }
                                },
                            )
                        },
                ) {
                    itemContent(item, isDragging)
                }
            }
        }
    }
}
