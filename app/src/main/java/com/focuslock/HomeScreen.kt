package com.focuslock

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(onOpenApps: () -> Unit, onOpenStats: () -> Unit, onOpenSchedule: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var minutes by remember { mutableIntStateOf(FocusStore.minutes(ctx)) }
    var appCount by remember { mutableIntStateOf(FocusStore.blockedApps(ctx).size) }
    var ready by remember { mutableStateOf(isReady(ctx)) }
    var strict by remember { mutableStateOf(FocusStore.strict(ctx)) }
    var showPermission by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                ready = isReady(ctx)
                appCount = FocusStore.blockedApps(ctx).size
                strict = FocusStore.strict(ctx)
                now = System.currentTimeMillis()
                FocusStore.finalizeIfDone(ctx)
                Scheduler.sync(ctx)
            }
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }

    // Ticks only while visible: every 0.5s during focus, every 5s when idle (to notice a schedule starting).
    LaunchedEffect(Unit) {
        activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                FocusStore.finalizeIfDone(ctx)
                delay(if (currentBlock(ctx, now) != null) 500 else 5000)
            }
        }
    }

    val block = currentBlock(ctx, now)
    val active = block != null
    val isSchedule = block?.kind == Kind.SCHEDULE
    val onBreak = now < FocusStore.breakUntil(ctx)
    val remaining = if (block != null) (block.end - now).coerceAtLeast(0) else 0L
    val total = if (block != null) (block.end - block.start).coerceAtLeast(1) else 1L
    val progress = if (active) remaining.toFloat() / total else 1f
    val pct = if (active) ((1f - progress) * 100).toInt().coerceIn(0, 100) else 0
    val todaySec = FocusStore.focusSec(ctx, FocusStore.today())
    val goalSec = FocusStore.goalMin(ctx) * 60L
    val appsLocked = Locks.appsLocked(ctx, now)

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("FOCUSLOCK", color = Muted, fontSize = 12.sp, letterSpacing = 4.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Schedule", color = Fg, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onOpenSchedule).padding(vertical = 8.dp))
                Text("Stats", color = Fg, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onOpenStats).padding(vertical = 8.dp))
            }
        }
        Spacer(Modifier.height(20.dp))

        TimerRing(
            progress = progress,
            time = formatTime(if (active) remaining else minutes * 60_000L),
            caption = when {
                onBreak -> "ON BREAK"
                isSchedule -> "SCHEDULED"
                active -> "FOCUS ON"
                else -> "READY"
            },
            active = active && !onBreak
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (active) "$pct% complete  ·  $appCount ${if (appCount == 1) "app" else "apps"} blocked" +
                (if (strict) "  ·  Strict" else "")
            else "Focus off",
            color = Muted, fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Today ${formatDuration(todaySec)} of ${formatDuration(goalSec)} goal",
            color = Muted, fontSize = 13.sp
        )

        if (active) {
            Spacer(Modifier.height(16.dp))
            BreakControls(now, onChanged = { now = System.currentTimeMillis() })
        }
        Spacer(Modifier.height(20.dp))

        val presets = listOf(25, 45, 60, 90, 120)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { m ->
                Chip("${m}m", minutes == m, !active) { minutes = m; FocusStore.setMinutes(ctx, m) }
            }
            val custom = minutes !in presets
            Chip(if (custom) "${minutes}m" else "Custom", custom, !active) { showCustom = true }
        }
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surf)
                .clickable(onClick = onOpenApps).padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Blocked apps", color = Fg, fontSize = 16.sp)
            Text(if (appsLocked) "$appCount · add only  ›" else "$appCount selected  ›", color = Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surf)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Strict mode", color = Fg, fontSize = 16.sp)
                Text("No stopping or editing during focus", color = Muted, fontSize = 12.sp)
            }
            Switch(
                checked = strict, enabled = !active,
                onCheckedChange = { strict = it; FocusStore.setStrict(ctx, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Bg, checkedTrackColor = Fg,
                    uncheckedThumbColor = Muted, uncheckedTrackColor = Surf, uncheckedBorderColor = Line
                )
            )
        }
        Spacer(Modifier.height(16.dp))

        val locked = active && (isSchedule || strict)
        val label = when {
            active && isSchedule -> "SCHEDULED FOCUS"
            active && strict -> "STRICT MODE"
            active -> "STOP"
            !ready -> "GRANT ACCESS"
            appCount == 0 -> "SELECT APPS"
            else -> "START FOCUS"
        }
        Box(
            Modifier.fillMaxWidth().height(72.dp).alpha(if (locked) 0.45f else 1f)
                .clip(RoundedCornerShape(36.dp))
                .background(if (active) Bg else Fg)
                .then(if (active) Modifier.border(1.dp, Line, RoundedCornerShape(36.dp)) else Modifier)
                .clickable(enabled = !locked) {
                    when {
                        active -> { FocusStore.stop(ctx); now = System.currentTimeMillis() }
                        !ready -> showPermission = true
                        appCount == 0 -> onOpenApps()
                        else -> {
                            FocusStore.start(ctx, minutes)
                            ContextCompat.startForegroundService(ctx, Intent(ctx, BlockerService::class.java))
                            now = System.currentTimeMillis()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = if (active) Fg else Bg, fontSize = 15.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (showPermission) {
        val needUsage = !hasUsageAccess(ctx)
        AlertDialog(
            onDismissRequest = { showPermission = false },
            containerColor = Surf,
            title = { Text("Before you continue") },
            text = {
                Text(
                    "FocusLock needs two permissions to block apps:\n\n" +
                    "1. Usage access: FocusLock checks which app is in the foreground, only during your focus time, so it can tell when you open an app you chose to block.\n\n" +
                    "2. Display over other apps: so the Stay Focused screen can appear on top of that app.\n\n" +
                    "This information stays on your phone. FocusLock does not collect, store or share it, and has no internet access.\n\n" +
                    if (needUsage) "Tap Agree, then turn on FocusLock under Usage access and come back."
                    else "Tap Agree, then turn on 'Allow display over other apps' for FocusLock.",
                    color = Muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermission = false
                    if (needUsage) ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    else ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    )
                }) { Text("Agree", color = Fg) }
            },
            dismissButton = { TextButton(onClick = { showPermission = false }) { Text("Cancel", color = Muted) } }
        )
    }

    if (showCustom) {
        var text by remember { mutableStateOf(minutes.toString()) }
        AlertDialog(
            onDismissRequest = { showCustom = false },
            containerColor = Surf,
            title = { Text("Custom duration") },
            text = {
                TextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    suffix = { Text("min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.takeIf { it in 1..720 }?.let { minutes = it; FocusStore.setMinutes(ctx, it) }
                    showCustom = false
                }) { Text("Set", color = Fg) }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Cancel", color = Muted) } }
        )
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(50))
            .background(if (selected) Fg else Surf)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) { Text(label, color = if (selected) Bg else Fg, fontSize = 14.sp) }
}

@Composable
private fun TimerRing(progress: Float, time: String, caption: String, active: Boolean) {
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(Line, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            drawArc(
                if (active) Fg else Color(0xFF3A3A3A), -90f, 360f * progress, false, topLeft, arc,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, color = Fg, fontSize = 52.sp, fontWeight = FontWeight.Thin)
            Spacer(Modifier.height(4.dp))
            Text(caption, color = Muted, fontSize = 12.sp, letterSpacing = 3.sp)
        }
    }
}
