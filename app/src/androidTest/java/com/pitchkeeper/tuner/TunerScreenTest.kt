package com.pitchkeeper.tuner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pitchkeeper.tuner.ui.TunerScreen
import com.pitchkeeper.tuner.ui.TunerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TunerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun idleScreenGuidesTheUserToSing() {
        compose.setContent {
            TunerTheme {
                TunerScreen(TunerUiState(hasPermission = true), {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("找一个安静的地方，唱一个长音").assertIsDisplayed()
        compose.onNodeWithTag("listenButton").assertIsEnabled()
        compose.onNodeWithTag("pitchReading").assertIsDisplayed()
    }

    @Test
    fun activeScreenExplainsSharpPitchInText() {
        compose.setContent {
            TunerTheme {
                TunerScreen(
                    TunerUiState(
                        status = ListeningStatus.LISTENING,
                        hasPermission = true,
                        frequency = 447.8f,
                        confidence = 0.97f,
                        rms = 0.05f,
                        displayNote = 69,
                        displayCents = 30f,
                    ),
                    {}, {}, {}, {}, {}, {},
                )
            }
        }
        compose.onNodeWithText("A4").assertIsDisplayed()
        compose.onNodeWithText("447.8 Hz").assertIsDisplayed()
        compose.onNodeWithText("稍高一点 · +30 音分").assertIsDisplayed()
        compose.onNodeWithText("停止测量").assertIsDisplayed()
    }

    @Test
    fun standardPitchChoicesCanBeOpenedAndChanged() {
        var selected = 440f
        compose.setContent {
            TunerTheme {
                TunerScreen(TunerUiState(hasPermission = true), {}, {}, {}, {}, { selected = it }, {})
            }
        }
        compose.onNodeWithTag("referenceButton").performClick()
        compose.onNodeWithText("442 Hz").performClick()
        assert(selected == 442f)
    }
}
