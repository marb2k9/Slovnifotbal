package cz.slovnifotbal.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cz.slovnifotbal.domain.ThemeMode

private val LightColors = lightColorScheme(
    primary = Grass40,
    onPrimary = Color.White,
    primaryContainer = Grass90,
    onPrimaryContainer = Grass10,
    secondary = Sky40,
    onSecondary = Color.White,
    secondaryContainer = Sky90,
    onSecondaryContainer = Sky10,
    tertiary = Gold40,
    onTertiary = Color.White,
    tertiaryContainer = Gold90,
    onTertiaryContainer = Gold10,
    background = Field99,
    onBackground = Ink10,
    surface = Field99,
    onSurface = Ink10,
    surfaceVariant = Field90,
    onSurfaceVariant = Ink30,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F6EC),
    surfaceContainer = Field95,
    surfaceContainerHigh = Color(0xFFE3ECDF),
    surfaceContainerHighest = Field90,
    outline = Ink50,
    outlineVariant = Color(0xFFC0C9BE),
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = Grass80,
    onPrimary = Grass20,
    primaryContainer = Grass30,
    onPrimaryContainer = Grass90,
    secondary = Sky80,
    onSecondary = Sky20,
    secondaryContainer = Sky30,
    onSecondaryContainer = Sky90,
    tertiary = Gold80,
    onTertiary = Gold20,
    tertiaryContainer = Gold30,
    onTertiaryContainer = Gold90,
    background = Night6,
    onBackground = Night90,
    surface = Night6,
    onSurface = Night90,
    surfaceVariant = Night22,
    onSurfaceVariant = Night80,
    surfaceContainerLowest = Color(0xFF09100B),
    surfaceContainerLow = Night10,
    surfaceContainer = Color(0xFF1A221C),
    surfaceContainerHigh = Night17,
    surfaceContainerHighest = Night22,
    outline = Color(0xFF8A9389),
    outlineVariant = Color(0xFF414941),
    error = ErrorRedDark,
)

/** Doplňkové barvy hry (hráč, počítač, výhra, prohra). */
@Immutable
data class GameColors(
    val player: Color,
    val onPlayer: Color,
    val playerContainer: Color,
    val onPlayerContainer: Color,
    val computer: Color,
    val onComputer: Color,
    val computerContainer: Color,
    val onComputerContainer: Color,
    val ball: Color,
    val onBall: Color,
    val win: Color,
    val lose: Color,
    val pitch: Color,
    val pitchLines: Color,
)

private val LightGameColors = GameColors(
    player = Grass40, onPlayer = Color.White, playerContainer = Grass90, onPlayerContainer = Grass10,
    computer = Sky40, onComputer = Color.White, computerContainer = Sky90, onComputerContainer = Sky10,
    ball = Color(0xFFFFB627), onBall = Color(0xFF2A1700),
    win = Color(0xFF1B7F3A), lose = Color(0xFF2155B5),
    pitch = PitchStripeLight, pitchLines = Color(0xCCFFFFFF),
)

private val DarkGameColors = GameColors(
    player = Grass80, onPlayer = Grass20, playerContainer = Grass30, onPlayerContainer = Grass90,
    computer = Sky80, onComputer = Sky20, computerContainer = Sky30, onComputerContainer = Sky90,
    ball = Color(0xFFFFC95C), onBall = Color(0xFF2A1700),
    win = Grass80, lose = Sky80,
    pitch = Color(0xFF15502A), pitchLines = Color(0x99FFFFFF),
)

val LocalGameColors = staticCompositionLocalOf { LightGameColors }

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SlovniFotbalTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalGameColors provides if (dark) DarkGameColors else LightGameColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object GameTheme {
    val colors: GameColors
        @Composable get() = LocalGameColors.current
}
