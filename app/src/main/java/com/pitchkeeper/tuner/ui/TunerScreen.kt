package com.pitchkeeper.tuner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitchkeeper.tuner.ListeningStatus
import com.pitchkeeper.tuner.TunerUiState
import com.pitchkeeper.tuner.pitch.PitchNotation
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun TunerScreen(
    state: TunerUiState,
    onToggle: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearHistory: () -> Unit,
    onConcertAChange: (Float) -> Unit,
    onDismissError: () -> Unit,
) {
    var showReference by remember { mutableStateOf(false) }
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            kotlinx.coroutines.delay(6_000)
            onDismissError()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // One quiet recording-status line, not a decorative pulsing element.
                if (state.isListening) {
                    drawLine(
                        color = SeaGlass,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "音准",
                    fontSize = 29.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = Ink,
                )
                Text(
                    "听见自己的音高",
                    fontSize = 13.sp,
                    color = SecondaryInk,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            TextButton(
                onClick = { showReference = !showReference },
                modifier = Modifier.testTag("referenceButton"),
            ) {
                Text(
                    String.format(Locale.CHINA, "标准音 %.0f Hz", state.concertA),
                    color = Ink,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (showReference) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(440f, 442f, 415f).forEach { frequency ->
                    OutlinedButton(
                        onClick = { onConcertAChange(frequency) },
                        border = BorderStroke(
                            1.dp,
                            if (state.concertA == frequency) SeaGlass else Line,
                        ),
                    ) {
                        Text("${frequency.toInt()} Hz", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        TuningArc(
            cents = state.displayCents,
            hasPitch = state.frequency != null,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("pitchReading"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                PitchNotation.nameForMidi(state.displayNote),
                fontSize = 104.sp,
                lineHeight = 108.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-3).sp,
                color = Ink,
            )
            Text(
                PitchNotation.solfegeForMidi(state.displayNote),
                fontSize = 15.sp,
                color = SecondaryInk,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                state.frequency?.let { String.format(Locale.CHINA, "%.1f Hz", it) } ?: "— Hz",
                fontSize = 19.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                color = Ink,
                modifier = Modifier.padding(top = 15.dp),
            )
        }
        Spacer(Modifier.height(19.dp))
        Text(
            measurementPrompt(state),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (state.frequency != null && abs(state.displayCents) > 10f) Coral else Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(24.dp).semantics {
                liveRegion = LiveRegionMode.Polite
            },
        )

        Spacer(Modifier.height(23.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("输入音量", fontSize = 12.sp, color = SecondaryInk)
            Spacer(Modifier.width(16.dp))
            InputLevel(level = (sqrt(state.rms.toDouble()) * 2.5).toFloat(), modifier = Modifier.weight(1f))
            Text(
                if (state.rms > 0.115f) "稍轻一些" else if (state.rms > 0.008f) "清晰" else "安静",
                fontSize = 12.sp,
                color = SecondaryInk,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(15.dp))
        HorizontalDivider(color = Line, thickness = 0.7.dp)
        Row(
            Modifier.fillMaxWidth().padding(top = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("最近音准", fontSize = 13.sp, color = SecondaryInk)
            if (state.history.isNotEmpty()) {
                TextButton(onClick = onClearHistory, modifier = Modifier.testTag("clearHistory")) {
                    Text("清空", fontSize = 12.sp)
                }
            }
        }
        PitchHistory(points = state.history, isListening = state.isListening)
        Text(
            if (state.confidence > 0f) {
                String.format(Locale.CHINA, "检测可信度 %.0f%%", state.confidence * 100)
            } else "每次只唱一个音，结果会更稳定",
            fontSize = 11.sp,
            color = SecondaryInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        val requestPermission = !state.hasPermission
        Button(
            onClick = if (requestPermission) onRequestPermission else onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("listenButton"),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isListening) Ink else SeaGlass,
                contentColor = Paper,
            ),
        ) {
            Icon(
                imageVector = if (state.isListening) Icons.Rounded.Stop else Icons.Outlined.Mic,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    requestPermission -> "允许麦克风，开始试唱"
                    state.status == ListeningStatus.STARTING -> "正在准备麦克风…"
                    state.isListening -> "停止测量"
                    else -> "开始试唱"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (state.permissionDenied && !state.hasPermission) {
            TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("已拒绝？前往系统设置开启麦克风", fontSize = 12.sp)
            }
        } else {
            Text(
                if (state.isListening) "保持约 20–30 厘米距离，唱“啊——”" else "仅在本机分析，不保存或上传录音",
                fontSize = 12.sp,
                color = SecondaryInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
            )
        }
        if (state.errorMessage != null) {
            Text(
                state.errorMessage,
                color = Coral,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
    }
}

private fun measurementPrompt(state: TunerUiState): String = when {
    !state.isListening -> "找一个安静的地方，唱一个长音"
    state.status == ListeningStatus.STARTING -> "正在准备麦克风"
    state.rms < 0.004f && state.frequency == null -> "唱“啊——”，保持 2 秒"
    state.rms > 0.115f -> "离麦克风远一点，或唱轻一些"
    state.frequency == null -> "再清晰一些，尽量只唱一个音"
    abs(state.displayCents) <= 5f -> "很准，保持这个感觉"
    abs(state.displayCents) <= 10f -> "接近了，继续保持"
    state.displayCents > 0f -> "稍高一点 · +${state.displayCents.toInt()} 音分"
    else -> "稍低一点 · ${state.displayCents.toInt()} 音分"
}

@Preview(name = "初始状态", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun TunerIdlePreview() {
    TunerTheme {
        TunerScreen(
            TunerUiState(hasPermission = true),
            {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "A4 略偏高", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun TunerActivePreview() {
    TunerTheme {
        TunerScreen(
            TunerUiState(
                status = ListeningStatus.LISTENING,
                hasPermission = true,
                frequency = 447.8f,
                rawFrequency = 448.1f,
                confidence = 0.97f,
                rms = 0.05f,
                displayNote = 69,
                displayCents = 30f,
                history = listOf(null, 18f, 21f, 25f, 30f),
            ),
            {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "紧凑屏幕", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun TunerCompactPreview() {
    TunerTheme {
        TunerScreen(
            TunerUiState(hasPermission = true),
            {}, {}, {}, {}, {}, {},
        )
    }
}
