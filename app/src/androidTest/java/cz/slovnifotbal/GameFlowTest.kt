package cz.slovnifotbal

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

/**
 * Instrumentační testy na skutečném zařízení/emulátoru: instalace, spuštění, načtení slovníku,
 * odehrání tahu, nová hra, výhra a obrazovky. Každý test běží s čistými daty (Android Test
 * Orchestrator, clearPackageData). Snímky obrazovky se ukládají jako doplňkový výstup testů.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GameFlowTest {

    @get:Rule(order = 0)
    val rule = createAndroidComposeRule<MainActivity>()

    /** Při selhání uloží snímek obrazovky (aktivita je v tu chvíli ještě zobrazená). */
    @get:Rule(order = 1)
    val failureScreenshot = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description) {
            screenshot("FAIL_${description.methodName}", waitForIdle = false)
        }
    }

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

    /** Ověří slovo v bublině tahu (historie se k bublině nejdřív posune, aby byla složená). */
    private fun assertMove(index: Int, word: String) {
        rule.onNodeWithTag("history_list").performScrollToNode(hasTestTag("move_$index"))
        rule.onNodeWithTag("move_$index").assertTextContains(word, substring = true, ignoreCase = true)
    }

    /**
     * Uloží snímek celé obrazovky (včetně klávesnice a systémových lišt) příkazem `screencap`
     * do /data/local/tmp/slovnifotbal-shots – mazání dat aplikace mezi testy ho nesmaže, CI ho stáhne.
     * Slouží jen pro kontrolu vzhledu; chyba při ukládání test neshodí.
     */
    private fun screenshot(name: String, waitForIdle: Boolean = true) {
        runCatching {
            if (waitForIdle) rule.waitForIdle()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            for (cmd in listOf("mkdir -p $SHOT_DIR", "screencap -p $SHOT_DIR/$name.png")) {
                // výstup je potřeba dočíst – tím se zároveň počká na dokončení příkazu
                ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(cmd)).use { it.readBytes() }
            }
        }
    }

    @Test
    fun appStartsLoadsDictionaryAndComputerAnswers() {
        waitForPlayerTurn()
        screenshot("01_start")
        play("kolo")
        // počítač odpoví slovem na O (bublina 2. tahu) a hráč je znovu na tahu
        rule.waitUntilExactlyOneExists(hasTestTag("move_2"), timeoutMillis = 30_000)
        assertMove(1, "kolo")
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
        assertMove(1, "auto")
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
        rule.waitUntilExactlyOneExists(hasTestTag("link_strict") and isSelected(), timeoutMillis = 10_000)
        rule.onNodeWithTag("theme_dark").performScrollTo().performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("theme_dark") and isSelected(), timeoutMillis = 10_000)
        screenshot("07_settings_strict_dark")
        rule.onNodeWithTag("back_button").performClick()
        // rozehraná hra má ještě stará pravidla -> upozornění; nová hra ho odstraní
        rule.waitUntilExactlyOneExists(hasTestTag("rules_changed_banner"), timeoutMillis = 10_000)
        rule.onNodeWithTag("new_game_button").performClick() // nová hra s novými pravidly
        rule.waitUntilDoesNotExist(hasTestTag("rules_changed_banner"), timeoutMillis = 10_000)
        play("země")
        rule.waitUntilExactlyOneExists(hasTestTag("result_title"), timeoutMillis = 15_000)
        rule.onNodeWithTag("result_title").assertTextContains("VYHRÁL JSI!")
        screenshot("08_result_dark")
        rule.onNodeWithTag("play_again_button").performClick()
        waitForPlayerTurn(30_000)
        rule.waitUntilDoesNotExist(hasTestTag("move_1"), timeoutMillis = 10_000)
        screenshot("09_new_game_dark")
    }

    private companion object {
        const val SHOT_DIR = "/data/local/tmp/slovnifotbal-shots"
    }
}
