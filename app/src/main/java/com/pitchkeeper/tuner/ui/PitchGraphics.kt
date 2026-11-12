package com.pitchkeeper.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TuningArc(
    cents: Float,
    hasPitch: Boolean,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val lowStyle = remember {
        TextStyle(color = SecondaryInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    val centerStyle = remember {
        TextStyle(color = SeaGlass, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    val accent = when {
        !hasPitch -> Line
        abs(cents) <= 10f -> SeaGlass
        else -> Coral
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .testTag("tuningArc")
            .semantics {
                contentDescription = if (hasPitch) {
                    when {
                        abs(cents) <= 10f -> "音准良好，偏差 ${cents.toInt()} 音分"
                        cents > 0 -> "偏高 ${cents.toInt()} 音分"
                        else -> "偏低 ${abs(cents).toInt()} 音分"
                    }
                } else "等待稳定的人声"
            },
    ) {
        val radius = size.width * 0.30f
        val origin = Offset(center.x, size.height - 2.dp.toPx())
        for (c in -50..50 step 10) {
            val angle = (c / 50f) * 58f
            val radians = (angle - 90f) * PI.toFloat() / 180f
            val inner = radius - if (c == 0) 16.dp.toPx() else 9.dp.toPx()
            val color = if (c == 0) SeaGlass else Line
            drawLine(
                color = color,
                start = origin + Offset(cos(radians) * inner, sin(radians) * inner),
                end = origin + Offset(cos(radians) * radius, sin(radians) * radius),
                strokeWidth = if (c == 0) 2.dp.toPx() else 1.dp.toPx(),
            )
        }
        val arcStart = -148f * PI.toFloat() / 180f
        val arcSweep = 116f * PI.toFloat() / 180f
        val arcPath = Path().apply {
            moveTo(
                origin.x + cos(arcStart) * radius,
                origin.y + sin(arcStart) * radius,
            )
            // The visual guide follows the same circular geometry as the needle.
            for (step in 1..32) {
                val angle = arcStart + arcSweep * step / 32f
                lineTo(
                    origin.x + cos(angle) * radius,
                    origin.y + sin(angle) * radius,
                )
            }
        }
        drawPath(arcPath, color = Line, style = Stroke(width = 1.dp.toPx()))
        val needleAngle = ((cents.coerceIn(-50f, 50f) / 50f) * 58f - 90f) * PI.toFloat() / 180f
        val tip = origin + Offset(cos(needleAngle) * radius * 0.9f, sin(needleAngle) * radius * 0.9f)
        drawLine(
            color = accent,
            start = origin,
            end = tip,
            strokeWidth = 3.dp.toPx(),
        )
        drawCircle(color = accent, radius = 5.dp.toPx(), center = origin)
        drawCircle(color = Fog, radius = 2.dp.toPx(), center = origin)

        val labelY = 2.dp.toPx()
        val low = textMeasurer.measure("偏低 −50", lowStyle)
        val middle = textMeasurer.measure("准", centerStyle)
        val high = textMeasurer.measure("+50 偏高", lowStyle)
        drawText(low, topLeft = Offset(0f, labelY))
        drawText(middle, topLeft = Offset(center.x - middle.size.width / 2, labelY))
        drawText(high, topLeft = Offset(size.width - high.size.width, labelY))
    }
}

@Composable
fun InputLevel(level: Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .height(24.dp)
            .fillMaxWidth()
            .semantics { contentDescription = "输入音量 ${(level * 100).toInt()}%" },
    ) {
        val y = center.y
        drawLine(Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
        val targetX = size.width * level.coerceIn(0f, 1f)
        drawLine(
            if (level > 0.85f) Coral else SeaGlass,
            Offset(0f, y),
            Offset(targetX, y),
            strokeWidth = 3.dp.toPx(),
        )
        drawCircle(
            color = Paper,
            radius = 5.dp.toPx(),
            center = Offset(targetX, y),
        )
        drawCircle(
            color = if (level > 0.85f) Coral else SeaGlass,
            radius = 5.dp.toPx(),
            center = Offset(targetX, y),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
fun PitchHistory(
    points: List<Float?>,
    isListening: Boolean,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val guideStyle = remember { TextStyle(color = SecondaryInk, fontSize = 11.sp) }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(116.dp)
            .testTag("pitchHistory")
            .semantics { contentDescription = "最近音准变化，中间横线代表唱准" },
    ) {
        val top = 16.dp.toPx()
        val bottom = size.height - 16.dp.toPx()
        val center = (top + bottom) / 2f
        for (value in listOf(-50, 0, 50)) {
            val y = center - value / 50f * (bottom - top) / 2f
            drawLine(
                color = if (value == 0) SeaGlassLight else Line,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (value == 0) 1.2f.dp.toPx() else 0.7f.dp.toPx(),
            )
            if (value != 0) {
                drawText(
                    textMeasurer.measure(if (value > 0) "+50" else "−50", guideStyle),
                    topLeft = Offset(5.dp.toPx(), y - 14.dp.toPx()),
                )
            }
        }
        if (points.isEmpty()) {
            val message = textMeasurer.measure(
                if (isListening) "开始唱后，这里会留下你的音准轨迹" else "你的音准轨迹会显示在这里",
                guideStyle,
            )
            drawText(message, topLeft = Offset(size.width / 2f - message.size.width / 2f, center + 12.dp.toPx()))
            return@Canvas
        }
        val step = size.width / 99f
        var previous: Offset? = null
        points.forEachIndexed { index, value ->
            val current = value?.let {
                Offset(index * step, center - it / 50f * (bottom - top) / 2f)
            }
            val prior = previous
            if (current != null && prior != null && (current.x - prior.x) <= step * 1.5f) {
                drawLine(SeaGlass, prior, current, strokeWidth = 2.dp.toPx())
            }
            if (current != null && (index == points.lastIndex || prior == null)) {
                drawCircle(SeaGlass, radius = 2.5.dp.toPx(), center = current)
            }
            previous = current
        }
    }
}
