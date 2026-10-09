package com.outspoken.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.outspoken.R

@OptIn(ExperimentalTextApi::class)
private fun urbanist(weight: Int) = Font(
    resId = R.font.urbanist,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Urbanist = FontFamily(urbanist(300), urbanist(400), urbanist(500), urbanist(600))

/** Text style in design units: size in px of the 390 px wide frame, which maps 1:1 to sp. */
fun type(
    size: Int,
    weight: Int = 400,
    color: Color = Ink,
    lineHeight: Float? = null,
    align: TextAlign = TextAlign.Unspecified,
) = TextStyle(
    fontFamily = Urbanist,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    color = color,
    lineHeight = lineHeight?.let { (size * it).sp } ?: TextUnit.Unspecified,
    textAlign = align,
)
