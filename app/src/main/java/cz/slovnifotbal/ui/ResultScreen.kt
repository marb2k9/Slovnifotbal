package cz.slovnifotbal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.slovnifotbal.domain.EndReason
import cz.slovnifotbal.domain.GamePhase
import cz.slovnifotbal.domain.Player
import cz.slovnifotbal.ui.components.AppIcons
import cz.slovnifotbal.ui.theme.GameTheme
import cz.slovnifotbal.viewmodel.GameUiState
import java.util.Locale

private val CS: Locale = Locale.forLanguageTag("cs-CZ")

/**
 * Výsledková obrazovka po skončení hry (zobrazuje se přes herní plochu).
 * Výhra: „VYHRÁL JSI!“ + HRÁT ZNOVU, prohra: „TENTOKRÁT VYHRÁL POČÍTAČ.“ + ODVETA.
 */
@Composable
fun ResultScreen(
    visible: Boolean,
    state: GameUiState,
    onPlayAgain: () -> Unit,
    onClose: () -> Unit,
    onStatistics: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                // Doteky mimo kartu nesmí projít do hry pod překryvem. Záměrně ne clickable: to by
                // pro čtečku obrazovky sloučilo celou kartu do jedné položky.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .semantics { paneTitle = "Výsledek hry" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.animateEnterExit(
                    enter = scaleIn(initialScale = 0.7f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.9f) + fadeOut(),
                ),
            ) {
                ResultCard(state, onPlayAgain, onClose, onStatistics)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(state: GameUiState, onPlayAgain: () -> Unit, onClose: () -> Unit, onStatistics: () -> Unit) {
    val colors = GameTheme.colors
    val won = state.phase == GamePhase.PLAYER_WON
    val accent = if (won) colors.win else colors.lose
    val letter = state.requiredLetterVariants ?: state.requiredLetter ?: "?"
    val reason = when (state.endReason) {
        EndReason.COMPUTER_HAS_NO_WORD -> "Počítač nenašel žádné slovo na písmeno $letter."
        EndReason.PLAYER_HAS_NO_WORD -> "Na písmeno $letter už ve slovníku nezbylo žádné slovo. Nemáš už žádné platné slovo. Prohráváš!"
        EndReason.PLAYER_SURRENDERED -> "Vzdal ses. Příště to vyjde!"
        EndReason.PLAYER_ABANDONED -> "Hra byla předčasně ukončena."
        null -> ""
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .padding(20.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .testTag("result_screen"),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (won) AppIcons.Trophy else AppIcons.Robot,
                    contentDescription = null,
                    tint = if (won) colors.onPlayer else colors.onComputer,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (won) "VYHRÁL JSI!" else "TENTOKRÁT VYHRÁL POČÍTAČ.",
                style = MaterialTheme.typography.displaySmall,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("result_title"),
            )
            if (reason.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(reason, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ResultStat("Počet tahů", state.moves.size.toString(), Modifier.weight(1f).testTag("result_moves"))
                ResultStat("Tvoje slova", state.playerWordCount.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Obtížnost: ${state.rules.difficulty.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("Průběh hry", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.moves.forEachIndexed { i, move ->
                        val human = move.player == Player.HUMAN
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (human) colors.playerContainer else colors.computerContainer,
                                contentColor = if (human) colors.onPlayerContainer else colors.onComputerContainer,
                            ) {
                                Text(
                                    move.word.uppercase(CS),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            if (i < state.moves.lastIndex) {
                                Icon(
                                    AppIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onPlayAgain,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("play_again_button"),
            ) {
                Icon(AppIcons.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (won) "HRÁT ZNOVU" else "ODVETA", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            // na úzkém displeji se druhé tlačítko zalomí pod první
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = onClose, modifier = Modifier.testTag("close_result_button")) {
                    Text("Prohlédnout hru", maxLines = 1)
                }
                TextButton(onClick = onStatistics) { Text("Statistiky", maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String, modifier: Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
