package com.timeofwar.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.wear.tiles.TileService
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules the next Tile refresh. Exact alarms are used when the user/device
 * allows them; otherwise Android's idle-aware inexact alarm is the safe fallback.
 */
object TileRefreshScheduler {
    private const val ACTION = "com.timeofwar.clock.ACTION_REFRESH_TILE"
    private const val REQUEST_CODE = 2402

    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences("time_of_war_clock", Context.MODE_PRIVATE)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TileRefreshReceiver::class.java).setAction(ACTION)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pending)

        val now = LocalDateTime.now()
        val next = if (prefs.getBoolean("showHour", true)) {
            now.withMinute(0).withSecond(0).withNano(0).plusHours(1)
        } else {
            val eventTimes = buildList {
                if (prefs.getBoolean("show2022", true)) add(java.time.LocalTime.of(2, 40))
                if (prefs.getBoolean("show2014", false)) add(java.time.LocalTime.of(12, 0))
            }
            if (eventTimes.isEmpty()) return
            eventTimes.map { time ->
                var candidate = now.toLocalDate().atTime(time)
                if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
                candidate
            }.minOrNull() ?: return
        }
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                return
            } catch (_: SecurityException) {
                // The access can be revoked between the permission check and scheduling.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }
}

class TileRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            TileService.getUpdater(context).requestUpdate(TimeOfWarTileService::class.java)
        }
        TileRefreshScheduler.schedule(context)
    }
}
