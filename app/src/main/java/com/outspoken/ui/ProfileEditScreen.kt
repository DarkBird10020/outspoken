package com.outspoken.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.outspoken.R
import com.outspoken.profile.PatientProfile
import com.outspoken.profile.parsePeople
import com.outspoken.profile.peopleText
import com.outspoken.profile.splitList
import com.outspoken.ui.theme.InkFaint
import com.outspoken.ui.theme.InkSoft
import com.outspoken.ui.theme.Surfaces
import com.outspoken.ui.theme.dottedCanvas
import com.outspoken.ui.theme.surface
import com.outspoken.ui.theme.type

/**
 * Editing the profile. Not in the design (listed as a design gap): fields in the design's field
 * style, typed by the person's family or carer, not by eye. Lists take one item per line.
 */
@Composable
fun ProfileEditScreen(
    initial: PatientProfile,
    onSave: (PatientProfile) -> Unit,
    onCancel: () -> Unit,
) {
    var fullName by rememberSaveable { mutableStateOf(initial.fullName) }
    var calledName by rememberSaveable { mutableStateOf(initial.calledName) }
    var gender by rememberSaveable { mutableStateOf(initial.gender) }
    var age by rememberSaveable { mutableStateOf(initial.age) }
    var languages by rememberSaveable { mutableStateOf(initial.languages) }
    var livesIn by rememberSaveable { mutableStateOf(initial.livesIn) }
    var work by rememberSaveable { mutableStateOf(initial.work) }
    var likes by rememberSaveable { mutableStateOf(initial.likes.joinToString("\n")) }
    var dislikes by rememberSaveable { mutableStateOf(initial.dislikes.joinToString("\n")) }
    var people by rememberSaveable { mutableStateOf(peopleText(initial.people)) }
    var conditions by rememberSaveable { mutableStateOf(initial.conditions.joinToString("\n")) }
    var allergies by rememberSaveable { mutableStateOf(initial.allergies.joinToString("\n")) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }

    fun fill(p: PatientProfile) {
        fullName = p.fullName; calledName = p.calledName; gender = p.gender; age = p.age
        languages = p.languages; livesIn = p.livesIn; work = p.work
        likes = p.likes.joinToString("\n"); dislikes = p.dislikes.joinToString("\n")
        people = peopleText(p.people); conditions = p.conditions.joinToString("\n")
        allergies = p.allergies.joinToString("\n"); notes = p.notes
    }

    DesignScreen(Modifier.dottedCanvas(), gap = 14.dp, scrolls = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CircleIconButton(R.drawable.ic_back, "Back without saving", onCancel)
            Pill(Surfaces.Glass) { Text("Edit profile", style = type(15, 500)) }
        }
        Text(
            "Written by family or a carer. Everything stays on this phone and only the model here reads it.",
            style = type(14, color = InkSoft, lineHeight = 1.35f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        GlassCard(gap = 12) {
            Text("About me", style = type(15, 500))
            Field("Full name", fullName) { fullName = it }
            Field("Called (what family calls them)", calledName) { calledName = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field("Gender", gender) { gender = it } }
                Box(Modifier.weight(1f)) { Field("Age", age) { age = it } }
            }
            Field("Speaks", languages) { languages = it }
            Field("Lives in", livesIn) { livesIn = it }
            Field("Used to work as", work) { work = it }
            Field("Likes, one per line", likes, lines = 3) { likes = it }
        }
        GlassCard(gap = 12) {
            Text("People close to me", style = type(15, 500))
            Field("One per line, as Relation: Name (Wife: Meera)", people, lines = 4) { people = it }
        }
        GlassCard(gap = 12) {
            Text("Health and care", style = type(15, 500))
            Field("Conditions, one per line", conditions, lines = 2) { conditions = it }
            Field("Allergies, one per line", allergies, lines = 2) { allergies = it }
            Field("Does not like, one per line", dislikes, lines = 2) { dislikes = it }
            Field("Notes", notes, lines = 2) { notes = it }
        }
        PillButton(
            "Save",
            {
                onSave(
                    PatientProfile(
                        fullName = fullName.trim(), calledName = calledName.trim(), gender = gender.trim(), age = age.trim(),
                        languages = languages.trim(), livesIn = livesIn.trim(), work = work.trim(),
                        likes = splitList(likes), dislikes = splitList(dislikes), people = parsePeople(people),
                        conditions = splitList(conditions), allergies = splitList(allergies), notes = notes.trim(),
                    ),
                )
            },
            Modifier.fillMaxWidth(),
            style = Surfaces.StartButton,
            weight = 600,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Fill with sample", { fill(PatientProfile.SAMPLE) }, Modifier.weight(1f), size = 15)
            PillButton("Clear all", { fill(PatientProfile()) }, Modifier.weight(1f), size = 15)
        }
        Text(
            "The sample is a made-up person for trying the app out.",
            style = type(13, color = InkFaint),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = type(13, color = InkSoft))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = (if (lines == 1) 52 else 32 + lines * 22).dp)
                .surface(Surfaces.Field, RoundedCornerShape(if (lines == 1) 26.dp else 22.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = lines == 1,
                textStyle = type(16),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
            )
        }
    }
}
