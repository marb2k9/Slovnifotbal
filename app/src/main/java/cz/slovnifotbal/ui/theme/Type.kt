package cz.slovnifotbal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cz.slovnifotbal.R

/** Nunito – čitelné, přátelské písmo s plnou podporou české diakritiky (SIL OFL). */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

/** Baloo 2 – výrazné písmo pro nadpisy a velké písmeno (SIL OFL). */
val Baloo = FontFamily(
    Font(R.font.baloo2_bold, FontWeight.Bold),
    Font(R.font.baloo2_extrabold, FontWeight.ExtraBold),
)

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 68.sp),
    displayMedium = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 48.sp),
    displaySmall = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)
