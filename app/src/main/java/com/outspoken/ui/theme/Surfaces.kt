package com.outspoken.ui.theme

import android.graphics.Matrix
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * A CSS `radial-gradient(rx% ry% at cx% cy%, ...)` layer. Centre and radii are fractions of the
 * box, so the gradient is an ellipse that stretches with it, as in the design files.
 */
class RadialLayer(
    private val centerX: Float,
    private val centerY: Float,
    private val radiusX: Float,
    private val radiusY: Float,
    private val stops: List<Pair<Float, Color>>,
) : ShaderBrush() {

    override fun createShader(size: Size): Shader {
        val center = Offset(centerX * size.width, centerY * size.height)
        val rx = (radiusX * size.width).coerceAtLeast(0.01f)
        val ry = (radiusY * size.height).coerceAtLeast(0.01f)
        return RadialGradientShader(
            center = center,
            radius = rx,
            colors = stops.map { it.second },
            colorStops = stops.map { it.first },
        ).apply {
            setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, center.x, center.y) })
        }
    }
}

/** A CSS `box-shadow`. */
data class BoxShadow(
    val y: Dp,
    val blur: Dp,
    val spread: Dp = 0.dp,
    val color: Color,
    val inset: Boolean = false,
) {
    fun toShadow() = Shadow(radius = blur, color = color, spread = spread, offset = DpOffset(0.dp, y))
}

/** Fill, border and shadows of one surface. Layers are listed top first, as in CSS. */
class SurfaceStyle(
    val base: Color = Color.Transparent,
    val layers: List<Brush> = emptyList(),
    val border: BorderStroke? = null,
    val shadows: List<BoxShadow> = emptyList(),
)

fun Modifier.surface(style: SurfaceStyle, shape: Shape): Modifier {
    var modifier = this
    style.shadows.filterNot { it.inset }.forEach { modifier = modifier.dropShadow(shape, it.toShadow()) }
    modifier = modifier.background(style.base, shape)
    style.layers.asReversed().forEach { modifier = modifier.background(it, shape) }
    style.shadows.filter { it.inset }.forEach { modifier = modifier.innerShadow(shape, it.toShadow()) }
    style.border?.let { modifier = modifier.border(it, shape) }
    return modifier
}

/** The light grey screen background with an 18 dp dot grid. */
fun Modifier.dottedCanvas(): Modifier = background(Canvas).drawWithCache {
    val step = 18.dp.toPx()
    val dot = 1.dp.toPx()
    val width = size.width
    val height = size.height
    val points = buildList {
        var y = step / 2
        while (y < height) {
            var x = step / 2
            while (x < width) {
                add(Offset(x, y))
                x += step
            }
            y += step
        }
    }
    onDrawBehind {
        drawPoints(points, PointMode.Points, CanvasDot, strokeWidth = dot * 2, cap = StrokeCap.Round)
    }
}

private fun pinkGlow(x: Float, y: Float, rx: Float, ry: Float) = RadialLayer(
    x, y, rx, ry,
    listOf(
        0f to Color(0xFFF04E88),
        0.34f to rgba(243, 116, 164, 0.92f),
        0.62f to rgba(248, 172, 200, 0.6f),
        0.88f to rgba(252, 224, 234, 0f),
    ),
)

private val PeachCorner = RadialLayer(
    0.12f, 0f, 0.75f, 0.7f,
    listOf(0f to rgba(255, 226, 214, 0.95f), 0.72f to rgba(255, 226, 214, 0f)),
)

private val WhiteTopEdge = BoxShadow(y = 1.dp, blur = 0.dp, color = Color.White, inset = true)

object Surfaces {

    val Glass = SurfaceStyle(
        layers = listOf(Brush.verticalGradient(listOf(rgba(255, 255, 255, 0.85f), rgba(255, 255, 255, 0.5f)))),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.95f)),
        shadows = listOf(
            BoxShadow(y = 14.dp, blur = 28.dp, spread = (-12).dp, color = rgba(80, 90, 100, 0.22f)),
            WhiteTopEdge,
        ),
    )

    /** The highlighted card. */
    val Pink = SurfaceStyle(
        base = Color(0xFFFBE6EE),
        layers = listOf(pinkGlow(0.58f, 0.82f, 0.85f, 0.95f), PeachCorner),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.75f)),
        shadows = listOf(
            BoxShadow(y = 0.dp, blur = 22.dp, spread = 6.dp, color = rgba(255, 255, 255, 0.6f), inset = true),
            BoxShadow(y = 22.dp, blur = 36.dp, spread = (-14).dp, color = rgba(236, 72, 134, 0.55f)),
        ),
    )

    val BlinkBadge = SurfaceStyle(
        base = rgba(255, 255, 255, 0.6f),
        border = BorderStroke(2.dp, Color.White),
        shadows = listOf(BoxShadow(y = 8.dp, blur = 18.dp, color = rgba(190, 40, 100, 0.28f))),
    )

    val Star = SurfaceStyle(
        base = Color(0xFFFBE6EE),
        layers = listOf(
            RadialLayer(
                0.5f, 0.62f, 0.75f, 0.75f,
                listOf(
                    0f to Color(0xFFF04E88),
                    0.45f to rgba(243, 116, 164, 0.9f),
                    0.78f to rgba(250, 200, 218, 0.6f),
                    1f to rgba(252, 228, 236, 0f),
                ),
            ),
        ),
        border = BorderStroke(2.dp, Color.White),
        shadows = listOf(
            BoxShadow(y = 0.dp, blur = 16.dp, spread = 4.dp, color = rgba(255, 255, 255, 0.55f), inset = true),
            BoxShadow(y = 22.dp, blur = 36.dp, spread = (-12).dp, color = rgba(236, 72, 134, 0.55f)),
        ),
    )

    val StartButton = SurfaceStyle(
        base = Color(0xFFFBE0EA),
        layers = listOf(
            RadialLayer(
                0.5f, 0.95f, 0.7f, 1.7f,
                listOf(
                    0f to Color(0xFFF04E88),
                    0.4f to rgba(243, 116, 164, 0.92f),
                    0.72f to rgba(248, 180, 206, 0.7f),
                    1f to rgba(252, 224, 234, 0f),
                ),
            ),
        ),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.8f)),
        shadows = listOf(
            BoxShadow(y = 0.dp, blur = 14.dp, spread = 4.dp, color = rgba(255, 255, 255, 0.5f), inset = true),
            BoxShadow(y = 20.dp, blur = 32.dp, spread = (-12).dp, color = rgba(236, 72, 134, 0.55f)),
        ),
    )

    val Green = SurfaceStyle(
        base = Color(0xFFEDF3E2),
        layers = listOf(
            RadialLayer(
                0.55f, 0.8f, 0.85f, 0.95f,
                listOf(
                    0f to Color(0xFF2E9B48),
                    0.34f to rgba(86, 176, 92, 0.92f),
                    0.62f to rgba(160, 210, 128, 0.6f),
                    0.88f to rgba(226, 238, 196, 0f),
                ),
            ),
            RadialLayer(
                0.14f, 0f, 0.75f, 0.7f,
                listOf(0f to rgba(232, 238, 170, 0.95f), 0.72f to rgba(232, 238, 170, 0f)),
            ),
        ),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.75f)),
        shadows = listOf(
            BoxShadow(y = 0.dp, blur = 22.dp, spread = 6.dp, color = rgba(255, 255, 255, 0.6f), inset = true),
            BoxShadow(y = 22.dp, blur = 36.dp, spread = (-14).dp, color = rgba(52, 150, 70, 0.55f)),
        ),
    )

    val OfflinePill = SurfaceStyle(
        layers = listOf(
            RadialLayer(
                0.5f, 0.7f, 1.2f, 1.8f,
                listOf(0f to Color(0xFF5C9E7B), 0.55f to Color(0xFF86BB9C), 1f to Color(0xFFC4DDCC)),
            ),
        ),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.8f)),
        shadows = listOf(
            BoxShadow(y = 0.dp, blur = 12.dp, spread = 3.dp, color = rgba(255, 255, 255, 0.45f), inset = true),
            BoxShadow(y = 14.dp, blur = 24.dp, spread = (-10).dp, color = rgba(70, 140, 100, 0.5f)),
        ),
    )

    val Model = SurfaceStyle(
        base = Color(0xFFECE9F5),
        layers = listOf(
            RadialLayer(
                0.72f, 0.48f, 0.8f, 0.85f,
                listOf(
                    0f to Color(0xFF9F9BE0),
                    0.36f to rgba(176, 170, 230, 0.9f),
                    0.64f to rgba(206, 198, 238, 0.55f),
                    0.9f to rgba(232, 228, 244, 0f),
                ),
            ),
            RadialLayer(
                0.08f, 0.78f, 0.7f, 0.8f,
                listOf(0f to rgba(232, 176, 214, 0.9f), 0.72f to rgba(232, 176, 214, 0f)),
            ),
        ),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.75f)),
        shadows = listOf(
            BoxShadow(y = 0.dp, blur = 28.dp, spread = 10.dp, color = rgba(255, 255, 255, 0.6f), inset = true),
            BoxShadow(y = 24.dp, blur = 40.dp, spread = (-16).dp, color = rgba(120, 112, 214, 0.55f)),
        ),
    )

    /** The dark box the live camera sits in (eye check and calibration). */
    val Camera = SurfaceStyle(
        base = Color(0xFF1B1D1F),
        layers = listOf(
            RadialLayer(
                0.5f, 0.3f, 0.9f, 0.8f,
                listOf(0f to Color(0xFF4A4F54), 0.6f to Color(0xFF2A2D31), 1f to Color(0xFF1B1D1F)),
            ),
        ),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.75f)),
        shadows = listOf(BoxShadow(y = 18.dp, blur = 30.dp, spread = (-14).dp, color = rgba(40, 45, 50, 0.5f))),
    )

    /** The profile photo frame (design of the profile page). */
    val ProfilePhoto = SurfaceStyle(
        base = Color(0xFFEDF3E2),
        layers = listOf(
            RadialLayer(
                0.5f, 0.45f, 0.9f, 0.7f,
                listOf(
                    0f to Color(0xFF2E9B48),
                    0.38f to rgba(86, 176, 92, 0.85f),
                    0.7f to rgba(190, 222, 180, 0.6f),
                    1f to rgba(240, 244, 236, 0.9f),
                ),
            ),
        ),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.9f)),
        shadows = listOf(
            BoxShadow(y = 0.dp, blur = 22.dp, spread = 6.dp, color = rgba(255, 255, 255, 0.55f), inset = true),
            BoxShadow(y = 24.dp, blur = 36.dp, spread = (-16).dp, color = rgba(52, 150, 70, 0.55f)),
        ),
    )

    /** A text field: white, with a soft inner shadow. */
    val Field = SurfaceStyle(
        base = rgba(255, 255, 255, 0.7f),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.95f)),
        shadows = listOf(BoxShadow(y = 2.dp, blur = 6.dp, color = rgba(80, 90, 100, 0.12f), inset = true)),
    )

    val Chip = SurfaceStyle(
        base = rgba(255, 255, 255, 0.6f),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.9f)),
    )

    /** Full-screen background of the help alert. */
    val HelpBackground = SurfaceStyle(
        base = Color(0xFFFBE1EA),
        layers = listOf(
            RadialLayer(
                0.5f, 0.42f, 0.85f, 0.55f,
                listOf(
                    0f to Color(0xFFF04E88),
                    0.38f to rgba(243, 116, 164, 0.9f),
                    0.68f to rgba(248, 180, 206, 0.5f),
                    0.92f to rgba(252, 228, 236, 0f),
                ),
            ),
            RadialLayer(
                1f, 1f, 0.7f, 0.45f,
                listOf(0f to rgba(170, 60, 120, 0.5f), 0.75f to rgba(170, 60, 120, 0f)),
            ),
            RadialLayer(
                0f, 0f, 0.7f, 0.4f,
                listOf(0f to rgba(255, 226, 214, 0.95f), 0.75f to rgba(255, 226, 214, 0f)),
            ),
        ),
    )

    val HelpGlass = SurfaceStyle(
        base = rgba(255, 255, 255, 0.5f),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.85f)),
        shadows = listOf(
            BoxShadow(y = 12.dp, blur = 30.dp, color = rgba(120, 40, 75, 0.18f)),
            BoxShadow(y = 1.dp, blur = 0.dp, color = rgba(255, 255, 255, 0.9f), inset = true),
        ),
    )

    val HelpBellRing = SurfaceStyle(
        base = rgba(255, 255, 255, 0.45f),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.9f)),
        shadows = listOf(
            BoxShadow(y = 20.dp, blur = 50.dp, color = rgba(120, 40, 75, 0.28f)),
            BoxShadow(y = 1.dp, blur = 0.dp, color = rgba(255, 255, 255, 0.9f), inset = true),
        ),
    )

    val HelpBell = SurfaceStyle(
        base = rgba(255, 255, 255, 0.8f),
        border = BorderStroke(2.dp, Color.White),
        shadows = listOf(BoxShadow(y = 10.dp, blur = 24.dp, color = rgba(190, 40, 100, 0.3f))),
    )

    val HelpMute = SurfaceStyle(
        base = rgba(255, 255, 255, 0.7f),
        border = BorderStroke(1.dp, rgba(255, 255, 255, 0.9f)),
    )

    val HelpDismiss = SurfaceStyle(
        base = rgba(255, 255, 255, 0.88f),
        border = BorderStroke(1.dp, Color.White),
        shadows = listOf(
            BoxShadow(y = 18.dp, blur = 40.dp, color = rgba(120, 40, 75, 0.3f)),
            WhiteTopEdge,
        ),
    )
}
