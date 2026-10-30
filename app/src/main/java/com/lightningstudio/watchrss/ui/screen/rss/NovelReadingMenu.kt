package com.lightningstudio.watchrss.ui.screen.rss

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import com.lightningstudio.watchrss.ui.util.showAppToast

/** Novel-only reader controls; the bubble remains inside the app and uses no platform blur. */
@Composable
internal fun NovelReadingMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    scrolling: Boolean,
    pageTurning: Boolean,
    pageMode: Boolean,
    backgroundColor: Color,
    surfaceColor: Color,
    textColor: Color,
    accentColor: Color,
    readAloudEnabled: Boolean,
    onReadAloud: () -> Unit,
    onModeSelected: (Boolean) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var quiet by remember { mutableStateOf(false) }
    val reveal = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val bottomInset = with(density) { 14.dp.toPx() }
    val bubbleRadius = with(density) { 16.dp.toPx() }

    LaunchedEffect(scrolling, pageTurning, expanded) {
        if (expanded) { visible = true; quiet = false; return@LaunchedEffect }
        if (scrolling || pageTurning) { visible = false; quiet = false; return@LaunchedEffect }
        delay(350)
        visible = true
        quiet = false
        delay(1500)
        quiet = true
    }
    LaunchedEffect(expanded) {
        reveal.animateTo(if (expanded) 1f else 0f, tween(if (expanded) 260 else 220))
    }
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    val bubbleColor by animateColorAsState(
        targetValue = if (quiet) lerp(backgroundColor, surfaceColor, 0.48f) else surfaceColor,
        animationSpec = tween(400), label = "readerBubbleMaterial"
    )
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (quiet) 0.22f else 1f,
        animationSpec = tween(400), label = "readerBubbleIdleAlpha"
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val revealCenter = Offset(widthPx / 2f, heightPx - bottomInset - bubbleRadius)
        val maxRadius = maxOf(hypot(revealCenter.x, revealCenter.y), hypot(widthPx - revealCenter.x, revealCenter.y))
        if (reveal.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * reveal.value)).clickable { onExpandedChange(false) })
            val shape = remember(reveal.value, widthPx, heightPx) {
                GenericShape { _, _ ->
                    val radius = bubbleRadius + (maxRadius - bubbleRadius) * reveal.value
                    addOval(Rect(revealCenter.x - radius, revealCenter.y - radius, revealCenter.x + radius, revealCenter.y + radius))
                }
            }
            Box(Modifier.fillMaxSize().clip(shape).background(backgroundColor).clickable { onExpandedChange(false) }) {
                if (reveal.value > 0.65f) {
                    Column(Modifier.align(Alignment.Center).fillMaxWidth(0.76f)
                        .graphicsLayer { alpha = ((reveal.value - 0.65f) / 0.35f).coerceIn(0f, 1f) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("阅读方式", color = textColor, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(surfaceColor)
                                .border(1.dp, textColor.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                                .padding(3.dp)
                                .semantics { contentDescription = "阅读方式" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(false to "纵向滚动", true to "点击翻页").forEach { (mode, label) ->
                                val selected = mode == pageMode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(if (selected) accentColor.copy(alpha = 0.14f) else Color.Transparent)
                                        .then(
                                            if (selected) Modifier.border(1.dp, accentColor, RoundedCornerShape(17.dp))
                                            else Modifier
                                        )
                                        .semantics { this.selected = selected }
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            role = Role.RadioButton
                                        ) {
                                            scope.launch {
                                                onExpandedChange(false)
                                                delay(220)
                                                onModeSelected(mode)
                                                if (mode && !pageMode) {
                                                    showAppToast(context, "轻点左侧上一页，轻点右侧下一页")
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = if (selected) accentColor else textColor,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(surfaceColor)
                            .border(1.dp, textColor.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                            .clickable(enabled = readAloudEnabled, role = Role.Button) {
                                scope.launch { onExpandedChange(false); delay(220); onReadAloud() }
                            }.padding(horizontal = 12.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                            Text("大声朗读", color = if (readAloudEnabled) textColor else textColor.copy(alpha = 0.4f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = visible && !expanded && reveal.value == 0f,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            enter = fadeIn(tween(140)), exit = fadeOut(tween(100))) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { quiet = false; onExpandedChange(true) }
                .semantics { contentDescription = "阅读方式菜单" }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(32.dp).graphicsLayer { alpha = bubbleAlpha }) {
                    drawCircle(bubbleColor)
                    drawCircle(textColor.copy(alpha = if (quiet) 0.18f else 0.35f), style = Stroke(width = 1.dp.toPx()))
                    val stroke = 2.dp.toPx()
                    listOf(-5.dp.toPx(), 0f, 5.dp.toPx()).forEach { y ->
                        drawLine(textColor, Offset(center.x - 7.dp.toPx(), center.y + y), Offset(center.x + 7.dp.toPx(), center.y + y), strokeWidth = stroke)
                    }
                }
            }
        }
    }
}
