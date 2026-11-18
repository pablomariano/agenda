package com.pablomariano.agenda

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val agenda by viewModels<PersonalAgendaModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lightBars = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.BLACK)
        enableEdgeToEdge(statusBarStyle = lightBars, navigationBarStyle = lightBars)
        setContent {
            MaterialTheme(colorScheme = AgendaColorScheme, typography = AgendaTypography) {
                if (BuildConfig.AGENDA_API_URL.isEmpty()) TodayScreen() else PersonalAgendaScreen(agenda)
            }
        }
    }
}

internal object AgendaColors {
    val Canvas = Color(0xFFEEF5F3)
    val Surface = Color.White
    val Mint = Color(0xFFDDEFE8)
    val Ink = Color(0xFF112D2B)
    val Muted = Color(0xFF496460)
    val Action = Color(0xFF006A55)
    val Blue = Color(0xFF155C73)
    val Sky = Color(0xFFE2F1F7)
}

private val AgendaColorScheme = lightColorScheme(
    primary = AgendaColors.Action,
    onPrimary = Color.White,
    primaryContainer = AgendaColors.Mint,
    onPrimaryContainer = AgendaColors.Action,
    background = AgendaColors.Canvas,
    onBackground = AgendaColors.Ink,
    surface = AgendaColors.Surface,
    onSurface = AgendaColors.Ink,
    onSurfaceVariant = AgendaColors.Muted,
    surfaceContainerHigh = AgendaColors.Surface,
)

private fun agendaText(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
)

private val AgendaTypography = Typography(
    displaySmall = agendaText(32, 38, FontWeight.Bold),
    headlineSmall = agendaText(24, 30, FontWeight.Bold),
    titleMedium = agendaText(16, 21, FontWeight.Bold),
    bodyMedium = agendaText(14, 20, FontWeight.Normal),
    labelLarge = agendaText(13, 18, FontWeight.Medium),
    labelMedium = agendaText(12, 16, FontWeight.Medium),
)
