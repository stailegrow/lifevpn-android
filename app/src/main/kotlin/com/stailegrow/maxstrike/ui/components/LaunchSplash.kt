package com.stailegrow.maxstrike.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.Palette
import com.stailegrow.maxstrike.ui.theme.accentBrush
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Экран запуска — Life VPN 2.0. Показывается один раз при старте
 * приложения перед AppRoot (см. MainActivity.kt).
 *
 * Раньше это был статичный кружок сплошного цвета на неподвижном фоне —
 * пользователь попросил заменить его на настоящий логотип (та же
 * "капля с разрывом", что в иконке приложения и в глифе кнопки
 * подключения) и сделать сам экран живым — тот же переливающийся
 * анимированный фон, что крутится вокруг кнопки подключения, только на
 * весь экран. Логотип дышит/покачивается и залит вращающимся акцентным
 * градиентом темы, вырастает пружиной из центра, следом мягко
 * проявляется вордмарк "Life VPN". Чисто декоративный экран, никакой
 * логики подключения здесь нет (см. PLAN.md — 2.0 это косметика, а не
 * новый функционал).
 */
@Composable
fun LaunchSplash(onFinished: () -> Unit, holdMillis: Long = 1700L) {
    val palette = LocalPalette.current
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appeared = true
        delay(holdMillis)
        onFinished()
    }

    val logoScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "splash-logo-scale",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "splash-logo-alpha",
    )
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "splash-wordmark-alpha",
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Тот же живой переливающийся фон, что и вокруг кнопки подключения
        // и на основных экранах приложения — во весь экран. Это разовый
        // экран запуска, тумблер "Живой фон" в настройках тут ни при чём,
        // поэтому animated всегда true.
        AppBackground(animated = true, modifier = Modifier.fillMaxSize())

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SplashLogo(
                palette = palette,
                modifier = Modifier
                    .size(168.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha),
            )
            Spacer(modifier = Modifier.height(22.dp))
            val title = buildAnnotatedString {
                withStyle(SpanStyle(color = palette.textPrimary)) { append("Life ") }
                withStyle(SpanStyle(brush = palette.accentBrush())) { append("VPN") }
            }
            Text(
                text = title,
                style = HudType.wordmark(24.sp),
                modifier = Modifier.alpha(wordmarkAlpha),
            )
        }
    }
}

/**
 * Фирменный знак — та же органическая "капля с разрывом", что и в иконке
 * приложения (см. gen_icon.py) и перекликается с глифом питания в кнопке
 * подключения, вместо безликого кружка-лоадера. Мягкий пульсирующий ореол
 * позади, само пятно непрерывно дышит и залито вращающимся градиентом —
 * не статичная картинка, а живой знак, ровно то же ощущение, что и у
 * кнопки подключения на главном экране.
 */
@Composable
private fun SplashLogo(palette: Palette, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "splash-logo")
    val breathePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "splash-logo-breathe",
    )
    val haloPulsePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "splash-logo-halo",
    )
    val gradientAngleDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "splash-logo-gradient",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Мягкий пульсирующий ореол позади знака — тот же язык, что и
        // halo вокруг BlobConnectButton.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val haloRadius = size.minDimension / 2f * (0.92f + 0.12f * sin(haloPulsePhase))
            drawCircle(
                brush = Brush.radialGradient(
                    0f to palette.accentStart.copy(alpha = 0.55f),
                    0.6f to palette.accentEnd.copy(alpha = 0.28f),
                    1f to palette.accentEnd.copy(alpha = 0f),
                    center = center,
                    radius = haloRadius,
                ),
                radius = haloRadius,
                center = center,
            )
        }

        // Само пятно — рисуется в офскрин-слое (CompositingStrategy.Offscreen),
        // чтобы круглый вырез внутри (BlendMode.Clear) по-настоящему прорезал
        // капле "дыру" и сквозь неё было видно живой фон позади, а не просто
        // закрашивал кружок сплошным цветом.
        Box(
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val blobRadius = size.minDimension / 2f * 0.92f
                val blobPath = buildSplashBlobPath(center, blobRadius, phase = breathePhase)

                val gradAngleRad = Math.toRadians(gradientAngleDeg.toDouble())
                val gradDx = cos(gradAngleRad).toFloat()
                val gradDy = sin(gradAngleRad).toFloat()
                val gradStart = Offset(center.x - gradDx * blobRadius, center.y - gradDy * blobRadius)
                val gradEnd = Offset(center.x + gradDx * blobRadius, center.y + gradDy * blobRadius)
                val fillBrush = Brush.linearGradient(
                    listOf(palette.accentStart, palette.accentEnd),
                    start = gradStart,
                    end = gradEnd,
                )
                drawPath(blobPath, brush = fillBrush)

                // Круглый разрыв — та же геометрия (доля радиуса, смещение,
                // угол), что и в иконке приложения, для узнаваемости знака.
                val holeRadius = blobRadius * 0.16f
                val holeDist = blobRadius * 0.55f
                val holeAngleRad = Math.toRadians(-55.0)
                val holeCenter = Offset(
                    center.x + holeDist * cos(holeAngleRad).toFloat(),
                    center.y + holeDist * sin(holeAngleRad).toFloat(),
                )
                drawCircle(
                    color = Color.Black,
                    radius = holeRadius,
                    center = holeCenter,
                    blendMode = BlendMode.Clear,
                )
            }
        }
    }
}

private fun buildSplashBlobPath(center: Offset, baseRadius: Float, phase: Float): Path {
    val points = 20
    val wobble = 0.8f
    val pts = ArrayList<Offset>(points)
    for (i in 0 until points) {
        val theta = (i.toFloat() / points) * (2f * PI.toFloat())
        // ВАЖНО: множитель у phase здесь обязан быть ЦЕЛЫМ числом. phase —
        // это зацикленные "часы" с периодом ровно 2π (RepeatMode.Restart),
        // поэтому sin(... + phase) и sin(... - phase) сами по себе идеально
        // бесшовны на стыке цикла. Но если умножить phase на НЕцелый
        // коэффициент (здесь раньше стояло 1.4f), то к моменту, когда phase
        // допрыгивает до 2π и мгновенно сбрасывается на 0, сам множитель
        // "phase * 1.4" не возвращается к тому же значению по модулю 2π —
        // контур пятна заметно "дёргался" ровно раз за цикл дыхания. Любое
        // целое (2f, как сейчас, -1f, 3f и т.д.) сохраняет периодичность.
        val r = baseRadius * (
            1f +
                wobble * 0.11f * sin(3f * theta + phase) +
                wobble * 0.06f * sin(5f * theta - phase * 2f)
            )
        pts.add(Offset(center.x + r * cos(theta), center.y + r * sin(theta)))
    }

    val path = Path()
    val first = pts[0]
    val last = pts[points - 1]
    path.moveTo((last.x + first.x) / 2f, (last.y + first.y) / 2f)
    for (i in 0 until points) {
        val cur = pts[i]
        val next = pts[(i + 1) % points]
        val midX = (cur.x + next.x) / 2f
        val midY = (cur.y + next.y) / 2f
        path.quadraticTo(cur.x, cur.y, midX, midY)
    }
    path.close()
    return path
}
