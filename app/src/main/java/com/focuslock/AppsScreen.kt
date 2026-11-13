package com.focuslock

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class AppInfo(val label: String, val pkg: String)

private suspend fun loadApps(ctx: Context, selected: Set<String>): List<AppInfo> =
    withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedWith(compareBy({ it.pkg !in selected }, { it.label.lowercase() }))
    }

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)

    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>().apply { addAll(FocusStore.blockedApps(ctx)) } }
    LaunchedEffect(Unit) { apps = loadApps(ctx, selected.toSet()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        (ctx as ComponentActivity).repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(1000) }
        }
    }
    val locked = Locks.appsLocked(ctx, now)

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Text(
            "‹  Back", color = Muted, fontSize = 15.sp,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 16.dp)
        )
        Text("Block during focus", color = Fg, fontSize = 28.sp, fontWeight = FontWeight.Light)
        if (locked) {
            Spacer(Modifier.height(6.dp))
            Text("Focus time is active: you can add apps but not remove them.", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        TextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search apps", color = Muted) },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surf, unfocusedContainerColor = Surf,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            )
        )
        Spacer(Modifier.height(8.dp))

        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = Muted) }
        } else {
            val shown = list.filter { it.label.contains(query, ignoreCase = true) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.pkg }) { app ->
                    val checked = app.pkg in selected
                    val icon = remember(app.pkg) {
                        runCatching { ctx.packageManager.getApplicationIcon(app.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    }
                    val toggle = {
                        // re-check at tap time; while locked, only adding is allowed
                        val lockedNow = Locks.appsLocked(ctx, System.currentTimeMillis())
                        if (checked) { if (!lockedNow) selected.remove(app.pkg) } else selected.add(app.pkg)
                        FocusStore.setBlockedApps(ctx, selected.toSet())
                        Unit
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) Image(icon, null, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(app.label, color = Fg, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Switch(
                            checked = checked, onCheckedChange = { toggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Bg, checkedTrackColor = Fg,
                                uncheckedThumbColor = Muted, uncheckedTrackColor = Surf,
                                uncheckedBorderColor = Line
                            )
                        )
                    }
                }
            }
        }
    }
}
