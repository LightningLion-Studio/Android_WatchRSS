package com.lightningstudio.watchrss.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lightningstudio.watchrss.R

private val WatchFontFamily = FontFamily(
    Font(R.font.watch_sans, weight = FontWeight.Normal),
    Font(R.font.watch_sans, weight = FontWeight.Medium),
    Font(R.font.watch_sans, weight = FontWeight.SemiBold)
)

private fun watchTextStyle(
    fontWeight: FontWeight,
    fontSize: Int,
    lineHeight: Int
) = TextStyle(
    fontFamily = WatchFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp
)

val Typography = Typography(
    displayLarge = watchTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 38,
        lineHeight = 44
    ),
    displayMedium = watchTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 34,
        lineHeight = 40
    ),
    displaySmall = watchTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 29,
        lineHeight = 35
    ),
    headlineLarge = watchTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 31,
        lineHeight = 38
    ),
    headlineMedium = watchTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26,
        lineHeight = 31
    ),
    headlineSmall = watchTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24,
        lineHeight = 29
    ),
    titleLarge = watchTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 23,
        lineHeight = 28
    ),
    titleMedium = watchTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 21,
        lineHeight = 26
    ),
    titleSmall = watchTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18,
        lineHeight = 22
    ),
    bodyLarge = watchTextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17,
        lineHeight = 21
    ),
    bodyMedium = watchTextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14,
        lineHeight = 17
    ),
    bodySmall = watchTextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12,
        lineHeight = 14
    ),
    labelLarge = watchTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14,
        lineHeight = 17
    ),
    labelMedium = watchTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12,
        lineHeight = 14
    ),
    labelSmall = watchTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 9,
        lineHeight = 11
    )
)

val ActionButtonTextStyle = TextStyle(
    fontFamily = WatchFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 17.sp,
    lineHeight = 23.sp,
    letterSpacing = 0.sp
)
