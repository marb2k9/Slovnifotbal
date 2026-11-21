package cz.slovnifotbal.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Výpočet statistik (bez úložiště). */
class GameStatisticsTest {

    @Test
    fun recordsWinsLossesStreaksAndRecords() {
        var s = GameStatistics()
        s = s.record(GameResult(won = true, difficulty = Difficulty.EASY, totalWords = 9, playerWords = 5))
        s = s.record(GameResult(won = true, difficulty = Difficulty.MEDIUM, totalWords = 14, playerWords = 7))
        s = s.record(GameResult(won = false, difficulty = Difficulty.HARD, totalWords = 6, playerWords = 3))
        s = s.record(GameResult(won = true, difficulty = Difficulty.HARD, totalWords = 21, playerWords = 11))
        assertEquals(4, s.gamesPlayed)
        assertEquals(3, s.wins)
        assertEquals(1, s.losses)
        assertEquals(75, s.winRatePercent)
        assertEquals(21, s.longestChain)
        assertEquals(11, s.mostPlayerWordsInGame)
        assertEquals(50, s.totalWords)
        assertEquals(26, s.totalPlayerWords)
        assertEquals(1, s.currentWinStreak)
        assertEquals(2, s.bestWinStreak)
        assertEquals(1, s.gamesOn(Difficulty.EASY))
        assertEquals(1, s.gamesOn(Difficulty.MEDIUM))
        assertEquals(2, s.gamesOn(Difficulty.HARD))
        assertEquals(1, s.winsOn(Difficulty.HARD))
    }

    @Test
    fun emptyStatisticsHaveZeroWinRate() {
        assertEquals(0, GameStatistics().winRatePercent)
    }
}
