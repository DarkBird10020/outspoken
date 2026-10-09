package com.outspoken.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.ui.theme.Ink
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.SurfaceStyle
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/** Full-screen frame used by every designed screen: 24 dp top and bottom, 20 dp sides. */
@Composable
fun DesignScreen(
    background: Modifier,
    gap: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .then(background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
fun Pill(
    style: SurfaceStyle,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .height(48.dp)
            .surface(style, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun DotPill(
    text: String,
    dot: Color,
    style: SurfaceStyle = Surfaces.Glass,
    onLongClick: (() -> Unit)? = null,
) {
    val modifier = if (onLongClick == null) {
        Modifier
    } else {
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
    }
    Pill(style, modifier) {
        Box(
            Modifier
                .size(10.dp)
                .surface(SurfaceStyle(base = dot), CircleShape)
        )
        Text(text, style = type(15, 500))
    }
}

@Composable
fun CircleIconButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .surface(Surfaces.Glass, CircleShape)
            .clip(CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = Ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun BackButton(onClick: () -> Unit) =
    CircleIconButton(R.drawable.ic_back, "Back to conversation", onClick)

/** Lock icon and a short line at the bottom of a screen. */
@Composable
fun FooterNote(text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = InkSoft, modifier = Modifier.size(14.dp))
        Text(text, style = type(13, color = InkSoft))
    }
}

/** Title and grey subtitle block under the top bar. */
@Composable
fun ScreenHeading(title: String, subtitle: String, gap: Dp) {
    Column(
        Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Text(title, style = type(34, 400, lineHeight = 1.08f))
        Text(subtitle, style = type(16, color = InkSoft, lineHeight = 1.35f))
    }
}
