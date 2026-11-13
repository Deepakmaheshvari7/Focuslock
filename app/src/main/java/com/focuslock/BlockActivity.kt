package com.focuslock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The "Stay Focused" screen shown on top of a blocked app. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(SystemBarStyle.dark(transparent), SystemBarStyle.dark(transparent))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            FocusTheme { BlockScreen(onHome = ::goHome, onDone = { finish() }) }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}

@Composable
private fun BlockScreen(onHome: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var remaining by remember { mutableLongStateOf(0L) }
    val strict = remember { FocusStore.strict(ctx) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val b = currentBlock(ctx, now)
            // Block over, or an emergency break just started: reveal the app underneath.
            if (b == null || now < FocusStore.breakUntil(ctx)) { onDone(); break }
            remaining = b.end - now
            delay(500)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Stay Focused", color = Fg, fontSize = 36.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text("This app is blocked until your focus time ends.", color = Muted, fontSize = 15.sp)
        if (strict) {
            Spacer(Modifier.height(6.dp))
            Text("Strict mode is on", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(40.dp))
        Text(formatTime(remaining), color = Fg, fontSize = 64.sp, fontWeight = FontWeight.Thin)
        Spacer(Modifier.height(40.dp))
        BreakControls(now, onChanged = { onDone() })
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(Fg).clickable(onClick = onHome)
                .padding(horizontal = 36.dp, vertical = 16.dp)
        ) { Text("BACK TO STUDYING", color = Bg, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium) }
    }
}
