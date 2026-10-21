package com.timeofwar.clock

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.wear.protolayout.TypeBuilders
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
        TileRefreshScheduler.schedule(this)
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
        val backgroundPreset = prefs.getString("backgroundPreset", "night") ?: "night"
        val hasBackgroundImage = backgroundPreset != "color" && (
            backgroundPreset != "custom" || File(prefs.getString("imagePath", "") ?: "").let { it.exists() && it.length() > 0L }
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
            .apply {
                if (hasBackgroundImage) {
                    addContent(
                        LayoutElementBuilders.Image.Builder()
                            .setResourceId("watch_background")
                            .setWidth(DimensionBuilders.expand())
                            .setHeight(DimensionBuilders.expand())
                            .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_CROP)
                            .setModifiers(
                                ModifiersBuilders.Modifiers.Builder()
                                    .setOpacity(TypeBuilders.FloatProp.Builder().setValue(bgOpacity).build())
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .addContent(column.build())
            .build()

        val layout = LayoutElementBuilders.Layout.Builder().setRoot(root).build()
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(TimelineBuilders.TimelineEntry.Builder().setLayout(layout).build())
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(if (prefs.getBoolean("showHour", true)) 60 * 60 * 1000L else 24 * 60 * 60 * 1000L)
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val prefs = getSharedPreferences("time_of_war_clock", Context.MODE_PRIVATE)
        val preset = prefs.getString("backgroundPreset", "night") ?: "night"
        val resources = ResourceBuilders.Resources.Builder().setVersion("1")
        val imageResource = when (preset) {
            "color" -> null
            "ukraine" -> drawableResource(R.drawable.bg_ukraine)
            "camo" -> drawableResource(R.drawable.bg_camo)
            "redblack" -> drawableResource(R.drawable.bg_red_black)
            "custom" -> customImageResource(prefs.getString("imagePath", "") ?: "")
            else -> drawableResource(R.drawable.bg_night)
        }
        if (imageResource != null) resources.addIdToImageMapping("watch_background", imageResource)
        return Futures.immediateFuture(resources.build())
    }

    private fun drawableResource(drawableId: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(drawableId)
                    .build()
            ).build()

    private fun customImageResource(path: String): ResourceBuilders.ImageResource? = runCatching {
        val source = BitmapFactory.decodeFile(path) ?: return null
        val scale = minOf(1f, 320f / maxOf(source.width, source.height))
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 78, output)
        val inline = ResourceBuilders.InlineImageResource.Builder()
            .setData(output.toByteArray())
            .setWidthPx(bitmap.width)
            .setHeightPx(bitmap.height)
            .build()
        if (bitmap !== source) bitmap.recycle()
        source.recycle()
        ResourceBuilders.ImageResource.Builder().setInlineResource(inline).build()
    }.getOrNull()

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
}
