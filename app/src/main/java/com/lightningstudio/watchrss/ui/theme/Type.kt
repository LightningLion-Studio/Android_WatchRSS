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

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = WatchFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 31.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = WatchFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 31.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = WatchFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 29.sp
    ),
    titleMedium = TextStyle(
        fontFamily = WatchFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = WatchFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = WatchFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 17.sp
    ),
    bodySmall = TextStyle(
        fontFamily = WatchFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = WatchFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 11.sp
    )
)

val ActionButtonTextStyle = TextStyle(
    fontFamily = WatchFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 17.sp,
    lineHeight = 23.sp,
    letterSpacing = 0.sp
)
