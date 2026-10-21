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
 * Asks how long FocusRefine should work for; shown when the Home switch is turned on. Choosing a length turns filtering
 * on for that long (a session in [mode]); "Until I turn it off" has no end time. Cancel leaves filtering off.
 */
@Composable
fun SessionLengthDialog(mode: String, onPick: (minutes: Int?) -> Unit, onDismiss: () -> Unit) {
    run {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("How long should FocusRefine work?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filtering stays on in $mode mode for the time you pick, then pauses.", fontSize = 13.sp)
                    for (minutes in ModeSession.DURATIONS_MIN) {
                        Surface(
                            onClick = { onPick(minutes) },
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
                    Surface(
                        onClick = { onPick(null) },
                        shape = CircleShape,
                        color = StudioColors.CardSurface,
                        border = BorderStroke(1.dp, StudioColors.ActiveViolet),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Until I turn it off",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}
