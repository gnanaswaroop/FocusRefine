package com.focusrefine.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Default chevron size: large enough to read, small enough to sit beside a 15sp card title. */
private val ChevronSize = 24.dp

/**
 * The expand/collapse indicator for collapsible cards: a vector chevron that points down when the section is open and
 * rotates a quarter turn to point right when it is closed. Use it inside a header row that is itself the tap target
 * (at least 48dp tall); the icon only shows the state and tells screen readers what a tap will do.
 */
@Composable
fun StudioChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = StudioColors.TextSecondary,
    size: Dp = ChevronSize
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 0f else -90f, animationSpec = tween(durationMillis = 180), label = "chevron")
    Icon(
        painter = painterResource(id = R.drawable.ic_chevron_down),
        contentDescription = if (expanded) "Collapse" else "Expand",
        tint = tint,
        modifier = modifier.size(size).rotate(rotation)
    )
}

/** A chevron pointing right, for rows that open another screen. Decorative: the row's own label describes it. */
@Composable
fun StudioNavChevron(
    modifier: Modifier = Modifier,
    tint: Color = StudioColors.TextSecondary,
    size: Dp = ChevronSize
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_chevron_down),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size).rotate(-90f)
    )
}
