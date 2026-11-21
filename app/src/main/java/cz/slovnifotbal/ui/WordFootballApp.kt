package cz.slovnifotbal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.slovnifotbal.domain.GameSettings
import cz.slovnifotbal.ui.theme.SlovniFotbalTheme
import cz.slovnifotbal.viewmodel.AppViewModelProvider
import cz.slovnifotbal.viewmodel.GameViewModel
import cz.slovnifotbal.viewmodel.SettingsViewModel
import cz.slovnifotbal.viewmodel.StatisticsViewModel
import kotlinx.coroutines.flow.Flow

/** Obrazovky aplikace. */
enum class Screen { GAME, SETTINGS, STATISTICS }

/** Kořenová komponenta aplikace: téma a navigace mezi obrazovkami. */
@Composable
fun WordFootballApp(settingsFlow: Flow<GameSettings>) {
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = GameSettings())
    SlovniFotbalTheme(themeMode = settings.themeMode) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var screen by rememberSaveable { mutableStateOf(Screen.GAME) }
            val gameViewModel: GameViewModel = viewModel(factory = AppViewModelProvider.Factory)
            BackHandler(enabled = screen != Screen.GAME) { screen = Screen.GAME }
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState == Screen.GAME) {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                    }
                },
                label = "screen",
            ) { target ->
                when (target) {
                    Screen.GAME -> GameScreen(
                        viewModel = gameViewModel,
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onOpenStatistics = { screen = Screen.STATISTICS },
                    )
                    Screen.SETTINGS -> {
                        val vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        val gameState by gameViewModel.uiState.collectAsStateWithLifecycle()
                        SettingsScreen(vm, gameState.dictionaryWordCount, onBack = { screen = Screen.GAME })
                    }
                    Screen.STATISTICS -> {
                        val vm: StatisticsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                        StatisticsScreen(vm, onBack = { screen = Screen.GAME })
                    }
                }
            }
        }
    }
}
