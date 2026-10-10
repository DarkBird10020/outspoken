package com.outspoken.profile

/** Someone close to the person: how they are related, and their name. */
data class Person(val relation: String, val name: String)

/**
 * What the person wants the model to know, so replies sound like them and use the names and
 * facts of their life (owner request). Kept only on this phone, in the app's private folder; it is
 * never written to the run logs. Every field may be empty.
 */
data class PatientProfile(
    val fullName: String = "",
    val calledName: String = "",
    val gender: String = "",
    val age: String = "",
    val languages: String = "",
    val livesIn: String = "",
    val work: String = "",
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val people: List<Person> = emptyList(),
    val conditions: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val notes: String = "",
) {
    /** How many details are filled in, for the "12 details saved" count on the profile. */
    val detailCount: Int
        get() = listOf(fullName, calledName, gender, age, languages, livesIn, work, notes).count { it.isNotBlank() } +
            likes.size + dislikes.size + people.size + conditions.size + allergies.size

    val isEmpty: Boolean get() = detailCount == 0

    /** The name the person goes by: what they are called, else their first name. */
    val shortName: String get() = calledName.ifBlank { fullName.trim().substringBefore(' ') }

    companion object {
        /** A made-up person, the one in the design, for trying the profile out. Not a real patient. */
        val SAMPLE = PatientProfile(
            fullName = "Ramesh Iyer",
            calledName = "Ramu",
            gender = "Male",
            age = "62",
            languages = "English, Hindi",
            livesIn = "Bengaluru",
            work = "School teacher",
            likes = listOf("Chai, no sugar", "Cricket", "Old film songs"),
            dislikes = listOf("Loud TV"),
            people = listOf(
                Person("Wife", "Meera"),
                Person("Son", "Arjun"),
                Person("Friend", "Kabir"),
                Person("Day nurse", "Sister Anita"),
            ),
            conditions = listOf("ALS", "Type 2 diabetes"),
            allergies = listOf("Penicillin", "Peanuts"),
            notes = "Gets cold easily at night.",
        )
    }
}

/**
 * The profile as a few lines for the model's prompt, or null when it is empty. Short on purpose:
 * every word here is read before each answer, and the reply time grows with the prompt.
 */
fun profilePrompt(profile: PatientProfile?): String? {
    if (profile == null || profile.isEmpty) return null
    val p = profile
    val who = buildList {
        if (p.fullName.isNotBlank()) add(p.fullName.trim() + (if (p.calledName.isNotBlank()) " (called ${p.calledName.trim()})" else ""))
        else if (p.calledName.isNotBlank()) add("called ${p.calledName.trim()}")
        if (p.age.isNotBlank()) add("${p.age.trim()} years old")
        if (p.gender.isNotBlank()) add(p.gender.trim().lowercase())
        if (p.livesIn.isNotBlank()) add("lives in ${p.livesIn.trim()}")
        if (p.work.isNotBlank()) add("used to work as ${p.work.trim().lowercase()}")
        if (p.languages.isNotBlank()) add("speaks ${p.languages.trim()}")
    }
    return buildString {
        appendLine("About the Person (use these names and facts when they fit; never invent others):")
        if (who.isNotEmpty()) appendLine("- ${who.joinToString(", ")}.")
        if (p.people.isNotEmpty()) appendLine("- People close to them: ${p.people.joinToString(", ") { "${it.relation.trim().lowercase()} ${it.name.trim()}".trim() }}.")
        if (p.likes.isNotEmpty()) appendLine("- Likes: ${p.likes.joinToString("; ") { it.trim() }}.")
        if (p.dislikes.isNotEmpty()) appendLine("- Dislikes: ${p.dislikes.joinToString("; ") { it.trim() }}.")
        if (p.conditions.isNotEmpty()) appendLine("- Health: ${p.conditions.joinToString("; ") { it.trim() }}.")
        if (p.allergies.isNotEmpty()) appendLine("- Allergic to: ${p.allergies.joinToString("; ") { it.trim() }}. Never suggest these.")
        if (p.notes.isNotBlank()) appendLine("- ${p.notes.trim().removeSuffix(".")}.")
    }.trimEnd()
}

/**
 * Saves the profile as plain text lines, `key=value`, one list item per line, so it needs no
 * library and can be read back by [decodeProfile]. Backslashes and line breaks in values are
 * escaped.
 */
fun encodeProfile(p: PatientProfile): String = buildString {
    fun line(key: String, value: String) {
        if (value.isNotBlank()) appendLine("$key=${escape(value)}")
    }
    line("fullName", p.fullName)
    line("calledName", p.calledName)
    line("gender", p.gender)
    line("age", p.age)
    line("languages", p.languages)
    line("livesIn", p.livesIn)
    line("work", p.work)
    p.likes.forEach { line("like", it) }
    p.dislikes.forEach { line("dislike", it) }
    p.people.forEach { line("person", escapeBar(it.relation) + "|" + escapeBar(it.name)) }
    p.conditions.forEach { line("condition", it) }
    p.allergies.forEach { line("allergy", it) }
    line("notes", p.notes)
}

/** Reads [encodeProfile]'s text; unknown keys and broken lines are skipped. */
fun decodeProfile(text: String): PatientProfile {
    var p = PatientProfile()
    text.lineSequence().forEach { raw ->
        val key = raw.substringBefore('=', "")
        if (key.isEmpty()) return@forEach
        val value = unescape(raw.substringAfter('='))
        p = when (key) {
            "fullName" -> p.copy(fullName = value)
            "calledName" -> p.copy(calledName = value)
            "gender" -> p.copy(gender = value)
            "age" -> p.copy(age = value)
            "languages" -> p.copy(languages = value)
            "livesIn" -> p.copy(livesIn = value)
            "work" -> p.copy(work = value)
            "like" -> p.copy(likes = p.likes + value)
            "dislike" -> p.copy(dislikes = p.dislikes + value)
            "person" -> {
                val (relation, name) = splitBar(value)
                p.copy(people = p.people + Person(relation, name))
            }
            "condition" -> p.copy(conditions = p.conditions + value)
            "allergy" -> p.copy(allergies = p.allergies + value)
            "notes" -> p.copy(notes = value)
            else -> p
        }
    }
    return p
}

private fun escape(value: String) = value.replace("\\", "\\\\").replace("\n", "\\n")

private fun unescape(value: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '\\' && i + 1 < value.length) {
            out.append(if (value[i + 1] == 'n') '\n' else value[i + 1])
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

private fun escapeBar(value: String) = value.replace("|", "/")

private fun splitBar(value: String): Pair<String, String> =
    if ('|' in value) value.substringBefore('|') to value.substringAfter('|') else "" to value

/** One item per line, as typed in the editor, without blanks. Items may hold commas ("Chai, no sugar"). */
fun splitList(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

/** "Wife: Meera" lines as typed in the editor; a line without a colon is a name alone. */
fun parsePeople(text: String): List<Person> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
    if (':' in line) Person(line.substringBefore(':').trim(), line.substringAfter(':').trim()) else Person("", line)
}

fun peopleText(people: List<Person>): String = people.joinToString("\n") { if (it.relation.isBlank()) it.name else "${it.relation}: ${it.name}" }
