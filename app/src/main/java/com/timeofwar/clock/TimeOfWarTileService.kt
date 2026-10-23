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
        val fontSize = prefs.getFloat("fontSize", 24f).coerceIn(12f, 48f)
        val textColor = android.graphics.Color.rgb(
            prefs.getFloat("textR", 255f).toInt().coerceIn(0, 255),
            prefs.getFloat("textG", 255f).toInt().coerceIn(0, 255),
            prefs.getFloat("textB", 255f).toInt().coerceIn(0, 255)
        )
        val strokeColor = android.graphics.Color.rgb(
            prefs.getFloat("strokeR", 0f).toInt().coerceIn(0, 255),
            prefs.getFloat("strokeG", 0f).toInt().coerceIn(0, 255),
            prefs.getFloat("strokeB", 0f).toInt().coerceIn(0, 255)
        )
        val bgOpacity = 1f
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
        val events = mutableListOf<Pair<String, String>>()
        if (prefs.getBoolean("show2022", true)) {
            events += "Повномасштабне вторгнення" to counterText(
                LocalDateTime.of(2022, 2, 24, 2, 40), now,
                prefs.getBoolean("showDaysOnly", false), prefs.getBoolean("showHour", true)
            )
        }
        if (prefs.getBoolean("show2014", false)) {
            events += "Війна з 2014 року" to counterText(
                LocalDateTime.of(2014, 2, 20, 12, 0), now,
                prefs.getBoolean("showDaysOnly", false), prefs.getBoolean("showHour", true)
            )
        }
        if (events.isEmpty()) events += "Увімкніть подію в налаштуваннях" to ""

        val openSettings = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            ).build()

        val customFile = File(prefs.getString("imagePath", "") ?: "")
        // Change the tile/resource cache key whenever saved preferences or the selected photo changes.
        val resourceVersion = "prefs-${prefs.all.hashCode()}-${customFile.lastModified()}-${customFile.length()}"

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(textElement("TIME OF WAR", 9f, 0xFFBDBDBD.toInt()))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(1f)).build())
        events.forEach { (title, counter) ->
            column.addContent(textElement(
                if (title == "Повномасштабне вторгнення") "З 24.02.2022" else if (title == "Війна з 2014 року") "З 20.02.2014" else title,
                8f, 0xFFFFFFFF.toInt()
            ))
            if (counter.isNotBlank()) {
                // Give the counter the whole tile width and calculate font size from a width budget,
                // instead of using a fixed character-count scale that can still clip on larger font settings.
                val availableWidthDp = 184f
                val estimatedGlyphWidthEm = 0.62f
                val widthFitSp = availableWidthDp /
                    (counter.length.coerceAtLeast(1).toFloat() * estimatedGlyphWidthEm)
                val fittedFontSize = minOf(fontSize * 0.82f, widthFitSp).coerceIn(6f, 22f)
                column.addContent(outlinedTextElement(
                    counter,
                    fittedFontSize,
                    textColor,
                    strokeColor
                ))
            }
        }
        column.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(1f)).build())
        column.addContent(textElement("Торкніться для налаштувань", 7f, 0xFF80CBC4.toInt()))

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
            .setResourcesVersion(resourceVersion)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(if (prefs.getBoolean("showHour", true)) 60 * 60 * 1000L else 24 * 60 * 60 * 1000L)
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val prefs = getSharedPreferences("time_of_war_clock", Context.MODE_PRIVATE)
        val preset = prefs.getString("backgroundPreset", "night") ?: "night"
        val customFile = File(prefs.getString("imagePath", "") ?: "")
        val version = "prefs-${prefs.all.hashCode()}-${customFile.lastModified()}-${customFile.length()}"
        val resources = ResourceBuilders.Resources.Builder().setVersion(version)
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

    private fun outlinedTextElement(
        value: String,
        sizeSp: Float,
        textColor: Int,
        outlineColor: Int
    ): LayoutElementBuilders.Box {
        val box = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        val offsets = listOf(
            -1.5f to -1.5f, 0f to -1.5f, 1.5f to -1.5f,
            -1.5f to 0f, 1.5f to 0f,
            -1.5f to 1.5f, 0f to 1.5f, 1.5f to 1.5f
        )
        offsets.forEach { (dx, dy) ->
            val style = LayoutElementBuilders.FontStyle.Builder()
                .setSize(DimensionBuilders.sp(sizeSp))
                .setColor(ColorBuilders.argb(outlineColor))
                .build()
            val transform = ModifiersBuilders.Transformation.Builder()
                .setTranslationX(DimensionBuilders.dp(dx))
                .setTranslationY(DimensionBuilders.dp(dy))
                .build()
            box.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(value)
                    .setFontStyle(style)
                    .setModifiers(ModifiersBuilders.Modifiers.Builder().setTransformation(transform).build())
                    .build()
            )
        }
        box.addContent(textElement(value, sizeSp, textColor))
        return box.build()
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
}
