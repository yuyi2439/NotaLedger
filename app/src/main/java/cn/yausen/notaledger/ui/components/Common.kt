package cn.yausen.notaledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yausen.notaledger.util.CategoryPalette

/**
 * Round colored badge with a category icon. The tint follows the category color
 * so the tinted background and the glyph stay visually linked.
 */
@Composable
fun CategoryBadge(
    icon: Int,
    colorIndex: Int,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val color = CategoryPalette.colorAt(colorIndex)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CategoryIcons.vectorAt(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

/** Month switcher with the arrows a ledger always needs. */
@Composable
fun MonthSelector(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton("‹", onPrevious)
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        ArrowButton("›", onNext)
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

@Composable
private fun ArrowButton(glyph: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = glyph, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small labelled figure used in the summary card. */
@Composable
fun AmountColumn(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            // Without an explicit colour the label keeps its on-surface default,
            // which is unreadable on a coloured container.
            color = color.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = cn.yausen.notaledger.util.Money.format(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** Empty-state block so an empty month never looks broken. */
@Composable
fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Inbox,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Section header inside scrollable lists. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

val CardShape = RoundedCornerShape(16.dp)

/**
 * Minimal wrapping row. Compose's own FlowRow is still experimental and its
 * availability shifts between versions, so this keeps the category grids
 * working on the plain layout primitives.
 */
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    verticalSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val hSpace = horizontalSpacing.roundToPx()
        val vSpace = verticalSpacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }

        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = 0
        val positions = mutableListOf<Pair<Int, Int>>()

        placeables.forEach { placeable ->
            if (rowWidth > 0 && rowWidth + hSpace + placeable.width > constraints.maxWidth) {
                totalHeight += rowHeight + vSpace
                rowWidth = 0
                rowHeight = 0
            }
            positions += (rowWidth + if (rowWidth > 0) hSpace else 0) to totalHeight
            rowWidth += (if (rowWidth > 0) hSpace else 0) + placeable.width
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        totalHeight += rowHeight

        layout(constraints.maxWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                val (x, y) = positions[index]
                placeable.placeRelative(x, y)
            }
        }
    }
}
