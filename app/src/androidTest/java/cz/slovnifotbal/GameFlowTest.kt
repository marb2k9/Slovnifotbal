package cz.slovnifotbal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentační testy na skutečném zařízení/emulátoru: instalace, spuštění, načtení slovníku
 * a odehrání tahu. Každý test běží s čistými daty (Android Test Orchestrator, clearPackageData).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GameFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForGame() {
        rule.waitUntilExactlyOneExists(hasTestTag("word_input"), timeoutMillis = 60_000)
    }

    @Test
    fun appStartsLoadsDictionaryAndComputerAnswers() {
        waitForGame()
        rule.onNodeWithTag("word_input").performTextInput("kolo")
        rule.onNodeWithTag("send_button").performClick()
        // počítač odpoví slovem na O (bublina s číslem 2. tahu)
        rule.waitUntilAtLeastOneExists(hasText("POČÍTAČ  ·  2.", substring = true), timeoutMillis = 30_000)
        rule.waitUntilExactlyOneExists(hasTestTag("word_input"), timeoutMillis = 30_000)
    }

    @Test
    fun invalidWordShowsCzechError() {
        waitForGame()
        rule.onNodeWithTag("word_input").performTextInput("xyzqw")
        rule.onNodeWithTag("send_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("input_error"), timeoutMillis = 10_000)
        rule.onNodeWithTag("input_error").assertTextContains("Toto slovo není v českém slovníku.")
    }

    @Test
    fun settingsAndStatisticsScreensOpen() {
        waitForGame()
        rule.onNodeWithTag("settings_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("settings_screen"), timeoutMillis = 10_000)
        rule.onNodeWithTag("difficulty_hard").performClick()
        rule.onNodeWithTag("back_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("word_input"), timeoutMillis = 10_000)
        rule.onNodeWithTag("statistics_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("statistics_screen"), timeoutMillis = 10_000)
        rule.onNodeWithTag("stat_games").assertIsDisplayed()
    }
}
