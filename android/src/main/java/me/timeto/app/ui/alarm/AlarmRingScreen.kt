package me.timeto.app.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.timeto.app.ui.c
import me.timeto.app.ui.squircleShape

/**
 * The full-screen-intent target. It deliberately has no dismiss control: the
 * ring stops only on snooze or on starting a new activity, and opening the app
 * must not silence it.
 *
 * Snooze lives here as well as on the notification, so it still works when
 * notifications are denied or the full-screen grant is missing.
 */
@Composable
fun AlarmRingScreen(
    onSnooze: () -> Unit,
    onStart: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(12, 12, 14))
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Text(
            text = "Time Is Over ⏰",
            color = c.text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Snooze or start a new activity",
            color = c.secondaryText,
            fontSize = 16.sp,
        )

        Spacer(modifier = Modifier.height(48.dp))

        AlarmRingButton(
            title = "Snooze",
            backgroundColor = c.fg,
            onClick = onSnooze,
        )

        Spacer(modifier = Modifier.height(16.dp))

        AlarmRingButton(
            title = "Start",
            backgroundColor = c.blue,
            onClick = onStart,
        )
    }
}

@Composable
private fun AlarmRingButton(
    title: String,
    backgroundColor: Color,
    onClick: () -> Unit,
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(squircleShape)
            .background(backgroundColor)
            .combinedClickable(onClick = onClick, onLongClick = null)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = c.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
