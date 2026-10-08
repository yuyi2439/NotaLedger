package cn.yausen.notaledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yausen.notaledger.util.CalcOp
import cn.yausen.notaledger.util.CalculatorState

/** One key of the amount keypad. */
sealed interface CalcKey {
    data class Digit(val value: Int) : CalcKey
    data object Decimal : CalcKey
    data class Op(val op: CalcOp) : CalcKey

    /** Saves the amount the expression stands for. */
    data object Save : CalcKey
}

/**
 * Four rows of digits with the arithmetic column on the left and save in the
 * bottom-right corner. Deleting a character is not a key of its own any more: the
 * backspace beside the amount serves that, which is what keeps the grid at four
 * rows instead of five.
 */
private val keypadRows: List<List<CalcKey>> = listOf(
    listOf(CalcKey.Op(CalcOp.DIVIDE), CalcKey.Digit(1), CalcKey.Digit(2), CalcKey.Digit(3)),
    listOf(CalcKey.Op(CalcOp.MULTIPLY), CalcKey.Digit(4), CalcKey.Digit(5), CalcKey.Digit(6)),
    listOf(CalcKey.Op(CalcOp.SUBTRACT), CalcKey.Digit(7), CalcKey.Digit(8), CalcKey.Digit(9)),
    listOf(CalcKey.Op(CalcOp.ADD), CalcKey.Decimal, CalcKey.Digit(0), CalcKey.Save),
)

/**
 * Fixed grid: ten digits, the decimal point, the four operators and save, one
 * cell each. Save sits in the bottom-right corner rather than on a row of its
 * own, so the whole keypad fits above the thumb without scrolling.
 *
 * [keyHeight] comes from the panel, which is what decides how tall a key may be;
 * only the floor lives here, so a squeezed panel still hands out tap targets big
 * enough to hit.
 */
@Composable
fun AmountKeypad(
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyHeight: Dp = 52.dp,
) {
    val spacing = 8.dp
    val rowHeight = keyHeight.coerceAtLeast(44.dp)
    val operatorColor = MaterialTheme.colorScheme.secondaryContainer
    val operatorText = MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        keypadRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        weight = 1f,
                        height = rowHeight,
                        enabled = enabled,
                        operatorColor = operatorColor,
                        operatorText = operatorText,
                        onKey = onKey,
                    )
                }
            }
        }
    }
}

/** One key of the grid, sized by [weight] so every cell stays the same width. */
@Composable
private fun RowScope.KeyButton(
    key: CalcKey,
    weight: Float,
    height: Dp,
    enabled: Boolean,
    operatorColor: Color,
    operatorText: Color,
    onKey: (CalcKey) -> Unit,
) {
    val buttonModifier = Modifier
        .weight(weight)
        .height(height)
    val shape = RoundedCornerShape(14.dp)

    when (key) {
        is CalcKey.Save -> Button(
            onClick = { onKey(key) },
            enabled = enabled,
            shape = shape,
            // A button's default 24dp of side padding does not fit two characters
            // into one cell of a 360dp-wide phone, so the label sets its own.
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = buttonModifier,
        ) {
            Text("保存", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }

        is CalcKey.Op -> FilledTonalButton(
            onClick = { onKey(key) },
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = operatorColor,
                contentColor = operatorText,
            ),
            modifier = buttonModifier,
        ) {
            Text("${key.op.symbol}", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }

        is CalcKey.Digit -> FilledTonalButton(
            onClick = { onKey(key) },
            enabled = enabled,
            shape = shape,
            modifier = buttonModifier,
        ) {
            Text("${key.value}", fontSize = 22.sp)
        }

        CalcKey.Decimal -> FilledTonalButton(
            onClick = { onKey(key) },
            enabled = enabled,
            shape = shape,
            modifier = buttonModifier,
        ) {
            Text(".", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The amount box above the keys. It renders the expression string the keypad
 * edits, with the number that expression stands for shown beside it: `35 + 2.5`
 * on the left, `= 37.5` on the right. The result is derived from the string on
 * every recomposition, so it is always current and never stored — and a suffix
 * that would not fit never appears.
 *
 * The backspace on the right of the box trims the expression one character at a
 * time; the keypad itself has no room for it any more.
 *
 * The box is the height of the row it sits in — empty, mid-expression or resolved.
 * Its content is the height of the 28sp figure next to 10dp of padding, so the
 * backspace is sized to that same 36dp: a taller trailing icon would make the box
 * outgrow its row on the first key press.
 */
@Composable
fun AmountDisplay(
    state: CalculatorState,
    accent: Color,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Row(
            // The row the box sits in owns the height and the box fills it. Its own
            // content is the 28sp figure with 10dp of padding either side — the 36dp
            // the backspace is sized to, so nothing here can change that height.
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "¥",
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                val expression = state.pretty
                if (CalculatorState.pendingOpCount(state.expression) > 0) {
                    // A calculation in progress: the whole expression, spaced for
                    // reading, a size down because it has to fit.
                    Text(
                        text = expression,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        maxLines = 1,
                    )
                } else {
                    EntryFigure(text = state.display, accent = accent)
                }
            }

            val result = state.result
            if (result.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = result,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            if (state.isClearable) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onBackspace() }
                        // 20dp icon + 8dp twice = the 36dp content height, so
                        // appearing on the first press cannot change the height.
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "退格",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * The amount being typed: the yuan in full size with the `.fen` tail one size
 * smaller, which keeps the leading digits reading first without shrinking the
 * decimals into an illegible smear. A bare zero stays muted.
 */
@Composable
private fun EntryFigure(text: String, accent: Color) {
    val dot = text.indexOf('.')
    val whole = if (dot < 0) text else text.substring(0, dot)
    val fraction = if (dot < 0) "" else text.substring(dot)
    val color = if (whole == "0" && fraction.isEmpty()) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    } else {
        accent
    }

    Text(
        text = whole,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
    )
    if (fraction.isNotEmpty()) {
        Text(
            text = fraction,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * The record's day and clock time as one short chip, e.g. `今天 22:13`. The day
 * opens the date picker and the time its own picker, so the stamp shown is the
 * stamp that will be saved. It carries no weekday and no icon: it sits beside the
 * amount box rather than on a line of its own, and a line of its own is what used
 * to push the keypad into scrolling.
 */
@Composable
fun DateTimeChip(
    dateText: String,
    timeText: String,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier,
    ) {
        Row(
            // Fills whatever height the caller gives the chip — the row it sits in
            // is sized by the amount box beside it, so the two end up flush.
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPickDate)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            // A hairline instead of a gap: one compact chip, two tap targets.
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPickTime)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}
