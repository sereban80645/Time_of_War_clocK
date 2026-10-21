package com.timeofwar.clock

import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.ResourceBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.time.LocalDateTime
import java.time.Period
import java.time.Duration

/**
 * Offline Wear OS Tile. All values are read from this watch's private preferences;
 * no phone companion or network connection is required.
 */
class TimeOfWarTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("time_of_war_clock", Context.MODE_PRIVATE)
        val now = LocalDateTime.now()
        val fontSize = prefs.getFloat("fontSize", 18f)
        val textColor = android.graphics.Color.rgb(
            prefs.getFloat("textR", 255f).toInt().coerceIn(0, 255),
            prefs.getFloat("textG", 255f).toInt().coerceIn(0, 255),
            prefs.getFloat("textB", 255f).toInt().coerceIn(0, 255)
        )
        val bgOpacity = prefs.getFloat("opacity", 0.5f).coerceIn(0f, 1f)
        val bgColor = android.graphics.Color.argb(
            (bgOpacity * 255).toInt(),
            prefs.getFloat("bgR", 30f).toInt().coerceIn(0, 255),
            prefs.getFloat("bgG", 30f).toInt().coerceIn(0, 255),
            prefs.getFloat("bgB", 30f).toInt().coerceIn(0, 255)
        )
        val lines = mutableListOf<String>()
        if (prefs.getBoolean("show2022", true)) {
            lines += "24.02.2022 · " + counterText(
                LocalDateTime.of(2022, 2, 24, 2, 40), now,
                prefs.getBoolean("showDaysOnly", false), prefs.getBoolean("showHour", true)
            )
        }
        if (prefs.getBoolean("show2014", false)) {
            lines += "20.02.2014 · " + counterText(
                LocalDateTime.of(2014, 2, 20, 12, 0), now,
                prefs.getBoolean("showDaysOnly", false), prefs.getBoolean("showHour", true)
            )
        }
        if (lines.isEmpty()) lines += "Увімкніть подію в налаштуваннях"

        val openSettings = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            ).build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(textElement("TIME OF WAR", 12f, 0xFFBDBDBD.toInt()))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
        lines.forEach { line -> column.addContent(textElement(line, (fontSize * 0.65f).coerceIn(10f, 18f), textColor)) }
        column.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(5f)).build())
        column.addContent(textElement("Торкніться для налаштувань", 10f, 0xFF80CBC4.toInt()))

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(bgColor)).build())
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open_settings")
                            .setOnClick(openSettings)
                            .build()
                    ).build()
            )
            .addContent(column.build())
            .build()

        val layout = LayoutElementBuilders.Layout.Builder().setRoot(root).build()
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(TimelineBuilders.TimelineEntry.Builder().setLayout(layout).build())
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(15 * 60 * 1000L)
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion("1").build())
    }

    private fun textElement(value: String, sizeSp: Float, color: Int): LayoutElementBuilders.Text {
        val style = LayoutElementBuilders.FontStyle.Builder()
            .setSize(DimensionBuilders.sp(sizeSp))
            .setColor(ColorBuilders.argb(color))
            .build()
        return LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(style)
            .build()
    }

    private fun counterText(start: LocalDateTime, now: LocalDateTime, daysOnly: Boolean, showHours: Boolean): String =
        CounterFormatter.format(start, now, daysOnly, showHours)
