package cz.slovnifotbal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.slovnifotbal.data.GameStorage
import cz.slovnifotbal.data.DictionaryRepository
import cz.slovnifotbal.data.NoSound
import cz.slovnifotbal.data.SoundEffect
import cz.slovnifotbal.data.SoundEffects
import cz.slovnifotbal.domain.Difficulty
import cz.slovnifotbal.domain.EndReason
import cz.slovnifotbal.domain.GamePhase
import cz.slovnifotbal.domain.GameResult
import cz.slovnifotbal.domain.GameRules
import cz.slovnifotbal.domain.GameSettings
import cz.slovnifotbal.domain.GameStatistics
import cz.slovnifotbal.domain.Move
import cz.slovnifotbal.domain.Player
import cz.slovnifotbal.engine.ComputerMoveResult
import cz.slovnifotbal.engine.ComputerPlayer
import cz.slovnifotbal.engine.PlayerMoveResult
import cz.slovnifotbal.engine.WordDictionary
import cz.slovnifotbal.engine.WordFootballEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Potvrzovací dialogy herní obrazovky. */
enum class GameDialog { CONFIRM_NEW_GAME, CONFIRM_SURRENDER }

/** Stav herní obrazovky. */
data class GameUiState(
    val phase: GamePhase = GamePhase.LOADING,
    val loadingProgress: Float = 0f,
    val errorMessage: String? = null,
    val moves: List<Move> = emptyList(),
    val input: String = "",
    val inputError: String? = null,
    /** Hlavní požadované písmeno (např. „R“), null u prvního tahu. */
    val requiredLetter: String? = null,
    /** Všechny varianty požadovaného písmene (např. „I / Í“). */
    val requiredLetterVariants: String? = null,
    /** Kolik nepoužitých slov zbývá hráči na požadované písmeno. */
    val availableWords: Int = 0,
    val rules: GameRules = GameRules(),
    val statistics: GameStatistics = GameStatistics(),
    val endReason: EndReason? = null,
    val showResult: Boolean = false,
    /** Zvyšuje se při každém přijatém slovu hráče (vizuální potvrzení, skrytí klávesnice). */
    val acceptedPulse: Int = 0,
    /** Zvyšuje se při každém odmítnutém slovu (animace chyby). */
    val errorPulse: Int = 0,
    val hint: String? = null,
    val hintsLeft: Int = GameViewModel.MAX_HINTS,
    val dialog: GameDialog? = null,
    val dictionaryWordCount: Int = 0,
    val playableWordCount: Int = 0,
    /** Nastavení se od začátku hry změnilo – projeví se v nové hře. */
    val pendingRulesChange: Boolean = false,
) {
    val isPlayerTurn: Boolean get() = phase.acceptsPlayerInput
    val playerWordCount: Int get() = moves.count { it.player == Player.HUMAN }
    val lastWord: String? get() = moves.lastOrNull()?.word
}

/**
 * ViewModel herní obrazovky. Herní logika je v [WordFootballEngine]; ViewModel zajišťuje načtení
 * slovníku, běh počítače mimo hlavní vlákno, ochranu proti opakovanému odeslání, ukládání
 * rozehrané hry a statistik.
 */
class GameViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val store: GameStorage,
    private val sounds: SoundEffects = NoSound,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val random: Random = Random.Default,
    private val minThinkingMillis: (Difficulty) -> Long = ::defaultThinkingTime,
    private val computerTimeLimitMillis: (Difficulty) -> Long = ComputerPlayer::defaultTimeLimit,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /** Všechny operace s enginem probíhají pod zámkem – engine sám vláknově bezpečný není. */
    private val engineMutex = Mutex()
    private var dictionary: WordDictionary? = null
    private var engine: WordFootballEngine? = null
    private var computerJob: Job? = null
    private var loadJob: Job? = null
    private var submitting = false
    private var gameGeneration = 0
    private var recordedEngine: WordFootballEngine? = null
    private var currentSettings = GameSettings()

    init {
        viewModelScope.launch {
            store.statisticsFlow.collect { stats -> _uiState.update { it.copy(statistics = stats) } }
        }
        viewModelScope.launch {
            store.settingsFlow.collect { settings ->
                currentSettings = settings
                val rules = engine?.rules
                _uiState.update { it.copy(pendingRulesChange = rules != null && rules != settings.rules.sanitized()) }
            }
        }
        loadDictionary()
    }

    /** Načte slovník a obnoví rozehranou hru (nebo připraví novou). Slouží i pro „Zkusit znovu“. */
    fun loadDictionary() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = GamePhase.LOADING, loadingProgress = 0f, errorMessage = null) }
            try {
                val dict = dictionaryRepository.load { p -> _uiState.update { it.copy(loadingProgress = p) } }
                dictionary = dict
                currentSettings = store.settings()
                val saved = store.loadGame()?.takeIf { !it.phase.isFinished && it.moves.isNotEmpty() }
                val restored = saved?.let { snap ->
                    withContext(computeDispatcher) { WordFootballEngine.restore(dict, snap, ComputerPlayer(random), random) }
                }
                val e = restored ?: withContext(computeDispatcher) {
                    WordFootballEngine(dict, currentSettings.rules, ComputerPlayer(random), random)
                }.also { it.startedAtMillis = clock() }
                if (restored == null && saved != null) store.saveGame(null)
                engineMutex.withLock { engine = e }
                gameGeneration++
                _uiState.update { it.copy(dictionaryWordCount = dict.size, input = "", inputError = null) }
                publish(e)
                if (e.phase == GamePhase.COMPUTER_THINKING) startComputerMove(e)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        phase = GamePhase.ERROR,
                        errorMessage = t.message ?: "Slovník se nepodařilo načíst.",
                    )
                }
            }
        }
    }

    fun onInputChange(value: String) {
        val cleaned = value.replace("\n", "").replace("\r", "").take(GameRules.MAX_WORD_LENGTH + 10)
        _uiState.update { it.copy(input = cleaned, inputError = null) }
    }

    /** Odešle slovo hráče. Opakované rychlé odeslání nebo odeslání během tahu počítače se ignoruje. */
    fun submitWord() {
        val state = _uiState.value
        if (state.phase == GamePhase.COMPUTER_THINKING) {
            _uiState.update { it.copy(inputError = "Počítač přemýšlí.") }
            return
        }
        val e = engine
        if (e == null || submitting || !state.phase.acceptsPlayerInput) return
        submitting = true
        val input = state.input
        val generation = gameGeneration
        viewModelScope.launch {
            try {
                val result = engineMutex.withLock { e.playPlayerWord(input) }
                if (generation != gameGeneration) return@launch
                when (result) {
                    is PlayerMoveResult.Rejected -> {
                        sounds.play(SoundEffect.ERROR)
                        _uiState.update { it.copy(inputError = result.message, errorPulse = it.errorPulse + 1) }
                    }
                    is PlayerMoveResult.Accepted -> {
                        sounds.play(SoundEffect.WORD_ACCEPTED)
                        _uiState.update {
                            it.copy(input = "", inputError = null, hint = null, acceptedPulse = it.acceptedPulse + 1)
                        }
                        publish(e)
                        if (result.computerHasAnswer) {
                            persist(e)
                            startComputerMove(e)
                        } else {
                            finishGame(e)
                        }
                    }
                }
            } finally {
                submitting = false
            }
        }
    }

    private fun startComputerMove(e: WordFootballEngine) {
        computerJob?.cancel()
        val generation = gameGeneration
        computerJob = viewModelScope.launch {
            val difficulty = e.rules.difficulty
            val minDelay = launch { delay(minThinkingMillis(difficulty)) }
            val outcome = engineMutex.withLock {
                withContext(computeDispatcher) {
                    val job = coroutineContext[Job]
                    val r = e.playComputerMove(computerTimeLimitMillis(difficulty)) { job?.isActive == false }
                    r to e.toUiSnapshot()
                }
            }
            minDelay.join()
            val (result, snapshot) = outcome
            if (result == ComputerMoveResult.Cancelled || generation != gameGeneration) return@launch
            applySnapshot(snapshot)
            when (result) {
                is ComputerMoveResult.Played -> {
                    sounds.play(SoundEffect.COMPUTER_WORD)
                    if (result.playerHasAnswer) persist(e) else finishGame(e)
                }
                ComputerMoveResult.NoWord -> finishGame(e)
                else -> Unit
            }
        }
    }

    /** Nová hra; rozehranou hru je třeba nejdřív potvrdit (započítá se jako prohra). */
    fun requestNewGame() {
        val e = engine
        if (e != null && !e.phase.isFinished && e.moveCount > 0) {
            _uiState.update { it.copy(dialog = GameDialog.CONFIRM_NEW_GAME) }
        } else {
            startNewGame(countAsLoss = false)
        }
    }

    fun confirmNewGame() {
        _uiState.update { it.copy(dialog = null) }
        startNewGame(countAsLoss = true)
    }

    fun requestSurrender() {
        val e = engine ?: return
        if (e.phase.isFinished || e.moveCount == 0) return
        _uiState.update { it.copy(dialog = GameDialog.CONFIRM_SURRENDER) }
    }

    fun confirmSurrender() {
        _uiState.update { it.copy(dialog = null) }
        val e = engine ?: return
        computerJob?.cancel()
        viewModelScope.launch {
            val changed = engineMutex.withLock { e.surrender() }
            if (changed) finishGame(e)
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    /** Skryje výsledkovou obrazovku (hra zůstane ukončená, lze si prohlédnout průběh). */
    fun dismissResult() {
        _uiState.update { it.copy(showResult = false) }
    }

    /** Nápověda – ukáže jedno platné slovo (omezený počet za hru). */
    fun useHint() {
        val state = _uiState.value
        val e = engine ?: return
        if (state.hintsLeft <= 0 || !state.phase.acceptsPlayerInput) return
        viewModelScope.launch {
            val word = engineMutex.withLock { e.hint() }
            _uiState.update {
                it.copy(
                    hint = word,
                    hintsLeft = it.hintsLeft - 1,
                    inputError = if (word == null) "Na toto písmeno už žádné slovo nezbývá." else null,
                )
            }
        }
    }

    /** Vloží nápovědu do textového pole. */
    fun applyHint() {
        val hint = _uiState.value.hint ?: return
        _uiState.update { it.copy(input = hint, inputError = null) }
    }

    private fun startNewGame(countAsLoss: Boolean) {
        val dict = dictionary
        if (dict == null) {
            loadDictionary()
            return
        }
        gameGeneration++
        computerJob?.cancel()
        val old = engine
        val generation = gameGeneration
        viewModelScope.launch {
            val rules = currentSettings.rules
            val newEngine = withContext(computeDispatcher) {
                WordFootballEngine(dict, rules, ComputerPlayer(random), random)
            }.also { it.startedAtMillis = clock() }
            val abandoned = engineMutex.withLock {
                val abandon = countAsLoss && old != null && !old.phase.isFinished && old.moveCount > 0 && old.abandon()
                if (generation == gameGeneration) engine = newEngine
                abandon
            }
            if (abandoned && old != null) recordResult(old)
            if (generation != gameGeneration) return@launch
            store.saveGame(null)
            _uiState.update {
                it.copy(
                    input = "",
                    inputError = null,
                    hint = null,
                    hintsLeft = MAX_HINTS,
                    showResult = false,
                    dialog = null,
                )
            }
            publish(newEngine)
        }
    }

    private suspend fun finishGame(e: WordFootballEngine) {
        recordResult(e)
        sounds.play(if (e.phase == GamePhase.PLAYER_WON) SoundEffect.WIN else SoundEffect.LOSE)
        store.saveGame(null)
        publish(e)
    }

    private suspend fun recordResult(e: WordFootballEngine) {
        if (recordedEngine === e || !e.phase.isFinished) return
        recordedEngine = e
        val moves = e.moves
        if (moves.isEmpty()) return
        store.recordGame(
            GameResult(
                won = e.phase == GamePhase.PLAYER_WON,
                difficulty = e.rules.difficulty,
                totalWords = moves.size,
                playerWords = moves.count { it.player == Player.HUMAN },
            ),
        )
    }

    private suspend fun persist(e: WordFootballEngine) {
        val snapshot = engineMutex.withLock { e.snapshot() }
        store.saveGame(if (snapshot.phase.isFinished) null else snapshot)
    }

    private suspend fun publish(e: WordFootballEngine) {
        val snapshot = engineMutex.withLock { e.toUiSnapshot() }
        applySnapshot(snapshot)
    }

    /** Neměnný výřez stavu enginu (vytváří se pod zámkem). */
    private data class EngineSnapshot(
        val phase: GamePhase,
        val moves: List<Move>,
        val requiredLetter: String?,
        val requiredVariants: String?,
        val available: Int,
        val rules: GameRules,
        val endReason: EndReason?,
        val playable: Int,
    )

    private fun WordFootballEngine.toUiSnapshot() = EngineSnapshot(
        phase = phase,
        moves = moves,
        requiredLetter = requiredLetterPrimary(),
        requiredVariants = requiredLetterText(),
        available = availableWordsForPlayer(),
        rules = rules,
        endReason = endReason,
        playable = playableWordCount,
    )

    private fun applySnapshot(s: EngineSnapshot) {
        _uiState.update {
            it.copy(
                phase = s.phase,
                moves = s.moves,
                requiredLetter = s.requiredLetter,
                requiredLetterVariants = s.requiredVariants,
                availableWords = s.available,
                rules = s.rules,
                endReason = s.endReason,
                showResult = s.phase.isFinished,
                playableWordCount = s.playable,
                pendingRulesChange = currentSettings.rules.sanitized() != s.rules,
                loadingProgress = 1f,
                errorMessage = null,
            )
        }
    }

    override fun onCleared() {
        computerJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val MAX_HINTS = 3

        /** Minimální viditelná doba „přemýšlení“, aby hra působila přirozeně (nezdržuje hru). */
        fun defaultThinkingTime(difficulty: Difficulty): Long = when (difficulty) {
            Difficulty.EASY -> 450L + Random.nextLong(300)
            Difficulty.MEDIUM -> 550L + Random.nextLong(400)
            Difficulty.HARD -> 650L + Random.nextLong(400)
        }
    }
}
