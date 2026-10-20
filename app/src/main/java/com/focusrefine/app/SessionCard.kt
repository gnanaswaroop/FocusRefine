package com.focusrefine.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home entry point for timed sessions, shown at the end of the mode row while no session runs: a small chip that opens a
 * duration picker. While a session runs, [SessionActiveCard] takes its place.
 */
@Composable
fun SessionStartChip(mode: String, onStart: (minutes: Int) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }

    Surface(
        onClick = { picking = true },
        shape = CircleShape,
        color = StudioColors.CardSurface,
        border = BorderStroke(1.dp, StudioColors.StudioBorder),
        modifier = modifier
    ) {
        Text(
            text = "⏱ Timed session",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Start a $mode session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$mode stays on for the time you pick, then your usual mode comes back.", fontSize = 13.sp)
                    for (minutes in ModeSession.DURATIONS_MIN) {
                        Surface(
                            onClick = { picking = false; onStart(minutes) },
                            shape = CircleShape,
                            color = StudioColors.ActiveViolet,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "$minutes minutes",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        )
    }
}

/**
 * The card shown while a session runs: mode, time left, muted count, a progress bar and End session. [now] is the
 * current time in ms; Home refreshes it every 30 seconds so the card stays current without a broadcast.
 */
@Composable
fun SessionActiveCard(session: ModeSession.State, now: Long, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    StudioCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${session.mode} session", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "${ModeSession.minutesLeft(session.endMs, now)} min left · ${session.mutedSoFar} muted so far",
                        fontSize = 12.sp,
                        color = StudioColors.TextSecondary
                    )
                }
                TextButton(onClick = onEnd) { Text("End session", color = StudioColors.ActiveVioletLight) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ModeSession.percent(session.startMs, session.endMs, now) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = StudioColors.ActiveViolet,
                trackColor = StudioColors.StudioBorder
            )
        }
    }
}
