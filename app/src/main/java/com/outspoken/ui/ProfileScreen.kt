package com.outspoken.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.profile.PatientProfile
import com.outspoken.ui.theme.InkFaint
import com.outspoken.ui.theme.InkOnHelp
import com.outspoken.ui.theme.InkOnModel
import com.outspoken.ui.theme.SurfaceStyle
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.rgba
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/**
 * Profile (design of 2026-10-10): the person's name and photo, "About me" with what they like,
 * and the people close to them. Health (conditions, allergies, notes) is not in the design and
 * sits in a glass card between the two (listed as a design gap). All of it goes to the model.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: PatientProfile,
    photo: ImageBitmap?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
    onPhoto: () -> Unit,
) {
    DesignScreen(Modifier.dottedCanvas(), gap = 16.dp, scrolls = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(R.drawable.ic_back, "Back to talking", onBack)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleIconButton(R.drawable.ic_settings, "Settings", onSettings)
                CircleIconButton(R.drawable.ic_edit, "Edit profile", onEdit)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val name = profile.fullName.trim().ifEmpty { "Add a name" }
                Text(name.replaceFirst(' ', '\n'), style = type(34, 400, color = if (profile.fullName.isBlank()) InkFaint else com.outspoken.ui.theme.Ink, lineHeight = 1.08f))
                Text(
                    "Replies are written the way ${pronoun(profile.gender)} would say them.",
                    style = type(14, color = InkFaint, lineHeight = 1.35f),
                    modifier = Modifier.widthIn(max = 170.dp),
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${profile.detailCount}", style = type(40, 300, lineHeight = 1f))
                    Text(if (profile.detailCount == 1) "detail\nsaved" else "details\nsaved", style = type(13, color = InkFaint), modifier = Modifier.padding(top = 4.dp))
                }
            }
            PhotoFrame(photo, onPhoto)
        }
        AboutCard(profile, onEdit)
        HealthCard(profile)
        PeopleCard(profile, onEdit)
    }
}

/** "he", "she" or "they", from what the profile says. */
internal fun pronoun(gender: String): String = when (gender.trim().lowercase()) {
    "male", "man", "m" -> "he"
    "female", "woman", "f" -> "she"
    else -> "they"
}

@Composable
private fun PhotoFrame(photo: ImageBitmap?, onPhoto: () -> Unit) {
    val shape = RoundedCornerShape(66.dp)
    Box(
        Modifier
            .size(width = 132.dp, height = 176.dp)
            .surface(Surfaces.ProfilePhoto, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = "Add photo", onClick = onPhoto),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (photo != null) {
            Image(photo, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // The design's white head-and-shoulders outline.
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val sx = size.width / 132f
                val sy = size.height / 150f
                drawCircle(Color.White, radius = 26f * sx, center = Offset(66f * sx, 58f * sy))
                val body = Path().apply {
                    moveTo(14f * sx, 150f * sy)
                    cubicTo(14f * sx, 120f * sy, 36f * sx, 100f * sy, 66f * sx, 100f * sy)
                    cubicTo(96f * sx, 100f * sy, 118f * sx, 120f * sy, 118f * sx, 150f * sy)
                    close()
                }
                drawPath(body, Color.White)
            }
        }
        Text(
            if (photo == null) "Add photo" else "Change photo",
            style = type(12, 600),
            modifier = Modifier
                .padding(bottom = 12.dp)
                .surface(PhotoChip, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private val PhotoChip = SurfaceStyle(
    base = rgba(255, 255, 255, 0.75f),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutCard(profile: PatientProfile, onEdit: () -> Unit) {
    val facts = listOfNotNull(
        profile.calledName.fact("Called"),
        profile.gender.fact("Gender"),
        profile.age.fact("Age"),
        profile.languages.fact("Speaks"),
        profile.livesIn.fact("Lives in"),
        profile.work.fact("Used to work as"),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .surface(Surfaces.Model, RoundedCornerShape(30.dp))
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("About me", style = type(15, 500))
        if (facts.isEmpty()) {
            Text("Tap the pencil to add what the model should know.", style = type(15, color = InkOnModel))
        } else {
            FactGrid(facts, InkOnModel)
        }
        val likes = profile.likes
        if (likes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                likes.forEach { Chip(it) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthCard(profile: PatientProfile) {
    if (profile.conditions.isEmpty() && profile.allergies.isEmpty() && profile.dislikes.isEmpty() && profile.notes.isBlank()) return
    GlassCard(gap = 14) {
        Text("Health and care", style = type(15, 500))
        if (profile.conditions.isNotEmpty()) Labelled("Conditions", profile.conditions)
        if (profile.allergies.isNotEmpty()) Labelled("Allergies", profile.allergies)
        if (profile.dislikes.isNotEmpty()) Labelled("Does not like", profile.dislikes)
        if (profile.notes.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Notes", style = type(12, color = InkFaint))
                Text(profile.notes, style = type(16, 500))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Labelled(label: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = type(12, color = InkFaint))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { Chip(it) }
        }
    }
}

@Composable
private fun PeopleCard(profile: PatientProfile, onEdit: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .surface(Surfaces.Pink, RoundedCornerShape(30.dp))
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("People close to me", style = type(15, 500))
            Text(
                "Add",
                style = type(13, 600),
                modifier = Modifier
                    .surface(AddChip, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        if (profile.people.isEmpty()) {
            Text("Family, friends and carers, so replies can use their names.", style = type(15, color = InkOnHelp))
        } else {
            FactGrid(profile.people.map { it.relation.ifBlank { "Close to me" } to it.name }, InkOnHelp)
        }
        FooterNote("Stays on this phone. Only the model here reads it.")
    }
}

private val AddChip = SurfaceStyle(
    base = rgba(255, 255, 255, 0.7f),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
)

private fun String.fact(label: String) = trim().takeIf { it.isNotEmpty() }?.let { label to it }

/** Label over value, two to a row; the right-hand one aligned right, as in the design. */
@Composable
private fun FactGrid(facts: List<Pair<String, String>>, labelColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        facts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEachIndexed { i, (label, value) ->
                    val align = if (i == 1) TextAlign.End else TextAlign.Start
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = if (i == 1) Alignment.End else Alignment.Start) {
                        Text(label, style = type(12, color = labelColor, align = align))
                        Text(value, style = type(18, 500, align = align))
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        style = type(14, 500),
        modifier = Modifier
            .surface(Surfaces.Chip, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
