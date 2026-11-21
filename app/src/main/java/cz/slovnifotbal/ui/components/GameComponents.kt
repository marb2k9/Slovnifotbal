package cz.slovnifotbal.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.slovnifotbal.domain.ChRule
import cz.slovnifotbal.domain.Player
import cz.slovnifotbal.ui.theme.Baloo
import cz.slovnifotbal.ui.theme.GameTheme
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val CS: Locale = Locale.forLanguageTag("cs-CZ")

/** Jednoduchý kreslený fotbalový míč (bez bitmap). */
@Composable
fun SoccerBall(modifier: Modifier = Modifier, size: Dp = 48.dp, lineColor: Color = Color(0xFF1D241F)) {
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(Color.White, radius = r, center = c)
        val inner = r * 0.36f
        fun point(radius: Float, angleDeg: Float) = Offset(
            c.x + radius * cos(Math.toRadians(angleDeg.toDouble())).toFloat(),
            c.y + radius * sin(Math.toRadians(angleDeg.toDouble())).toFloat(),
        )
        // prostřední pětiúhelník
        val pent = Path().apply {
            for (i in 0 until 5) {
                val p = point(inner, -90f + i * 72f)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(pent, lineColor)
        // čáry k okraji a krajní záplaty
        for (i in 0 until 5) {
            val a = -90f + i * 72f
            drawLine(lineColor, point(inner, a), point(r * 0.78f, a), strokeWidth = r * 0.07f)
            val patch = Path().apply {
                val b = a + 36f
                val p1 = point(r * 0.80f, b - 16f)
                val p2 = point(r * 1.02f, b - 22f)
                val p3 = point(r * 1.02f, b + 22f)
                val p4 = point(r * 0.80f, b + 16f)
                val p0 = point(r * 0.68f, b)
                moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); lineTo(p4.x, p4.y); close()
            }
            drawPath(patch, lineColor)
        }
        drawCircle(lineColor, radius = r - r * 0.035f, center = c, style = Stroke(width = r * 0.07f))
    }
}

/** Velké požadované písmeno ve „míči“ – pulzuje, když je hráč na tahu. */
@Composable
fun LetterBall(letter: String?, active: Boolean, modifier: Modifier = Modifier, size: Dp = 92.dp) {
    val colors = GameTheme.colors
    val transition = rememberInfiniteTransition(label = "ballPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(if (active) pulse else 1f)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(colors.ball.copy(alpha = 0.92f), colors.ball)))
            .border(4.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .semantics { contentDescription = if (letter != null) "Požadované písmeno $letter" else "Libovolné písmeno" },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = letter,
            transitionSpec = { (scaleIn(initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut(targetScale = 1.6f) + fadeOut()) },
            label = "letter",
        ) { l ->
            if (l == null) {
                SoccerBall(size = size * 0.62f)
            } else {
                // velikost písmene je pevně svázaná s velikostí míče (nezávisí na zvětšení písma v systému)
                val letterSize = with(LocalDensity.current) { (size * if (l.length > 1) 0.42f else 0.56f).toSp() }
                Text(
                    text = l,
                    fontFamily = Baloo,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = letterSize,
                    lineHeight = letterSize,
                    color = colors.onBall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Slovo se zvýrazněným prvním a posledním písmenem (návaznost řetězce). */
fun highlightedWord(word: String, chRule: ChRule, accent: Color, base: Color): AnnotatedString {
    val upper = word.uppercase(CS)
    val startLen = if (chRule == ChRule.SINGLE_LETTER && word.startsWith("ch") && word.length > 2) 2 else 1
    val endLen = if (chRule == ChRule.SINGLE_LETTER && word.endsWith("ch") && word.length > 2) 2 else 1
    return buildAnnotatedString {
        if (upper.length <= startLen + endLen - 1) {
            withStyle(SpanStyle(color = base)) { append(upper) }
            return@buildAnnotatedString
        }
        withStyle(SpanStyle(color = base)) { append(upper.substring(0, startLen)) }
        withStyle(SpanStyle(color = base)) { append(upper.substring(startLen, upper.length - endLen)) }
        withStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Black)) {
            append(upper.substring(upper.length - endLen))
        }
    }
}

/** Bublina s jedním tahem v historii. */
@Composable
fun MoveBubble(index: Int, player: Player, word: String, chRule: ChRule, isLatest: Boolean, modifier: Modifier = Modifier) {
    val colors = GameTheme.colors
    val human = player == Player.HUMAN
    val container = if (human) colors.playerContainer else colors.computerContainer
    val content = if (human) colors.onPlayerContainer else colors.onComputerContainer
    val accent = if (human) colors.player else colors.computer
    val shape = if (human) RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp) else RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
    Row(
        // jedna položka pro čtečku obrazovky („TY · 1.“, „KOLO“)
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = if (human) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (human) Avatar(AppIcons.Person, accent, colors.onPlayer)
        Spacer(Modifier.width(8.dp))
        Surface(
            color = container,
            contentColor = content,
            shape = shape,
            tonalElevation = if (isLatest) 3.dp else 0.dp,
            shadowElevation = if (isLatest) 2.dp else 0.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(if (isLatest) Modifier.border(2.dp, accent.copy(alpha = 0.55f), shape) else Modifier),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = (if (human) "TY" else "POČÍTAČ") + "  ·  $index.",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
                Text(
                    text = highlightedWord(word, chRule, MaterialTheme.colorScheme.tertiary, content),
                    style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 1.sp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (!human) Avatar(AppIcons.Robot, accent, colors.onComputer)
    }
}

@Composable
private fun Avatar(icon: ImageVector, background: Color, tint: Color) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Animace „Počítač přemýšlí“. */
@Composable
fun ThinkingBubble(modifier: Modifier = Modifier) {
    val colors = GameTheme.colors
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1100)),
        label = "dots",
    )
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
        Surface(
            color = colors.computerContainer,
            contentColor = colors.onComputerContainer,
            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
            modifier = Modifier.semantics { contentDescription = "Počítač přemýšlí" },
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Počítač přemýšlí", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(10.dp))
                for (i in 0 until 3) {
                    val active = (phase.toInt() % 3) == i
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(if (active) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(colors.computer.copy(alpha = if (active) 1f else 0.4f)),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Avatar(AppIcons.Robot, colors.computer, colors.onComputer)
    }
}

/** Ukazatel skóre ve stylu stadionové tabule. */
@Composable
fun ScoreBoard(wins: Int, losses: Int, difficultyLabel: String, moveCount: Int, modifier: Modifier = Modifier) {
    val colors = GameTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.pitch)
            .semantics { contentDescription = "Skóre: ty $wins, počítač $losses. Obtížnost $difficultyLabel." },
    ) {
        PitchLines(Modifier.matchParentSize(), colors.pitchLines)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreSide("TY", wins, Modifier.weight(1f), Alignment.Start)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(":", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                Surface(color = Color.White.copy(alpha = 0.18f), contentColor = Color.White, shape = RoundedCornerShape(50)) {
                    Text(
                        difficultyLabel.uppercase(CS),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text("tah ${moveCount + 1}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
            }
            ScoreSide("POČÍTAČ", losses, Modifier.weight(1f), Alignment.End)
        }
    }
}

@Composable
private fun ScoreSide(label: String, value: Int, modifier: Modifier, align: Alignment.Horizontal) {
    Column(modifier, horizontalAlignment = align) {
        Text(label, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
        Text(value.toString(), color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 38.sp)
    }
}

/** Čáry hřiště jako dekorace pozadí. */
@Composable
fun PitchLines(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stripe = w / 8f
        for (i in 0 until 8 step 2) {
            drawRect(Color.White.copy(alpha = 0.05f), topLeft = Offset(i * stripe, 0f), size = androidx.compose.ui.geometry.Size(stripe, h))
        }
        val stroke = Stroke(width = 2.dp.toPx())
        drawLine(color.copy(alpha = 0.5f), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 2.dp.toPx())
        drawCircle(color.copy(alpha = 0.5f), radius = h * 0.32f, center = Offset(w / 2f, h / 2f), style = stroke)
        drawRect(color.copy(alpha = 0.35f), topLeft = Offset(-2f, h * 0.22f), size = androidx.compose.ui.geometry.Size(w * 0.09f, h * 0.56f), style = stroke)
        drawRect(color.copy(alpha = 0.35f), topLeft = Offset(w * 0.91f + 2f, h * 0.22f), size = androidx.compose.ui.geometry.Size(w * 0.09f, h * 0.56f), style = stroke)
    }
}

/** Karta s nadpisem pro obrazovky nastavení a statistik. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Dlaždice s jedním číslem statistik. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp, color = accent)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
