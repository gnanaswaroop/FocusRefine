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
import androidx.compose.foundation.layout.offset
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
 * The title row of the Home screen: logo, app name, and the Help and Settings buttons.
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
            // weight(1f): the name takes what is left, so the buttons can never be pushed off a narrow screen.
            Text(
                text = "FocusRefine",
                fontSize = if (compact) 20.sp else 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (showHelp) {
                IconButton(onClick = onOpenHelp, modifier = buttonModifier.semantics { contentDescription = "Help" }) {
                    Text(text = "?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = StudioColors.TextPrimary)
                }
            }
            // Pulled towards the Help button so the two read as a pair; the touch target stays 48dp.
            val settingsModifier = if (showHelp) buttonModifier.offset(x = (-8).dp) else buttonModifier
            IconButton(onClick = onOpenSettings, modifier = settingsModifier) {
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
