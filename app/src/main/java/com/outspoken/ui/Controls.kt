package com.outspoken.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.unit.dp
import com.outspoken.ui.theme.BoxShadow
import com.outspoken.ui.theme.Ink
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.SurfaceStyle
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.rgba
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/** A glass card with the design's 28 dp corners. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    style: SurfaceStyle = Surfaces.Glass,
    gap: Int = 10,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .surface(style, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(gap.dp),
        content = content,
    )
}

/** A small grey label over a value, as in the 2 x 2 tiles of the design. */
@Composable
fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .surface(Surfaces.Glass, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = type(13, color = InkSoft))
        Text(value, style = type(22, 500))
    }
}

/** A full-width pill button: glass, or pink for the main action. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SurfaceStyle = Surfaces.Glass,
    height: Int = 56,
    size: Int = 17,
    weight: Int = 500,
    sidePadding: Int = 0,
) {
    val shape = RoundedCornerShape((height / 2).dp)
    Box(
        modifier
            .height(height.dp)
            .surface(style, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = sidePadding.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = type(size, weight))
    }
}

/** The design's slider: a 10 dp track filled pink up to a white 22 dp knob. */
@Composable
fun DesignSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val fraction = ((value.coerceIn(range) - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val change by rememberUpdatedState(onChange)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = type(15))
            Text(valueText, style = type(15, 600))
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(range), range)
                    setProgress { target ->
                        change(target.coerceIn(range))
                        true
                    }
                }
                .pointerInput(range) {
                    fun at(x: Float) = range.start + (x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start)
                    detectTapGestures { change(at(it.x)) }
                }
                .pointerInput(range) {
                    fun at(x: Float) = range.start + (x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start)
                    detectHorizontalDragGestures { pointer, _ -> change(at(pointer.position.x)) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val track = RoundedCornerShape(5.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .surface(SurfaceStyle(base = rgba(27, 29, 31, 0.1f)), track)
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .surface(SurfaceStyle(layers = listOf(SliderFill)), track)
            )
            Box(
                Modifier
                    .offset(x = (maxWidth - 22.dp) * fraction)
                    .size(22.dp)
                    .surface(SliderKnob, CircleShape)
            )
        }
    }
}

/** A row with a label and an on / off pill, green when on, for the switches the design does not show. */
@Composable
fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type(15), modifier = Modifier.weight(1f))
        PillButton(
            text = if (on) "On" else "Off",
            onClick = { onChange(!on) },
            style = if (on) Surfaces.OfflinePill else Surfaces.Glass,
            height = 44,
            size = 15,
            weight = 600,
            modifier = Modifier.size(width = 72.dp, height = 44.dp),
        )
    }
}

/** Two options side by side in one glass bar; the chosen one is green. */
@Composable
fun Segmented(left: String, right: String, leftChosen: Boolean, onChoose: (left: Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .surface(Surfaces.Glass, RoundedCornerShape(28.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        listOf(true to left, false to right).forEach { (isLeft, text) ->
            val chosen = isLeft == leftChosen
            val shape = RoundedCornerShape(23.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (chosen) Modifier.surface(Surfaces.OfflinePill, shape) else Modifier)
                    .clip(shape)
                    .clickable(role = Role.Tab, onClick = { onChoose(isLeft) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = type(16, if (chosen) 600 else 500, color = if (chosen) Ink else InkSoft))
            }
        }
    }
}

private val SliderFill = Brush.horizontalGradient(listOf(Color(0xFFF8ACC8), Color(0xFFF04E88)))

private val SliderKnob = SurfaceStyle(
    base = Color.White,
    border = BorderStroke(1.dp, rgba(255, 255, 255, 0.95f)),
    shadows = listOf(BoxShadow(y = 4.dp, blur = 10.dp, color = rgba(190, 40, 100, 0.35f))),
)
