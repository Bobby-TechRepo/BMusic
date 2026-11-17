package com.bsoft.compose.bmusic.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The knob turns through 270°, from bottom-left (0) clockwise to bottom-right (max). */
private const val START_ANGLE = 135f
private const val SWEEP = 270f

/** Ticks drawn around the dial; also how often a haptic "click" is felt while turning. */
private const val TICKS = 20

/**
 * A rotary control, like a knob on an amplifier. Put a finger on it and turn it in a circle:
 * clockwise raises the level, anti-clockwise lowers it. The level is printed in the middle.
 * Double-tap resets it to zero.
 *
 * @param value current level, 0..[max]
 * @param onValueChange called while turning (apply live)
 * @param onValueChangeFinished called when the finger lifts (save)
 */
@Composable
fun RotaryKnob(
    value: Int,
    max: Int,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    enabled: Boolean = true,
    valueText: (Int) -> String = { "${it * 100 / max.coerceAtLeast(1)}%" },
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    // Fractional position while dragging, so slow turns still add up.
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val fraction = (value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)
    // Follow the finger exactly; animate only changes that come from elsewhere (reset, restore).
    val shown by animateFloatAsState(targetValue = fraction, label = "knob", animationSpec = if (dragging) snapSpec else springSpec)

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val stateText = valueText(value)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .semantics {
                    contentDescription = label
                    stateDescription = stateText
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..max.toFloat(), steps = 0)
                    if (enabled) {
                        setProgress { target ->
                            val newValue = target.roundToInt().coerceIn(0, max)
                            currentOnChange(newValue)
                            currentOnFinished()
                            true
                        }
                    }
                }
                .then(
                    if (!enabled) Modifier else Modifier
                        .pointerInput(max) {
                            detectTapGestures(onDoubleTap = {
                                if (currentValue != 0) {
                                    currentOnChange(0)
                                    currentOnFinished()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            })
                        }
                        .pointerInput(max) {
                            var lastAngle = 0f
                            detectDragGestures(
                                onDragStart = { start ->
                                    dragging = true
                                    dragFraction = currentValue.toFloat() / max
                                    lastAngle = angleOf(start, this.size.width / 2f, this.size.height / 2f)
                                },
                                onDragEnd = { dragging = false; currentOnFinished() },
                                onDragCancel = { dragging = false; currentOnFinished() },
                            ) { change, _ ->
                                change.consume()
                                val angle = angleOf(change.position, this.size.width / 2f, this.size.height / 2f)
                                val delta = rotationDelta(lastAngle, angle)
                                lastAngle = angle
                                val before = (dragFraction * TICKS).toInt()
                                dragFraction = (dragFraction + delta / SWEEP).coerceIn(0f, 1f)
                                val newValue = (dragFraction * max).roundToInt()
                                if (newValue != currentValue) currentOnChange(newValue)
                                if ((dragFraction * TICKS).toInt() != before) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = this.size.minDimension * 0.07f
                val radius = this.size.minDimension / 2f - stroke
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val arcTopLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2, radius * 2)

                // Track and level arcs.
                drawArc(
                    color = Color.White.copy(alpha = if (enabled) 0.12f else 0.06f),
                    startAngle = START_ANGLE, sweepAngle = SWEEP, useCenter = false,
                    topLeft = arcTopLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
                if (shown > 0.001f) {
                    // Rotated so the gradient runs from the start of the arc to its end.
                    rotate(degrees = START_ANGLE, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to tertiary, SWEEP / 360f to primary, 0.9f to primary, 1f to tertiary, center = center
                            ),
                            startAngle = 0f, sweepAngle = SWEEP * shown, useCenter = false,
                            topLeft = arcTopLeft, size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                            alpha = if (enabled) 1f else 0.4f
                        )
                    }
                }

                // Tick marks just inside the arc.
                val tickOuter = radius - stroke * 1.1f
                val tickInner = tickOuter - stroke * 0.6f
                for (i in 0..TICKS) {
                    val a = Math.toRadians((START_ANGLE + SWEEP * i / TICKS).toDouble())
                    val lit = i.toFloat() / TICKS <= shown
                    drawLine(
                        color = Color.White.copy(alpha = if (lit && enabled) 0.55f else 0.18f),
                        start = Offset(center.x + tickInner * cos(a).toFloat(), center.y + tickInner * sin(a).toFloat()),
                        end = Offset(center.x + tickOuter * cos(a).toFloat(), center.y + tickOuter * sin(a).toFloat()),
                        strokeWidth = stroke * 0.18f,
                        cap = StrokeCap.Round
                    )
                }

                // The knob itself: a soft shadow, then a brushed-metal-like face.
                val knobRadius = tickInner - stroke * 0.7f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                        center = center + Offset(0f, knobRadius * 0.08f), radius = knobRadius * 1.15f
                    ),
                    radius = knobRadius * 1.15f, center = center + Offset(0f, knobRadius * 0.08f)
                )
                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF4A4D55), Color(0xFF1E2026)),
                        start = center - Offset(knobRadius, knobRadius), end = center + Offset(knobRadius, knobRadius)
                    ),
                    radius = knobRadius, center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f), radius = knobRadius, center = center,
                    style = Stroke(width = stroke * 0.15f)
                )

                // Pointer dot showing where the knob is turned to.
                val pointerAngle = Math.toRadians((START_ANGLE + SWEEP * shown).toDouble())
                val pointerDistance = knobRadius * 0.78f
                drawCircle(
                    color = if (enabled) primary else Color.White.copy(alpha = 0.3f),
                    radius = stroke * 0.32f,
                    center = Offset(
                        center.x + pointerDistance * cos(pointerAngle).toFloat(),
                        center.y + pointerDistance * sin(pointerAngle).toFloat()
                    )
                )
            }
            Text(
                text = stateText,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = (size.value * 0.17f).sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 0.85f else 0.4f),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

private val snapSpec: AnimationSpec<Float> = snap()
private val springSpec: AnimationSpec<Float> = spring()

/**
 * How far the finger turned between two angles (degrees), taking the short way round when it
 * crosses the 0°/360° line. Positive is clockwise.
 */
internal fun rotationDelta(previousDegrees: Float, currentDegrees: Float): Float {
    var delta = currentDegrees - previousDegrees
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

/** Angle of [point] around the centre, in degrees clockwise from 3 o'clock (0..360). */
private fun angleOf(point: Offset, cx: Float, cy: Float): Float {
    val degrees = Math.toDegrees(atan2((point.y - cy).toDouble(), (point.x - cx).toDouble())).toFloat()
    return if (degrees < 0) degrees + 360f else degrees
}
