package cz.slovnifotbal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.slovnifotbal.domain.GamePhase
import cz.slovnifotbal.ui.components.AppIcons
import cz.slovnifotbal.ui.components.LetterBall
import cz.slovnifotbal.ui.components.MoveBubble
import cz.slovnifotbal.ui.components.PitchLines
import cz.slovnifotbal.ui.components.ScoreBoard
import cz.slovnifotbal.ui.components.SoccerBall
import cz.slovnifotbal.ui.components.ThinkingBubble
import cz.slovnifotbal.ui.theme.Baloo
import cz.slovnifotbal.ui.theme.GameTheme
import cz.slovnifotbal.viewmodel.GameDialog
import cz.slovnifotbal.viewmodel.GameUiState
import cz.slovnifotbal.viewmodel.GameViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val CS: Locale = Locale.forLanguageTag("cs-CZ")

/** Akce herní obrazovky (oddělené od ViewModelu kvůli náhledům a testům). */
data class GameActions(
    val onInputChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onNewGame: () -> Unit,
    val onConfirmNewGame: () -> Unit,
    val onSurrender: () -> Unit,
    val onConfirmSurrender: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onHint: () -> Unit,
    val onApplyHint: () -> Unit,
    val onDismissResult: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenStatistics: () -> Unit,
)

@Composable
fun GameScreen(viewModel: GameViewModel, onOpenSettings: () -> Unit, onOpenStatistics: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        GameActions(
            onInputChange = viewModel::onInputChange,
            onSubmit = viewModel::submitWord,
            onNewGame = viewModel::requestNewGame,
            onConfirmNewGame = viewModel::confirmNewGame,
            onSurrender = viewModel::requestSurrender,
            onConfirmSurrender = viewModel::confirmSurrender,
            onDismissDialog = viewModel::dismissDialog,
            onHint = viewModel::useHint,
            onApplyHint = viewModel::applyHint,
            onDismissResult = viewModel::dismissResult,
            onRetry = viewModel::loadDictionary,
            onOpenSettings = onOpenSettings,
            onOpenStatistics = onOpenStatistics,
        )
    }
    GameScreenContent(state, actions)
}

@Composable
fun GameScreenContent(state: GameUiState, actions: GameActions) {
    when (state.phase) {
        GamePhase.LOADING -> LoadingContent(state.loadingProgress)
        GamePhase.ERROR -> ErrorContent(state.errorMessage, actions.onRetry)
        else -> GameContent(state, actions)
    }
    when (state.dialog) {
        GameDialog.CONFIRM_NEW_GAME -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text("Začít novou hru?") },
            text = { Text("Rozehraná hra se ukončí a započítá se jako prohra.") },
            confirmButton = {
                Button(onClick = actions.onConfirmNewGame, modifier = Modifier.testTag("confirm_new_game")) { Text("NOVÁ HRA") }
            },
            dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text("Hrát dál") } },
        )
        GameDialog.CONFIRM_SURRENDER -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text("Opravdu se vzdáváš?") },
            text = { Text("Hra skončí a započítá se jako prohra.") },
            confirmButton = {
                Button(onClick = actions.onConfirmSurrender, modifier = Modifier.testTag("confirm_surrender")) { Text("Vzdát se") }
            },
            dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text("Hrát dál") } },
        )
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GameContent(state: GameUiState, actions: GameActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SoccerBall(size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("SLOVNÍ FOTBAL", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    }
                },
                actions = {
                    IconButton(onClick = actions.onOpenStatistics, modifier = Modifier.testTag("statistics_button")) {
                        Icon(AppIcons.Leaderboard, contentDescription = "Statistiky")
                    }
                    IconButton(onClick = actions.onOpenSettings, modifier = Modifier.testTag("settings_button")) {
                        Icon(AppIcons.Settings, contentDescription = "Nastavení")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // malý displej nebo otevřená klávesnice -> kompaktní rozvržení, historie má vždy dost místa
            val imeVisible = WindowInsets.isImeVisible
            val compact = imeVisible || maxHeight < 560.dp
            val narrow = maxWidth < 380.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = if (narrow) 12.dp else 16.dp),
            ) {
                TurnPanel(state, compact = compact)
                AnimatedVisibility(state.pendingRulesChange && !state.phase.isFinished && !state.preparingGame) {
                    RulesChangedBanner(actions.onNewGame)
                }
                MoveHistory(state, showScoreBoard = !imeVisible, compact = compact, modifier = Modifier.weight(1f))
                if (state.phase.isFinished) {
                    FinishedBar(state, actions)
                } else {
                    InputArea(state, actions, narrow = narrow)
                }
            }
            ResultScreen(
                visible = state.showResult,
                state = state,
                onPlayAgain = actions.onNewGame,
                onClose = actions.onDismissResult,
                onStatistics = actions.onOpenStatistics,
            )
        }
    }
}

/** Kdo je na tahu a na jaké písmeno se hraje – zůstává vždy vidět. */
@Composable
private fun TurnPanel(state: GameUiState, compact: Boolean) {
    val colors = GameTheme.colors
    val number = remember { NumberFormat.getIntegerInstance(CS) }
    val (title, subtitle) = when {
        state.preparingGame -> "Nová hra" to "Připravuji hřiště…"
        state.phase == GamePhase.READY -> "Výkop!" to "Začni libovolným slovem."
        state.phase == GamePhase.PLAYER_TURN -> "Jsi na tahu" to "Slovo na ${state.requiredLetterVariants ?: "?"}"
        state.phase == GamePhase.COMPUTER_THINKING -> "Hraje počítač" to "Počítač přemýšlí…"
        state.phase == GamePhase.PLAYER_WON -> "Konec hry" to "Vyhrál jsi!"
        state.phase == GamePhase.PLAYER_LOST -> "Konec hry" to "Tentokrát vyhrál počítač."
        else -> "" to ""
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Row(Modifier.padding(if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            LetterBall(
                letter = state.requiredLetter,
                active = state.isPlayerTurn,
                size = if (compact) 56.dp else 80.dp,
                modifier = Modifier.testTag("required_letter"),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val turnColor = when (state.phase) {
                    GamePhase.COMPUTER_THINKING -> colors.computer
                    GamePhase.PLAYER_LOST -> colors.lose
                    else -> colors.player
                }
                Text(
                    title.uppercase(CS),
                    style = MaterialTheme.typography.labelLarge,
                    color = turnColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("turn_subtitle"),
                )
                if (!compact && !state.preparingGame) {
                    val info = when (state.phase) {
                        GamePhase.PLAYER_TURN -> "Ve slovníku ${remainingText(state.availableWords, number)}"
                        GamePhase.READY -> "Ve hře je ${number.format(state.playableWordCount)} povolených slov"
                        else -> null
                    }
                    if (info != null) {
                        Text(
                            info,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (state.phase == GamePhase.COMPUTER_THINKING || state.preparingGame) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(0.7f),
                        color = colors.computer,
                        trackColor = colors.computerContainer,
                    )
                }
            }
        }
    }
}

/** „zbývá 1 slovo“, „zbývají 3 slova“, „zbývá 120 slov“. */
private fun remainingText(n: Int, format: NumberFormat): String = when {
    n == 1 -> "zbývá 1 slovo"
    n in 2..4 -> "zbývají $n slova"
    else -> "zbývá ${format.format(n)} slov"
}

@Composable
private fun RulesChangedBanner(onNewGame: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("rules_changed_banner"),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Nové nastavení platí od příští hry.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNewGame) { Text("Nová hra", maxLines = 1) }
        }
    }
}

/** Historie tahů; skóre je první položkou a s přibývajícími tahy odjede nahoru. */
@Composable
private fun MoveHistory(state: GameUiState, showScoreBoard: Boolean, compact: Boolean, modifier: Modifier) {
    val listState = rememberLazyListState()
    val thinking = state.phase == GamePhase.COMPUTER_THINKING
    val header = (if (showScoreBoard) 1 else 0) + (if (state.moves.isEmpty()) 1 else 0)
    val lastIndex = header + state.moves.size + (if (thinking) 1 else 0) // položka „bottom“
    LaunchedEffect(lastIndex, state.moves.size) {
        if (state.moves.isNotEmpty()) listState.animateScrollToItem(lastIndex) // novější tahy jsou vždy vidět
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_list"),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showScoreBoard) {
            item(key = "score") {
                ScoreBoard(
                    wins = state.statistics.wins,
                    losses = state.statistics.losses,
                    difficultyLabel = state.rules.difficulty.label,
                    moveCount = state.moves.size,
                    modifier = Modifier.testTag("score_board"),
                )
            }
        }
        if (state.moves.isEmpty()) {
            item(key = "empty") { EmptyHistory(compact, Modifier.animateItem()) }
        }
        itemsIndexed(state.moves, key = { i, m -> "$i-${m.word}" }) { i, move ->
            MoveBubble(
                index = i + 1,
                player = move.player,
                word = move.word,
                chRule = state.rules.chRule,
                isLatest = i == state.moves.lastIndex,
                modifier = Modifier
                    .animateItem()
                    .testTag("move_${i + 1}"),
            )
        }
        if (thinking) {
            item(key = "thinking") { ThinkingBubble(Modifier.animateItem()) }
        }
        item(key = "bottom") { Spacer(Modifier.height(2.dp)) }
    }
}

@Composable
private fun EmptyHistory(compact: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 8.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!compact) {
            SoccerBall(size = 48.dp)
            Spacer(Modifier.height(10.dp))
        }
        Text("Hřiště je připravené", style = MaterialTheme.typography.titleMedium)
        Text(
            "Napiš první slovo. Počítač odpoví slovem na jeho poslední písmeno a pak zase ty.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun InputArea(state: GameUiState, actions: GameActions, narrow: Boolean) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var successFlash by remember { mutableStateOf(false) }
    val colors = GameTheme.colors

    LaunchedEffect(state.errorPulse) {
        if (state.errorPulse > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            shake.snapTo(0f)
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 360
                    -14f at 50
                    12f at 110
                    -9f at 170
                    6f at 230
                    -3f at 290
                },
            )
        }
    }
    LaunchedEffect(state.acceptedPulse) {
        if (state.acceptedPulse > 0) {
            keyboard?.hide() // automatické skrytí klávesnice po platném odeslání
            focusManager.clearFocus()
            successFlash = true
            kotlinx.coroutines.delay(700)
            successFlash = false
        }
    }
    val borderColor by animateColorAsState(
        when {
            successFlash -> colors.player
            state.inputError != null -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        label = "border",
    )
    val enabled = state.isPlayerTurn

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        AnimatedVisibility(state.hint != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            AssistChip(
                onClick = actions.onApplyHint,
                label = {
                    Text(
                        "Nápověda: ${state.hint?.uppercase(CS)} – klepni pro vložení",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(AppIcons.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.testTag("hint_chip"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }) {
            OutlinedTextField(
                value = state.input,
                onValueChange = actions.onInputChange,
                enabled = enabled,
                singleLine = true,
                isError = state.inputError != null,
                placeholder = {
                    Text(
                        when {
                            state.preparingGame -> "Chvilku…"
                            state.phase == GamePhase.COMPUTER_THINKING -> "Počkej na počítač…"
                            state.requiredLetter != null -> "Slovo na ${state.requiredLetter}…"
                            else -> "První slovo…"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = if (successFlash) {
                    { Icon(AppIcons.Check, contentDescription = "Slovo přijato", tint = colors.player) }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { actions.onSubmit() }),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = borderColor,
                    unfocusedBorderColor = borderColor,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("word_input"),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = actions.onSubmit,
                enabled = enabled && state.input.isNotBlank(),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = if (narrow) 12.dp else 18.dp, vertical = 16.dp),
                modifier = Modifier.testTag("send_button"),
            ) {
                if (!narrow) {
                    Icon(AppIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text("ODESLAT", maxLines = 1)
            }
        }
        AnimatedVisibility(state.inputError != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                text = state.inputError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp)
                    .testTag("input_error")
                    .semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = actions.onNewGame,
                enabled = !state.preparingGame,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("new_game_button"),
            ) {
                Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("NOVÁ HRA", maxLines = 1)
            }
            FilledTonalButton(
                onClick = actions.onHint,
                enabled = enabled && state.hintsLeft > 0,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("hint_button"),
            ) {
                Icon(AppIcons.Lightbulb, contentDescription = "Nápověda", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("${state.hintsLeft}×", maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            val canSurrender = enabled && state.moves.isNotEmpty()
            if (narrow) {
                OutlinedIconButton(
                    onClick = actions.onSurrender,
                    enabled = canSurrender,
                    modifier = Modifier.testTag("surrender_button"),
                ) {
                    Icon(AppIcons.Flag, contentDescription = "Vzdát se")
                }
            } else {
                TextButton(
                    onClick = actions.onSurrender,
                    enabled = canSurrender,
                    modifier = Modifier.testTag("surrender_button"),
                ) {
                    Icon(AppIcons.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Vzdát se", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun FinishedBar(state: GameUiState, actions: GameActions) {
    val won = state.phase == GamePhase.PLAYER_WON
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (won) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (won) "Vyhrál jsi!" else "Tentokrát vyhrál počítač.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = actions.onNewGame, enabled = !state.preparingGame, modifier = Modifier.testTag("new_game_button")) {
                Text(if (won) "HRÁT ZNOVU" else "ODVETA", maxLines = 1)
            }
        }
    }
}

/** Úvodní obrazovka během načítání slovníku. */
@Composable
fun LoadingContent(progress: Float) {
    val colors = GameTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.pitch, colors.pitch.copy(alpha = 0.85f))))
            .testTag("loading"),
        contentAlignment = Alignment.Center,
    ) {
        PitchLines(Modifier.fillMaxSize(), colors.pitchLines.copy(alpha = 0.4f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            SoccerBall(size = 96.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "SLOVNÍ FOTBAL",
                fontFamily = Baloo,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text("Načítám český slovník…", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .width(220.dp)
                    .height(8.dp),
                color = colors.ball,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${(progress.coerceIn(0f, 1f) * 100).roundToInt()} %",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Chyba (např. chybějící nebo poškozený slovník). */
@Composable
fun ErrorContent(message: String?, onRetry: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            SoccerBall(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text("Něco se pokazilo", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                message ?: "Slovník se nepodařilo načíst.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors()) { Text("Zkusit znovu") }
        }
    }
}
