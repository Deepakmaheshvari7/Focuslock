package com.focuslock

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Emergency break: 2/3/5 minutes off, then a 30-minute cooldown before the next one. */
@Composable
fun BreakControls(now: Long, onChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    val breakUntil = FocusStore.breakUntil(ctx)
    val cooldown = FocusStore.cooldownUntil(ctx)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            now < breakUntil -> {
                Text("Break ends in ${formatTime(breakUntil - now)}", color = Fg, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Chip("End break now", false, true) { FocusStore.endBreak(ctx); onChanged() }
            }
            now < cooldown -> Text(
                "Next emergency break in ${formatTime(cooldown - now)}", color = Muted, fontSize = 13.sp
            )
            else -> {
                Text("EMERGENCY BREAK", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 5).forEach { m ->
                        Chip("$m min", false, true) { if (FocusStore.startBreak(ctx, m)) onChanged() }
                    }
                }
            }
        }
    }
}
