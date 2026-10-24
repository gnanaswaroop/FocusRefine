package com.focusrefine.app

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

object StudioColors {
    val DarkBackground = Color(0xFF0F0E17)
    val StudioSurface = Color(0xFF181625)
    val CardSurface = Color(0xFF201D33)
    val CardSurfaceHighlight = Color(0xFF292540)
    val StudioBorder = Color(0xFF352F52)
    val PrimaryViolet = Color(0xFF8B5CF6)
    val ActiveViolet = Color(0xFF7C3AED)
    val ActiveVioletLight = Color(0xFFA78BFA)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextMuted = Color(0xFF6B7280)

    val GreenAccent = Color(0xFF22C55E)
    val GreenSurface = Color(0xFF14532D)
    val GreenText = Color(0xFF4ADE80)

    val RedAccent = Color(0xFFEF4444)
    val RedSurface = Color(0xFF451A1A)
    val RedText = Color(0xFFF87171)

    val GoldAccent = Color(0xFFF59E0B)
    val GoldSurface = Color(0xFF452A1A)
    val GoldText = Color(0xFFFBBF24)
}

val StudioColorScheme = darkColorScheme(
    primary = StudioColors.PrimaryViolet,
    onPrimary = Color.White,
    primaryContainer = StudioColors.ActiveViolet,
    onPrimaryContainer = Color.White,
    secondary = StudioColors.ActiveVioletLight,
    onSecondary = Color.White,
    background = StudioColors.DarkBackground,
    onBackground = StudioColors.TextPrimary,
    surface = StudioColors.StudioSurface,
    onSurface = StudioColors.TextPrimary,
    surfaceVariant = StudioColors.CardSurface,
    onSurfaceVariant = StudioColors.TextSecondary,
    outline = StudioColors.StudioBorder,
    error = StudioColors.RedAccent,
    onError = Color.White
)

@Composable
fun FocusRefineTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    val savedThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    val isDark = when (savedThemeMode) {
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_YES -> true
        else -> isSystemInDarkTheme()
    }

    // The app is always dark, so the system bar icons must be light whatever the phone's own theme is.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = if (isDark) StudioColorScheme else darkColorScheme(
            background = StudioColors.DarkBackground,
            surface = StudioColors.StudioSurface,
            surfaceVariant = StudioColors.CardSurface,
            primary = StudioColors.PrimaryViolet
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp)
        )
    ) {
        // From target SDK 35 Android draws apps edge to edge, so every screen would sit under the status bar, the
        // navigation bar and the keyboard. Paint the background behind the bars, then keep the content clear of them
        // here once, for all screens. Where the system already fits the window (older targets) these insets are zero.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            content()
        }
    }
}

@Composable
fun StudioCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = BorderStroke(1.dp, StudioColors.StudioBorder),
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = StudioColors.CardSurface,
                contentColor = StudioColors.TextPrimary
            ),
            border = border,
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = StudioColors.CardSurface,
                contentColor = StudioColors.TextPrimary
            ),
            border = border,
            content = content
        )
    }
}

@Composable
fun PillBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = StudioColors.CardSurfaceHighlight,
    contentColor: Color = StudioColors.TextPrimary,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun StudioSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = StudioColors.CardSurfaceHighlight,
        border = BorderStroke(1.dp, StudioColors.StudioBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (isSelected) StudioColors.ActiveViolet else Color.Transparent)
                        .clickable { onOptionSelected(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else StudioColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun StudioFooter(
    modifier: Modifier = Modifier,
    text: String = "Private by design. Refined by you."
) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = StudioColors.TextMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    )
}

@Composable
fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconDrawable = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            context.packageManager.defaultActivityIcon
        }
    }

    val imageBitmap = remember(iconDrawable) {
        try {
            if (iconDrawable is android.graphics.drawable.BitmapDrawable) {
                iconDrawable.bitmap.asImageBitmap()
            } else {
                val bmp = android.graphics.Bitmap.createBitmap(
                    iconDrawable.intrinsicWidth.coerceAtLeast(1),
                    iconDrawable.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                iconDrawable.draw(canvas)
                bmp.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "App Icon",
            modifier = modifier.clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(StudioColors.CardSurfaceHighlight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    packageName.contains("maps", ignoreCase = true) -> "🗺️"
                    packageName.contains("whatsapp", ignoreCase = true) -> "💬"
                    packageName.contains("gmail", ignoreCase = true) -> "📧"
                    packageName.contains("instagram", ignoreCase = true) -> "📷"
                    else -> "📩"
                },
                fontSize = 12.sp
            )
        }
    }
}

