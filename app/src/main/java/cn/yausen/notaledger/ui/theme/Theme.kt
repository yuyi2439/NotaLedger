package cn.yausen.notaledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00695C)
private val TealLight = Color(0xFF4DB6AC)
private val Amber = Color(0xFFF9A825)
private val ExpenseRed = Color(0xFFD32F2F)
private val IncomeGreen = Color(0xFF2E7D32)

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00251A),
    secondary = Amber,
    onSecondary = Color(0xFF3E2C00),
    secondaryContainer = Color(0xFFFFE9B8),
    onSecondaryContainer = Color(0xFF3E2C00),
    tertiary = Color(0xFF5C6BC0),
    background = Color(0xFFF7F8F7),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7975),
    error = ExpenseRed,
)

private val DarkScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFFFD54F),
    onSecondary = Color(0xFF3E2C00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFE9B8),
    tertiary = Color(0xFF9FA8DA),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF161D1B),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF889390),
    error = Color(0xFFFF8A80),
)

/** Semantic colors the ledger uses consistently: red for spend, green for income. */
object LedgerColors {
    val expense: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else ExpenseRed
    val income: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF81C784) else IncomeGreen
}

@Composable
fun NotaLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
