package com.focuslock

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings

fun hasUsageAccess(ctx: Context): Boolean {
    val ops = ctx.getSystemService(AppOpsManager::class.java)
    @Suppress("DEPRECATION")
    val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun hasOverlay(ctx: Context) = Settings.canDrawOverlays(ctx)

fun isReady(ctx: Context) = hasUsageAccess(ctx) && hasOverlay(ctx)
