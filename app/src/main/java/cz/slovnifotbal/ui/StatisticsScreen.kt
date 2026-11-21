package cz.slovnifotbal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.slovnifotbal.domain.Difficulty
import cz.slovnifotbal.domain.GameStatistics
import cz.slovnifotbal.ui.components.AppIcons
import cz.slovnifotbal.ui.components.SectionCard
import cz.slovnifotbal.ui.components.StatTile
import cz.slovnifotbal.ui.theme.Baloo
import cz.slovnifotbal.ui.theme.GameTheme
import cz.slovnifotbal.viewmodel.StatisticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, onBack: () -> Unit) {
    val stats by viewModel.statistics.collectAsStateWithLifecycle()
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiky") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Zpět")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("statistics_screen"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WinRateCard(stats)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Odehrané hry", stats.gamesPlayed.toString(), Modifier.weight(1f).testTag("stat_games"))
                    StatTile("Výhry", stats.wins.toString(), Modifier.weight(1f).testTag("stat_wins"), GameTheme.colors.win)
                    StatTile("Prohry", stats.losses.toString(), Modifier.weight(1f).testTag("stat_losses"), GameTheme.colors.lose)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Nejdelší řetězec slov", stats.longestChain.toString(), Modifier.weight(1f))
                    StatTile("Nejvíc tvých slov v jedné hře", stats.mostPlayerWordsInGame.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Použitá slova celkem", stats.totalWords.toString(), Modifier.weight(1f))
                    StatTile("Z toho tvoje", stats.totalPlayerWords.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Aktuální série výher", stats.currentWinStreak.toString(), Modifier.weight(1f))
                    StatTile("Nejdelší série výher", stats.bestWinStreak.toString(), Modifier.weight(1f))
                }
                SectionCard("Podle obtížnosti") {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Text("Obtížnost", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.4f))
                        Text("Hry", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text("Výhry", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text("Úspěšnost", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    Difficulty.entries.forEach { d ->
                        val games = stats.gamesOn(d)
                        val wins = stats.winsOn(d)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(d.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1.4f))
                            Text(games.toString(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).testTag("stat_games_${d.key}"))
                            Text(wins.toString(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(if (games == 0) "–" else "${wins * 100 / games} %", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                }
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Vynulovat statistiky")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Vynulovat statistiky?") },
            text = { Text("Všechny výsledky budou smazány. Tuto akci nelze vrátit.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.reset()
                    confirmReset = false
                }) { Text("Vynulovat") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Zrušit") } },
        )
    }
}

@Composable
private fun WinRateCard(stats: GameStatistics) {
    SectionCard("Úspěšnost") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { stats.winRatePercent / 100f },
                    modifier = Modifier.size(96.dp),
                    strokeWidth = 10.dp,
                    color = GameTheme.colors.win,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    "${stats.winRatePercent} %",
                    fontFamily = Baloo,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    modifier = Modifier.testTag("stat_win_rate"),
                )
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    if (stats.gamesPlayed == 0) "Zatím žádná dohraná hra." else "Vyhrál jsi ${stats.wins} z ${stats.gamesPlayed} her.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Statistiky se ukládají jen v tomto telefonu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
