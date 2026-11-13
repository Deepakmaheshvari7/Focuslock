package com.focuslock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    var goal by remember { mutableIntStateOf(FocusStore.goalMin(ctx)) }

    val today = FocusStore.today()
    val todaySec = FocusStore.focusSec(ctx, today)
    val goalSec = goal * 60L
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val secs = days.map { FocusStore.focusSec(ctx, it.toString()) }
    val maxSec = maxOf(secs.maxOrNull() ?: 0L, goalSec, 1L)

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Text("‹  Back", color = Muted, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 16.dp))
        Text("Statistics", color = Fg, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(24.dp))

        Text("TODAY", color = Muted, fontSize = 11.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(4.dp))
        Text(formatDuration(todaySec), color = Fg, fontSize = 48.sp, fontWeight = FontWeight.Thin)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Line)) {
            Box(
                Modifier.fillMaxWidth((todaySec.toFloat() / goalSec).coerceIn(0f, 1f))
                    .fillMaxHeight().background(Fg)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (todaySec >= goalSec) "Goal reached 🎯" else "${formatDuration(goalSec - todaySec)} to reach your goal",
            color = Muted, fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Daily study goal", color = Fg, fontSize = 16.sp)
                Text(formatDuration(goalSec), color = Muted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("−", false, goal > 30) { goal -= 30; FocusStore.setGoalMin(ctx, goal) }
                Chip("+", false, goal < 960) { goal += 30; FocusStore.setGoalMin(ctx, goal) }
            }
        }
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("${FocusStore.sessions(ctx, today)}", "Sessions completed", Modifier.weight(1f))
            StatCard("${FocusStore.blockedCount(ctx, today)}", "Distractions blocked", Modifier.weight(1f))
        }
        Spacer(Modifier.height(28.dp))

        Text("LAST 7 DAYS", color = Muted, fontSize = 11.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(170.dp), verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, d ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(if (secs[i] > 0) formatDuration(secs[i]) else "", color = Muted, fontSize = 10.sp, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    val h: Dp = (110f * secs[i] / maxSec).dp.coerceAtLeast(3.dp)
                    Box(
                        Modifier.width(18.dp).height(h).clip(RoundedCornerShape(4.dp))
                            .background(if (i == 6) Fg else Color(0xFF3A3A3A))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("This week: ${formatDuration(secs.sum())}", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Surf).padding(16.dp)) {
        Text(value, color = Fg, fontSize = 30.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Muted, fontSize = 12.sp)
    }
}
