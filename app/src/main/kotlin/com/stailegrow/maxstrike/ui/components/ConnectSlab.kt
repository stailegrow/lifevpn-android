package com.stailegrow.maxstrike.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.core.ConnectionState
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.Palette
import com.stailegrow.maxstrike.ui.theme.accentBrush
import com.stailegrow.maxstrike.ui.theme.cutRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ConnectSlab — главный элемент управления, Life VPN 2.0. Никакой
 * карточки-рамки вокруг: как в согласованном эскизе (Main.dc.html), кнопка
 * с мягким ореолом и статус-текст сидят прямо на анимированном фоне,
 * без обводки и без прямоугольной подложки. Тап в любом месте — toggle()
 * выбранного сервера (сохранено из версии 1).
 *
 * Состояния:
 *  - Disconnected: дыхание, глиф "питание", подпись — имя сервера или
 *    "Выберите сервер".
 *  - Connecting: чаще "дышит" и вращается, спиннер вместо глифа, вокруг
 *    плывут три орбитальные точки.
 *  - Connected: зеленоватый акцентный градиент, галочка вместо глифа,
 *    разовая вспышка искр наружу, время сессии и внешний IP мягкими
 *    пилюлями.
 *  - Failed: сплошная заливка bad, заголовок и подпись цвета bad.
 */
@Composable
fun ConnectSlab(
    state: ConnectionState,
    serverName: String?,
    externalIP: String?,
    connectedSinceMillis: Long?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val connected = state is ConnectionState.Connected
    val connecting = state is ConnectionState.Connecting
    val failed = state is ConnectionState.Failed
    val enabled = serverName != null

    val headlineColor = if (failed) palette.bad else palette.textPrimary
    val headlineText = when (state) {
        is ConnectionState.Connected -> L.t("Подключено", "Connected")
        is ConnectionState.Connecting -> L.t("Подключение", "Connecting")
        is ConnectionState.Failed -> L.t("Ошибка", "Error")
        is ConnectionState.Disconnected -> L.t("Не подключено", "Not connected")
    }
    val captionText = when (state) {
        is ConnectionState.Failed -> state.message
        is ConnectionState.Connecting -> L.t("устанавливаем защищённый канал", "establishing a secure channel")
        else -> serverName ?: L.t("нажмите, чтобы включить", "tap to turn on")
    }

    val toggleInteractionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = toggleInteractionSource,
                indication = null,
                enabled = enabled,
            ) { onToggle() }
            .padding(vertical = 18.dp),
    ) {
        BlobConnectButton(connected = connected, connecting = connecting, failed = failed, palette = palette)

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = headlineText, style = HudType.hero(18.sp), color = headlineColor)
        Text(
            text = captionText,
            style = HudType.body(13.sp),
            color = palette.textSecondary,
            maxLines = 1,
        )

        if (connected && (connectedSinceMillis != null || externalIP != null)) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (connectedSinceMillis != null) {
                    StatPill(valueContent = { UptimeText(sinceMillis = connectedSinceMillis, color = palette.textPrimary) }, label = L.t("время", "time"))
                }
                externalIP?.let {
                    StatPill(valueContent = { Text(text = it, style = HudType.code(13.sp), color = palette.textPrimary) }, label = "IP")
                }
            }
        }
    }
}

/** Мягкая полупрозрачная пилюля для статистики — тот же язык, что круглые
 *  иконки в шапке эскиза: заливка без обводки. */
@Composable
private fun StatPill(valueContent: @Composable () -> Unit, label: String) {
    val palette = LocalPalette.current
    val shape = cutRect(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(palette.card)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        valueContent()
        Text(text = label, style = HudType.label(9.sp), color = palette.textSecondary)
    }
}

/**
 * Органическое "пятно" с мягким ореолом позади — форма и ореол
 * пересчитываются каждый кадр анимации, кешировать их как cutRect() не
 * имеет смысла. Единственный такой элемент на экране — лишняя перерисовка
 * тут не проблема.
 */
@Composable
private fun BlobConnectButton(connected: Boolean, connecting: Boolean, failed: Boolean, palette: Palette) {
    val infinite = rememberInfiniteTransition(label = "blob")

    // Все "часы" анимации теперь идут с ФИКСИРОВАННЫМ периодом, который
    // никогда не зависит от connecting/connected/failed. Раньше период и
    // диапазон каждого animateFloat пересчитывались по состоянию — при
    // смене состояния (например, тап на кнопку) Compose пересоздавал
    // анимацию с нуля, и текущее значение резко "прыгало" к начальному —
    // это и было причиной дёрганости. Теперь по состоянию плавно (через
    // animateFloatAsState) меняется только АМПЛИТУДА эффекта, а сами часы
    // тикают непрерывно и одинаково всегда.
    val breathePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "blob-breathe",
    )
    val haloPulsePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "blob-halo-pulse",
    )
    val gradientAngleDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "blob-gradient-angle",
    )
    val sheenAngleDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
        label = "blob-sheen-angle",
    )
    val orbitAngleDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
        label = "blob-orbit",
    )
    val spinnerAngleDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing)),
        label = "blob-spinner",
    )

    // Состояние меняет только силу эффекта, и делает это плавным тюином —
    // никогда не сбрасывая часы выше. Это и убирает рывок при переключении
    // Disconnected → Connecting → Connected.
    val easing = tween<Float>(380, easing = FastOutSlowInEasing)
    val rotationAmplitude by animateFloatAsState(
        targetValue = if (failed) 1f else if (connecting) 8f else if (connected) 2f else 4f,
        animationSpec = easing,
        label = "amp-rotation",
    )
    val scaleAmplitude by animateFloatAsState(
        targetValue = if (failed) 0.015f else if (connecting) 0.075f else if (connected) 0.02f else 0.035f,
        animationSpec = easing,
        label = "amp-scale",
    )
    val wobble by animateFloatAsState(
        targetValue = if (failed) 0.3f else if (connecting) 1.35f else if (connected) 0.55f else 0.55f,
        animationSpec = easing,
        label = "amp-wobble",
    )
    val haloPulseAmplitude by animateFloatAsState(
        targetValue = if (failed) 0.10f else if (connecting) 0.26f else if (connected) 0.13f else 0.16f,
        animationSpec = easing,
        label = "amp-halo",
    )

    // Однократная вспышка искр в момент успешного подключения — не
    // бесконечный цикл мерцания, а короткий "выстрел" наружу и затухание.
    val sparkleProgress = remember { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(connected) {
        if (connected) {
            sparkleProgress.snapTo(0f)
            scope.launch { sparkleProgress.animateTo(1f, animationSpec = tween(700, easing = LinearEasing)) }
        }
    }

    // AMOLED — настоящий чёрный экран, и кнопка на нём тоже должна быть
    // чёрной (не цветной), это и есть смысл AMOLED-темы. Раскраска
    // остаётся только для чёткой индикации подключения/ошибки.
    val amoledBlack = palette.id == "amoled" && !connected && !failed

    // Раньше ореол на AMOLED-теме всегда брал palette.accentStart (яркий
    // голубой) независимо от amoledBlack — само тело кнопки было чёрным,
    // но светящийся ореол вокруг него оставался синим, и кнопка визуально
    // читалась как "всё ещё синяя", а не чёрная. Теперь в состояниях
    // покоя/подключения на AMOLED ореол тоже нейтрально-тёмный.
    val haloColor = when {
        failed -> palette.bad
        amoledBlack -> Color(0xFF2A2A31)
        else -> palette.accentStart
    }
    // Глиф обычно рисуется цветом фона экрана — так он читается на фоне
    // цветной заливки кнопки. Но на чёрной AMOLED-кнопке фон = чёрный, и
    // такой глиф был бы не виден — на это время берём светлый цвет текста.
    val glyphColor = if (amoledBlack) palette.textPrimary else palette.background
    val orbitColor = if (connected) palette.good else palette.accentStart

    val outerSize = 208.dp

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(outerSize)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val haloRadius = size.minDimension / 2f * (1.05f + haloPulseAmplitude * sin(haloPulsePhase))
            drawCircle(
                brush = Brush.radialGradient(
                    0f to haloColor.copy(alpha = 0.85f),
                    0.55f to haloColor.copy(alpha = 0.50f),
                    1f to haloColor.copy(alpha = 0f),
                    center = center,
                    radius = haloRadius,
                ),
                radius = haloRadius,
                center = center,
            )

            val blobRadius = size.minDimension / 2f * 0.60f

            val gradAngleRad = Math.toRadians(gradientAngleDeg.toDouble())
            val gradDx = cos(gradAngleRad).toFloat()
            val gradDy = sin(gradAngleRad).toFloat()
            val gradStart = Offset(center.x - gradDx * blobRadius, center.y - gradDy * blobRadius)
            val gradEnd = Offset(center.x + gradDx * blobRadius, center.y + gradDy * blobRadius)
            val fillBrush = when {
                failed -> Brush.linearGradient(listOf(palette.bad, palette.bad), start = gradStart, end = gradEnd)
                connected -> Brush.linearGradient(listOf(palette.good, palette.accentEnd), start = gradStart, end = gradEnd)
                amoledBlack -> Brush.linearGradient(listOf(Color(0xFF0D0D10), Color(0xFF000000)), start = gradStart, end = gradEnd)
                else -> Brush.linearGradient(listOf(palette.accentStart, palette.accentEnd), start = gradStart, end = gradEnd)
            }

            val scaleFactor = 1f + scaleAmplitude * sin(breathePhase)
            rotate(degrees = rotationAmplitude * sin(breathePhase)) {
                scale(scaleFactor) {
                    val blobPath = buildBlobPath(center, blobRadius, phase = breathePhase, wobble = wobble)
                    drawPath(blobPath, brush = fillBrush)

                    // Мягкий обходящий блик — обрезан по форме пятна через
                    // clipPath, поэтому всегда остаётся внутри "капли".
                    // На чёрной AMOLED-кнопке он же даёт лёгкий "глянцевый"
                    // отблеск вместо статичной матовой заливки.
                    clipPath(blobPath) {
                        val sheenAngleRad = Math.toRadians(sheenAngleDeg.toDouble())
                        val sheenCenter = Offset(
                            center.x + blobRadius * 0.38f * cos(sheenAngleRad).toFloat(),
                            center.y + blobRadius * 0.38f * sin(sheenAngleRad).toFloat(),
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to Color.White.copy(alpha = if (amoledBlack) 0.16f else 0.22f),
                                1f to Color.White.copy(alpha = 0f),
                                center = sheenCenter,
                                radius = blobRadius * 0.75f,
                            ),
                            radius = blobRadius * 0.75f,
                            center = sheenCenter,
                        )
                    }
                }
            }

            // Символ внутри — неподвижен по позиции и размеру, реагирует
            // только сменой формы по фазе (питание / загрузка / галочка /
            // крест), не участвует в повороте/масштабе тела пятна.
            drawPhaseGlyph(
                center = center,
                radius = blobRadius,
                color = glyphColor,
                connecting = connecting,
                connected = connected,
                failed = failed,
                spinAngleDeg = spinnerAngleDeg,
            )

            if (connecting) {
                // ВАЖНО: угол орбиты используется напрямую, без умножения
                // на коэффициент скорости — раньше здесь стояло
                // "orbitAngleDeg * 1.6", и из-за этого умножения угол
                // переставал быть периодичным ровно на границе цикла
                // анимации (0°/360°), что и давало заметный "скачок" точек
                // каждые ~1.3 секунды. Скорость орбиты регулируется только
                // периодом самих часов (см. orbitAngleDeg выше).
                val orbitRadius = blobRadius + 26.dp.toPx()
                val dotRadius = 4.dp.toPx()
                repeat(3) { index ->
                    val orbitAngleRad = Math.toRadians((orbitAngleDeg + index * 120f).toDouble())
                    val cx = center.x + orbitRadius * cos(orbitAngleRad).toFloat()
                    val cy = center.y + orbitRadius * sin(orbitAngleRad).toFloat()
                    drawCircle(color = orbitColor, radius = dotRadius, center = Offset(cx, cy))
                }
            }

            if (sparkleProgress.value > 0f && sparkleProgress.value < 1f) {
                val burst = sparkleProgress.value
                val fade = 1f - burst
                val travel = blobRadius + 30.dp.toPx() * burst
                val sparkleAngles = listOf(-70f, 20f, 130f, 205f)
                sparkleAngles.forEach { sparkleAngleDeg ->
                    val sparkleAngleRad = Math.toRadians(sparkleAngleDeg.toDouble())
                    val sx = center.x + travel * cos(sparkleAngleRad).toFloat()
                    val sy = center.y + travel * sin(sparkleAngleRad).toFloat()
                    drawCircle(
                        color = Color.White.copy(alpha = fade * 0.9f),
                        radius = (2f + 2f * burst).dp.toPx(),
                        center = Offset(sx, sy),
                    )
                }
            }
        }
    }
}

/** Точки органического контура соединяются гладкими квадратичными кривыми
 *  через середины отрезков (классический приём построения "блоб"-формы),
 *  а не прямыми линиями — так контур всегда гладкий, без единого острого
 *  угла, как в согласованном эскизе (CSS border-radius blob). */
private fun buildBlobPath(center: Offset, baseRadius: Float, phase: Float, wobble: Float): Path {
    val points = 20
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

/** Глиф внутри кнопки — по фазе: классический значок питания в покое
 *  (кольцо с разрывом сверху + риска), та же дуга во время подключения,
 *  но вращается как спиннер, галочка при успехе, крестик при ошибке.
 *  Простые обводки вместо экспериментальной заливной формы — надёжнее и
 *  привычнее читаются на маленьком размере. Рисуется отдельно от тела
 *  пятна и не участвует в его повороте/масштабе — положение и размер
 *  символа всегда стабильны. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPhaseGlyph(
    center: Offset,
    radius: Float,
    color: Color,
    connecting: Boolean,
    connected: Boolean,
    failed: Boolean,
    spinAngleDeg: Float,
) {
    val glyphRadius = radius * 0.38f
    val strokeWidth = glyphRadius * 0.26f

    when {
        connecting -> {
            rotate(degrees = spinAngleDeg, pivot = center) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - glyphRadius, center.y - glyphRadius),
                    size = Size(glyphRadius * 2f, glyphRadius * 2f),
                )
            }
        }
        connected -> {
            val checkPath = Path().apply {
                moveTo(center.x - glyphRadius * 0.55f, center.y + glyphRadius * 0.05f)
                lineTo(center.x - glyphRadius * 0.1f, center.y + glyphRadius * 0.5f)
                lineTo(center.x + glyphRadius * 0.65f, center.y - glyphRadius * 0.45f)
            }
            drawPath(
                checkPath,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            )
        }
        failed -> {
            val a = glyphRadius * 0.5f
            drawLine(
                color = color,
                start = Offset(center.x - a, center.y - a),
                end = Offset(center.x + a, center.y + a),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(center.x + a, center.y - a),
                end = Offset(center.x - a, center.y + a),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        else -> {
            // Классический значок питания: разрыв кольца СВЕРХУ (по
            // 35 градусов в обе стороны от 12 часов), и риска идёт ровно
            // через этот разрыв. Раньше здесь стоял startAngle = -235f —
            // при sweepAngle = 290f разрыв получался не сверху, а СНИЗУ
            // кольца (около 6 часов), а риска рисовалась сверху и ни с
            // чем не совпадала: выходил почти сплошной круг с торчащей
            // палочкой, а не узнаваемый символ питания. startAngle = -55f
            // с тем же sweepAngle даёт разрыв ровно там, где риска.
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 290f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - glyphRadius, center.y - glyphRadius),
                size = Size(glyphRadius * 2f, glyphRadius * 2f),
            )
            drawLine(
                color = color,
                start = Offset(center.x, center.y - glyphRadius * 1.25f),
                end = Offset(center.x, center.y - glyphRadius * 0.15f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
@Composable
private fun UptimeText(sinceMillis: Long?, color: Color) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMillis) {
        while (sinceMillis != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val text = sinceMillis?.let { formatUptime(now - it) } ?: "--:--"
    Text(text = text, style = HudType.code(13.sp), color = color)
}

private fun formatUptime(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}
