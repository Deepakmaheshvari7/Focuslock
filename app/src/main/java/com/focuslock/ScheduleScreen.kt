package com.focuslock

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun hhmm(min: Int): String {
    val h = min / 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, min % 60, if (h < 12) "AM" else "PM")
}

@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    val items = remember { mutableStateListOf<Schedule>().apply { addAll(FocusStore.schedules(ctx)) } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        (ctx as ComponentActivity).repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(1000) }
        }
    }
    val locked = FocusStore.strict(ctx) && currentBlock(ctx, now) != null
    val noApps = FocusStore.blockedApps(ctx).isEmpty()
    val ready = isReady(ctx)

    fun canExact(): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    var exactOk by remember { mutableStateOf(canExact()) }
    DisposableEffect(Unit) {
        val owner = ctx as ComponentActivity
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) exactOk = canExact() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun save() { FocusStore.setSchedules(ctx, items.toList()); Scheduler.sync(ctx) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Text("‹  Back", color = Muted, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 16.dp))
        Text("Study schedule", color = Fg, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(6.dp))
        Text("Your blocked apps are locked automatically during these hours.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text("You get a reminder 10 minutes before each study time.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text("New schedules start switched off. Set the days and times, then turn it on.", color = Muted, fontSize = 13.sp)
        if (!exactOk) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Allow exact timing ›  Without it, schedules and reminders may start a few minutes late.",
                color = Fg, fontSize = 13.sp,
                modifier = Modifier.clickable {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                    )
                }
            )
        }
        if (Locks.scheduleWindowActive(ctx, now)) {
            Spacer(Modifier.height(6.dp))
            Text("A schedule is running. You can make it longer, but not shorter or turn it off.", color = Fg, fontSize = 13.sp)
        }
        if (locked) { Spacer(Modifier.height(6.dp)); Text("Locked while strict mode is active.", color = Muted, fontSize = 13.sp) }
        if (noApps) { Spacer(Modifier.height(6.dp)); Text("Pick apps to block on the main screen first.", color = Muted, fontSize = 13.sp) }
        if (!ready) { Spacer(Modifier.height(6.dp)); Text("Grant access from the main screen so blocking can run.", color = Muted, fontSize = 13.sp) }
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(items, key = { _, s -> s.id }) { i, s ->
                val strictLock = FocusStore.strict(ctx) && currentBlock(ctx, now) != null
                val denied = "A running schedule can be made longer, but not shortened or turned off."
                ScheduleCard(
                    s, strictLock, running = isScheduleActive(s, now),
                    // re-checked at the moment of the change, so a dialog opened earlier can't slip through
                    onChange = { new ->
                        if (Locks.canChange(ctx, s, new, System.currentTimeMillis())) { items[i] = new; save() }
                        else Toast.makeText(ctx, denied, Toast.LENGTH_LONG).show()
                    },
                    onDelete = {
                        if (Locks.canChange(ctx, s, null, System.currentTimeMillis())) { items.removeAt(i); save() }
                        else Toast.makeText(ctx, denied, Toast.LENGTH_LONG).show()
                    }
                )
            }
            item {
                Box(
                    Modifier.fillMaxWidth().alpha(if (locked) 0.4f else 1f).clip(RoundedCornerShape(16.dp)).background(Surf)
                        .clickable(enabled = !locked) {
                            items.add(Schedule(System.currentTimeMillis(), 0b0011111, 9 * 60, 12 * 60, false))
                            save()
                        }.padding(18.dp),
                    contentAlignment = Alignment.Center
                ) { Text("+  Add schedule", color = Fg, fontSize = 15.sp) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ScheduleCard(s: Schedule, locked: Boolean, running: Boolean, onChange: (Schedule) -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    fun pick(initial: Int, set: (Int) -> Schedule) {
        TimePickerDialog(ctx, { _, h, m -> onChange(set(h * 60 + m)) }, initial / 60, initial % 60, false).show()
    }
    Column(
        Modifier.fillMaxWidth().alpha(if (locked) 0.5f else 1f)
            .clip(RoundedCornerShape(16.dp)).background(Surf).padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hhmm(s.startMin), color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.clickable(enabled = !locked) { pick(s.startMin) { s.copy(startMin = it) } })
                Text("  –  ", color = Muted, fontSize = 18.sp)
                Text(hhmm(s.endMin), color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.clickable(enabled = !locked) { pick(s.endMin) { s.copy(endMin = it) } })
            }
            Switch(
                checked = s.enabled, enabled = !locked, onCheckedChange = { onChange(s.copy(enabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Bg, checkedTrackColor = Fg,
                    uncheckedThumbColor = Muted, uncheckedTrackColor = Surf, uncheckedBorderColor = Line
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            "MTWTFSS".forEachIndexed { i, ch ->
                val on = (s.days and (1 shl i)) != 0
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(if (on) Fg else Line)
                        .clickable(enabled = !locked) { onChange(s.copy(days = s.days xor (1 shl i))) },
                    contentAlignment = Alignment.Center
                ) { Text(ch.toString(), color = if (on) Bg else Muted, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                when {
                    locked -> "Locked by strict mode"
                    running -> "Running now: can be extended, not shortened"
                    s.days == 0 -> "Pick at least one day"
                    s.startMin == s.endMin -> "Start and end must differ"
                    s.endMin < s.startMin -> "Overnight, ends next day"
                    else -> "Repeats weekly"
                },
                color = Muted, fontSize = 12.sp
            )
            Text("Delete", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable(enabled = !locked, onClick = onDelete))
        }
    }
}
