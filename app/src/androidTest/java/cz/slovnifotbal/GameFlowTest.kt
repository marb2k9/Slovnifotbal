package cz.slovnifotbal

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.and
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumentační testy na skutečném zařízení/emulátoru: instalace, spuštění, načtení slovníku,
 * odehrání tahu, nová hra, výhra a obrazovky. Každý test běží s čistými daty (Android Test
 * Orchestrator, clearPackageData). Snímky obrazovky se ukládají jako doplňkový výstup testů.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GameFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val enabledInput: SemanticsMatcher = hasTestTag("word_input") and isEnabled()

    /** Počká, až je hra připravená a pole pro slovo přijímá vstup. */
    private fun waitForPlayerTurn(timeoutMillis: Long = 60_000) {
        rule.waitUntilExactlyOneExists(enabledInput, timeoutMillis = timeoutMillis)
    }

    private fun play(word: String) {
        waitForPlayerTurn(30_000)
        rule.onNodeWithTag("word_input").performTextInput(word)
        rule.onNodeWithTag("send_button").performClick()
    }

    /** Uloží snímek obrazovky (jen pro kontrolu vzhledu; chyba při ukládání test neshodí). */
    private fun screenshot(name: String) {
        runCatching {
            rule.waitForIdle()
            val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
                ?: InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir("screenshots")
                ?: return
            dir.mkdirs()
            val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun appStartsLoadsDictionaryAndComputerAnswers() {
        waitForPlayerTurn()
        screenshot("01_start")
        play("kolo")
        // počítač odpoví slovem na O (bublina 2. tahu) a hráč je znovu na tahu
        rule.waitUntilExactlyOneExists(hasTestTag("move_2"), timeoutMillis = 30_000)
        rule.onNodeWithTag("move_1").assertTextContains("kolo", substring = true, ignoreCase = true)
        waitForPlayerTurn(30_000)
        screenshot("02_after_first_move")
    }

    @Test
    fun invalidWordShowsCzechError() {
        play("xyzqw")
        rule.waitUntilExactlyOneExists(hasTestTag("input_error"), timeoutMillis = 10_000)
        rule.onNodeWithTag("input_error").assertTextContains("Toto slovo není v českém slovníku.")
        screenshot("03_error")
    }

    @Test
    fun settingsAndStatisticsScreensOpen() {
        waitForPlayerTurn()
        rule.onNodeWithTag("settings_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("settings_screen"), timeoutMillis = 10_000)
        rule.onNodeWithTag("difficulty_hard").performClick()
        screenshot("04_settings")
        rule.onNodeWithTag("back_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("word_input"), timeoutMillis = 10_000)
        rule.onNodeWithTag("statistics_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("statistics_screen"), timeoutMillis = 10_000)
        rule.onNodeWithTag("stat_games").assertIsDisplayed()
        screenshot("05_statistics")
    }

    /** Nová hra během rozehrané hry: potvrzení, prázdné hřiště a tah se odehraje už do nové hry. */
    @Test
    fun newGameDuringGameStartsCleanGame() {
        play("kolo")
        rule.waitUntilExactlyOneExists(hasTestTag("move_2"), timeoutMillis = 30_000)
        waitForPlayerTurn(30_000)
        rule.onNodeWithTag("new_game_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("confirm_new_game"), timeoutMillis = 10_000)
        screenshot("06_confirm_new_game")
        rule.onNodeWithTag("confirm_new_game").performClick()
        waitForPlayerTurn(30_000)
        rule.waitUntilDoesNotExist(hasTestTag("move_1"), timeoutMillis = 10_000)
        play("auto")
        rule.waitUntilExactlyOneExists(hasTestTag("move_2"), timeoutMillis = 30_000)
        rule.onNodeWithTag("move_1").assertTextContains("auto", substring = true, ignoreCase = true)
        // prohraná (opuštěná) hra je ve statistikách
        rule.onNodeWithTag("statistics_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("statistics_screen"), timeoutMillis = 10_000)
        rule.onNodeWithTag("stat_losses").assertTextContains("1", substring = true)
    }

    /** Při přesném navazování na diakritiku nemá počítač na „země“ (končí na Ě) odpověď -> výhra. */
    @Test
    fun strictDiacriticsWinShowsResultScreen() {
        waitForPlayerTurn()
        rule.onNodeWithTag("settings_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("settings_screen"), timeoutMillis = 10_000)
        rule.onNodeWithTag("link_strict").performScrollTo().performClick()
        rule.onNodeWithTag("theme_dark").performScrollTo().performClick()
        rule.onNodeWithTag("back_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("new_game_button"), timeoutMillis = 10_000)
        rule.onNodeWithTag("new_game_button").performClick() // nová hra s novými pravidly
        play("země")
        rule.waitUntilExactlyOneExists(hasTestTag("result_title"), timeoutMillis = 15_000)
        rule.onNodeWithTag("result_title").assertTextContains("VYHRÁL JSI!")
        screenshot("07_result_dark")
        rule.onNodeWithTag("play_again_button").performClick()
        waitForPlayerTurn(30_000)
        rule.waitUntilDoesNotExist(hasTestTag("move_1"), timeoutMillis = 10_000)
        screenshot("08_new_game_dark")
    }
}
