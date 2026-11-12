@file:OptIn(ExperimentalTextApi::class)

package com.appresso.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.appresso.app.R

// Fraunces: títulos y números destacados. Fuente variable (res/font/fraunces.ttf);
// el eje "opsz" se ajusta al tamaño para que se vea como en Figma.
private fun fraunces(size: TextUnit) = FontFamily(
    Font(
        resId = R.font.fraunces,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.SemiBold.weight),
            FontVariation.opticalSizing(size),
        ),
    )
)

// DM Sans: texto de interfaz, botones y etiquetas. Fuente variable (res/font/dm_sans.ttf).
private fun dmSans(weight: FontWeight) = Font(
    resId = R.font.dm_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DmSans = FontFamily(
    dmSans(FontWeight.Normal),
    dmSans(FontWeight.Medium),
    dmSans(FontWeight.SemiBold),
)

// Escala de la guía de estilo de Figma (Display, Heading, Body, Label, Button, Caption).
val Typography = Typography(
    // Display/Title: Fraunces SemiBold 48/48
    displayLarge = TextStyle(
        fontFamily = fraunces(48.sp), fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = 0.sp,
    ),
    // Heading/H2: Fraunces SemiBold 26/32
    headlineMedium = TextStyle(
        fontFamily = fraunces(26.sp), fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.sp,
    ),
    // Heading/H3: Fraunces SemiBold 20/26
    titleLarge = TextStyle(
        fontFamily = fraunces(20.sp), fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp,
    ),
    // Button/Label: DM Sans SemiBold 16/24
    titleMedium = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    ),
    // Body/Regular: DM Sans Regular 16/24
    bodyLarge = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    ),
    // Body/Small: DM Sans Regular 14/20
    bodyMedium = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    // Label/Medium: DM Sans Medium 14/20
    labelLarge = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    // Caption/Semibold: DM Sans SemiBold 12/16
    labelSmall = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp,
    ),
)
