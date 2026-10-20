package com.focusrefine.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The logo is shown at this size; it was 38dp with the mark itself only about 20dp across (52dp was bigger than the row needs). */
val BrandLogoSize = 48.dp

/**
 * The app's launcher icon as a round badge. An adaptive icon draws 108dp of artwork but only the centre 72dp is ever
 * visible, so the artwork is scaled up by 1.5 and clipped to the circle; otherwise the mark would float small in the
 * middle of a mostly empty tile (which is what the old 38dp logo looked like).
 */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, size: Dp = BrandLogoSize) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(StudioColors.CardSurfaceHighlight)
            .border(1.5.dp, StudioColors.ActiveViolet.copy(alpha = 0.6f), CircleShape)
            .semantics { contentDescription = "App Logo" }
    ) {
        AppIconImage(packageName = context.packageName, modifier = Modifier.fillMaxSize().scale(1.5f))
    }
}

/** Below this width the header switches to a compact layout so the app name is never cut off. */
private val CompactWidth = 360.dp

/**
 * The title row of the Home screen: logo, app name with the on-device badge beside it (under it on narrow screens), and the Help and Settings buttons.
 *
 * Compact layout (narrow screens): a 44dp logo, a 20sp name and 44dp buttons. With large system text on top of that
 * the "?" button leaves the row, because the app name matters more there; Help is still in the Settings menu.
 */
@Composable
fun HomeHeader(onOpenHelp: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < CompactWidth
        val showHelp = !(compact && fontScale > 1.15f)
        val buttonModifier = if (compact) Modifier.size(44.dp) else Modifier
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandLogo(size = if (compact) 44.dp else BrandLogoSize)
            Spacer(modifier = Modifier.width(if (compact) 10.dp else 8.dp))
            // weight(1f): the name and badge take what is left, so the buttons can never be pushed off a narrow screen.
            // The badge goes beside the name whenever both fit in that width (whatever the screen, display size or font),
            // and under it otherwise, so the name is never cut off.
            TitleBlock(
                modifier = Modifier.weight(1f),
                name = {
                    Text(
                        text = "FocusRefine",
                        fontSize = if (compact) 20.sp else 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                badge = { OnDeviceBadge() }
            )
            if (showHelp) {
                IconButton(onClick = onOpenHelp, modifier = buttonModifier.semantics { contentDescription = "Help" }) {
                    Text(text = "?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = StudioColors.TextPrimary)
                }
            }
            IconButton(onClick = onOpenSettings, modifier = buttonModifier) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = "Settings",
                    tint = StudioColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * The app name with a badge: side by side (centred on each other) when the name's natural width plus the badge fit in
 * the width available, otherwise the badge sits under the name.
 */
@Composable
private fun TitleBlock(name: @Composable () -> Unit, badge: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(content = { name(); badge() }, modifier = modifier) { measurables, constraints ->
        val gap = 6.dp.roundToPx()
        val stackedGap = 3.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val badgeP = measurables[1].measure(loose)
        val nameWanted = measurables[0].maxIntrinsicWidth(constraints.maxHeight)
        if (nameWanted + gap + badgeP.width <= constraints.maxWidth) {
            val nameP = measurables[0].measure(loose.copy(maxWidth = nameWanted))
            val h = maxOf(nameP.height, badgeP.height)
            layout(constraints.constrainWidth(nameP.width + gap + badgeP.width), constraints.constrainHeight(h)) {
                nameP.placeRelative(0, (h - nameP.height) / 2)
                badgeP.placeRelative(nameP.width + gap, (h - badgeP.height) / 2)
            }
        } else {
            val nameP = measurables[0].measure(loose)
            val h = nameP.height + stackedGap + badgeP.height
            layout(constraints.constrainWidth(maxOf(nameP.width, badgeP.width)), constraints.constrainHeight(h)) {
                nameP.placeRelative(0, 0)
                badgeP.placeRelative(0, nameP.height + stackedGap)
            }
        }
    }
}

@Composable
private fun OnDeviceBadge() {
    Surface(
        shape = CircleShape,
        color = StudioColors.GreenSurface,
        border = BorderStroke(1.dp, StudioColors.GreenAccent.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(StudioColors.GreenAccent)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "On Device AI",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = StudioColors.GreenText,
                maxLines = 1
            )
        }
    }
}
