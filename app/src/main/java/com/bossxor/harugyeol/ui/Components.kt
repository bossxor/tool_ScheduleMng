@file:OptIn(ExperimentalFoundationApi::class)

package com.bossxor.harugyeol.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.habit.DayState
import com.bossxor.harugyeol.habit.isDone

/** 부드러운 카드: 얇은 경계와 작은 그림자를 사용한다. */
@Composable
fun PopCard(
    modifier: Modifier = Modifier,
    fill: Color = LocalHaru.current.surface,
    shadow: Color = Color(0xFF19263D),
    radius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalHaru.current
    val shape = RoundedCornerShape(radius)
    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(if (c.dark) 0.dp else 3.dp, shape, clip = false, ambientColor = shadow.copy(alpha = 0.08f), spotColor = shadow.copy(alpha = 0.10f))
                .clip(shape)
                .background(fill)
                .border(1.dp, c.outline, shape)
                .then(
                    if (onClick != null || onLongClick != null)
                        Modifier.combinedClickable(
                            role = Role.Button,
                            onLongClick = onLongClick,
                            onClick = { onClick?.invoke() },
                        )
                    else Modifier
                ),
            content = content,
        )
    }
}

/** 작은 알약 버튼 */
@Composable
fun PillButton(
    text: String,
    fill: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LocalHaru.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, if (fill == c.surface) c.outline else fill.copy(alpha = 0.65f), shape)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HaruType.button, color = textColor)
    }
}

@Composable
fun HaruTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    multiline: Boolean = false,
) {
    val c = LocalHaru.current
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = numeric || !multiline,
        textStyle = HaruType.body.copy(color = c.text),
        cursorBrush = SolidColor(c.text),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .heightIn(min = if (multiline && !numeric) 112.dp else 56.dp)
                    .background(c.surface, shape)
                    .border(1.dp, c.outline, shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = HaruType.body, color = c.textSub)
                inner()
            }
        },
    )
}

/** 스티커 원 (docs/design-system.md 5.1). 그림이 준비되기 전에는 이름 첫 글자를 쓴다. */
@Composable
fun Sticker(name: String, p: HabitPalette, state: DayState, progress: Float, size: Dp = 56.dp, iconKey: String? = null) {
    val c = LocalHaru.current
    val letter = name.trim().take(1)
    val stampScale = remember { Animatable(1f) }
    val previousDone = remember { booleanArrayOf(state.isDone) }
    LaunchedEffect(state.isDone) {
        val becameDone = state.isDone && !previousDone[0]
        previousDone[0] = state.isDone
        if (becameDone) {
            stampScale.snapTo(0.86f)
            stampScale.animateTo(1.08f, tween(130))
            stampScale.animateTo(1f, tween(150))
        } else {
            stampScale.snapTo(1f)
        }
    }
    Box(Modifier.size(size).graphicsLayer { scaleX = stampScale.value; scaleY = stampScale.value }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            val r = this.size.minDimension / 2
            val center = Offset(r, r)
            when {
                state.isDone -> {
                    drawCircle(p.fill, r - stroke / 2)
                    drawCircle(c.outline, r - stroke / 2, style = Stroke(stroke))
                }
                state == DayState.PARTIAL -> {
                    drawCircle(p.tintBg(c), r - stroke / 2)
                    drawArc(
                        p.fill, -90f, 360f * progress.coerceIn(0f, 1f), false,
                        topLeft = Offset(stroke, stroke),
                        size = Size(this.size.width - 2 * stroke, this.size.height - 2 * stroke),
                        style = Stroke(stroke * 1.6f),
                    )
                    drawCircle(c.outline, r - stroke / 2, style = Stroke(stroke / 2))
                }
                state == DayState.SKIPPED -> {
                    drawCircle(c.stripeBg, r - stroke / 2)
                    drawCircle(c.outline, r - stroke / 2, style = Stroke(stroke))
                }
                state == DayState.EMPTY -> drawCircle(
                    c.dashed, r - stroke / 2,
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
                else -> drawCircle(c.textSub, r - stroke / 2, style = Stroke(1.dp.toPx()))
            }
            val disc = if (state.isDone || state == DayState.PARTIAL) 0.74f else 0.82f
            drawCircle(c.surface, r * disc, center)
        }
        if (iconKey != null && iconKey in HabitArt.drawables) HabitArtIcon(iconKey, Modifier.size(size * 0.55f)) else Text(
            letter,
            style = HaruType.headline,
            color = c.text.copy(alpha = if (state == DayState.REST || state == DayState.EMPTY) 0.45f else 1f),
        )
        if (state.isDone) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.32f)
                    .background(c.achievedText, CircleShape)
                    .border(1.dp, c.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("✓", style = HaruType.caption, color = if (c.dark) c.bg else Color.White) }
        }
    }
}
