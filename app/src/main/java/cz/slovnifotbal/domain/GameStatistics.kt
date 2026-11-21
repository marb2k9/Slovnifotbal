package cz.slovnifotbal.domain

import kotlin.math.roundToInt

/** Výsledek jedné dokončené hry pro statistiky. */
data class GameResult(
    val won: Boolean,
    val difficulty: Difficulty,
    val totalWords: Int,
    val playerWords: Int,
)

/** Souhrnné statistiky hráče. */
data class GameStatistics(
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    /** Nejdelší řetězec slov (hráč + počítač) v jedné hře. */
    val longestChain: Int = 0,
    /** Nejvíce slov hráče v jedné hře. */
    val mostPlayerWordsInGame: Int = 0,
    /** Počet všech použitých slov ve všech hrách (hráč + počítač). */
    val totalWords: Int = 0,
    /** Počet slov zadaných hráčem ve všech hrách. */
    val totalPlayerWords: Int = 0,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val gamesByDifficulty: Map<Difficulty, Int> = emptyMap(),
    val winsByDifficulty: Map<Difficulty, Int> = emptyMap(),
) {
    /** Úspěšnost v procentech (zaokrouhleno). */
    val winRatePercent: Int
        get() = if (gamesPlayed == 0) 0 else (wins * 100.0 / gamesPlayed).roundToInt()

    fun gamesOn(d: Difficulty): Int = gamesByDifficulty[d] ?: 0

    fun winsOn(d: Difficulty): Int = winsByDifficulty[d] ?: 0

    /** Vrátí statistiky po započtení další hry. */
    fun record(result: GameResult): GameStatistics {
        val streak = if (result.won) currentWinStreak + 1 else 0
        return copy(
            gamesPlayed = gamesPlayed + 1,
            wins = wins + if (result.won) 1 else 0,
            losses = losses + if (result.won) 0 else 1,
            longestChain = maxOf(longestChain, result.totalWords),
            mostPlayerWordsInGame = maxOf(mostPlayerWordsInGame, result.playerWords),
            totalWords = totalWords + result.totalWords,
            totalPlayerWords = totalPlayerWords + result.playerWords,
            currentWinStreak = streak,
            bestWinStreak = maxOf(bestWinStreak, streak),
            gamesByDifficulty = gamesByDifficulty + (result.difficulty to gamesOn(result.difficulty) + 1),
            winsByDifficulty = winsByDifficulty + (result.difficulty to winsOn(result.difficulty) + if (result.won) 1 else 0),
        )
    }
}
