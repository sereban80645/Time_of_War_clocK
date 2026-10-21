package com.timeofwar.clock

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.max

private val Event2022 = LocalDateTime.of(2022, 2, 24, 2, 40)
private val Event2014 = LocalDateTime.of(2014, 2, 20, 12, 0)

class MainActivity : ComponentActivity() {
    override fun onPause() {
        super.onPause()
        TileRefreshScheduler.schedule(this)
        runCatching { androidx.wear.tiles.TileService.getUpdater(this).requestUpdate(TimeOfWarTileService::class.java) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TileRefreshScheduler.schedule(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { WatchHome(this) } }
    }
}

private class WatchPrefs(context: Context) {
    private val p = context.getSharedPreferences("time_of_war_clock", Context.MODE_PRIVATE)
    fun bool(key: String, default: Boolean) = p.getBoolean(key, default)
    fun float(key: String, default: Float) = p.getFloat(key, default)
    fun string(key: String, default: String = "") = p.getString(key, default) ?: default
    fun put(key: String, value: Any) { p.edit().apply { when(value) {
        is Boolean -> putBoolean(key, value); is Float -> putFloat(key, value); is String -> putString(key, value)
    } }.apply() }
}

@Composable
private fun WatchHome(activity: Activity) {
    val prefs = remember { WatchPrefs(activity) }
    var show2022 by remember { mutableStateOf(prefs.bool("show2022", true)) }
    var show2014 by remember { mutableStateOf(prefs.bool("show2014", false)) }
    var daysOnly by remember { mutableStateOf(prefs.bool("showDaysOnly", false)) }
    var showHours by remember { mutableStateOf(prefs.bool("showHour", true)) }
    var fontSize by remember { mutableFloatStateOf(prefs.float("fontSize", 18f)) }
    var strokeWidth by remember { mutableFloatStateOf(prefs.float("strokeWidth", 2f)) }
    var opacity by remember { mutableFloatStateOf(prefs.float("opacity", .5f)) }
    var textR by remember { mutableFloatStateOf(prefs.float("textR", 255f)) }
    var textG by remember { mutableFloatStateOf(prefs.float("textG", 255f)) }
    var textB by remember { mutableFloatStateOf(prefs.float("textB", 255f)) }
    var strokeR by remember { mutableFloatStateOf(prefs.float("strokeR", 0f)) }
    var strokeG by remember { mutableFloatStateOf(prefs.float("strokeG", 0f)) }
    var strokeB by remember { mutableFloatStateOf(prefs.float("strokeB", 0f)) }
    var bgR by remember { mutableFloatStateOf(prefs.float("bgR", 30f)) }
    var bgG by remember { mutableFloatStateOf(prefs.float("bgG", 30f)) }
    var bgB by remember { mutableFloatStateOf(prefs.float("bgB", 30f)) }
    var imagePath by remember { mutableStateOf(prefs.string("imagePath")) }
    var backgroundPreset by remember { mutableStateOf(prefs.string("backgroundPreset", if (prefs.string("imagePath").isNotBlank()) "custom" else "night")) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); kotlinx.coroutines.delay(60_000) } }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) runCatching {
            val target = File(activity.filesDir, "watch_background.img")
            val copied = activity.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
            if (copied != null && target.exists() && target.length() > 0L) {
                imagePath = target.absolutePath; prefs.put("imagePath", imagePath)
                backgroundPreset = "custom"; prefs.put("backgroundPreset", backgroundPreset)
            }
        }
    }
    val textColor = Color(textR / 255f, textG / 255f, textB / 255f)
    val strokeColor = Color(strokeR / 255f, strokeG / 255f, strokeB / 255f)
    val bgColor = Color(bgR / 255f, bgG / 255f, bgB / 255f)

    Box(Modifier.fillMaxSize().background(bgColor.copy(alpha = opacity.coerceIn(0f, 1f)))) {
        if (backgroundPreset == "custom" && imagePath.isNotBlank()) {
            val bitmap = remember(imagePath) { runCatching { BitmapFactory.decodeFile(imagePath)?.asImageBitmap() }.getOrNull() }
            if (bitmap != null) Image(bitmap, contentDescription = "Власне фонове зображення", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(opacity.coerceIn(0f, 1f)))
        } else if (backgroundPreset != "color") {
            val presetRes = when (backgroundPreset) {
                "ukraine" -> R.drawable.bg_ukraine
                "camo" -> R.drawable.bg_camo
                "redblack" -> R.drawable.bg_red_black
                else -> R.drawable.bg_night
            }
            Image(painter = painterResource(presetRes), contentDescription = "Вбудований фон", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(opacity.coerceIn(0f, 1f)))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("TIME OF WAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (show2022) Counter("Повномасштабна війна", counter(Event2022, now, daysOnly, showHours), fontSize, strokeWidth, textColor, strokeColor)
        if (show2014) Counter("Війна з 2014 року", counter(Event2014, now, daysOnly, showHours), fontSize, strokeWidth, textColor, strokeColor)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("НАЛАШТУВАННЯ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        SettingSwitch("Відлік з 24.02.2022", show2022) { show2022 = it; prefs.put("show2022", it) }
        SettingSwitch("Відлік з 20.02.2014", show2014) { show2014 = it; prefs.put("show2014", it) }
        SettingSwitch("Тільки дні", daysOnly) { daysOnly = it; prefs.put("showDaysOnly", it) }
        SettingSwitch("Показувати години", showHours) { showHours = it; prefs.put("showHour", it) }
        SettingSlider("Розмір шрифту", fontSize, 10f, 30f) { fontSize = it; prefs.put("fontSize", it) }
        SettingSlider("Товщина обведення", strokeWidth, 0f, 5f) { strokeWidth = it; prefs.put("strokeWidth", it) }
        SettingSlider("Прозорість фону", opacity, 0f, 1f) { opacity = it; prefs.put("opacity", it) }
        Text("Колір тексту RGB", fontSize = 11.sp)
        SettingSlider("R", textR, 0f, 255f) { textR = it; prefs.put("textR", it) }
        SettingSlider("G", textG, 0f, 255f) { textG = it; prefs.put("textG", it) }
        SettingSlider("B", textB, 0f, 255f) { textB = it; prefs.put("textB", it) }
        Text("Колір обведення RGB", fontSize = 11.sp)
        SettingSlider("R", strokeR, 0f, 255f) { strokeR = it; prefs.put("strokeR", it) }
        SettingSlider("G", strokeG, 0f, 255f) { strokeG = it; prefs.put("strokeG", it) }
        SettingSlider("B", strokeB, 0f, 255f) { strokeB = it; prefs.put("strokeB", it) }
        Text("Колір фону RGB", fontSize = 11.sp)
        SettingSlider("R", bgR, 0f, 255f) { bgR = it; prefs.put("bgR", it) }
        SettingSlider("G", bgG, 0f, 255f) { bgG = it; prefs.put("bgG", it) }
        SettingSlider("B", bgB, 0f, 255f) { bgB = it; prefs.put("bgB", it) }
        Text("Вбудовані фони", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Button(onClick = { backgroundPreset = "night"; prefs.put("backgroundPreset", backgroundPreset) }, modifier = Modifier.fillMaxWidth()) { Text("Темний") }
        Button(onClick = { backgroundPreset = "ukraine"; prefs.put("backgroundPreset", backgroundPreset) }, modifier = Modifier.fillMaxWidth()) { Text("Прапор України") }
        Button(onClick = { backgroundPreset = "camo"; prefs.put("backgroundPreset", backgroundPreset) }, modifier = Modifier.fillMaxWidth()) { Text("Камуфляж") }
        Button(onClick = { backgroundPreset = "redblack"; prefs.put("backgroundPreset", backgroundPreset) }, modifier = Modifier.fillMaxWidth()) { Text("Чорно-червоний") }
        Button(onClick = { backgroundPreset = "color"; prefs.put("backgroundPreset", backgroundPreset) }, modifier = Modifier.fillMaxWidth()) { Text("Лише колір RGB") }
        Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Вибрати власне фото") }
        Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 11.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ${"%.0f".format(value)}", fontSize = 10.sp)
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
    }
}

@Composable
private fun Counter(title: String, value: String, fontSize: Float, strokeWidth: Float, textColor: Color, strokeColor: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 10.sp, color = Color.LightGray)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height((fontSize * 1.65f).dp)) {
            val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = fontSize * density; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER; style = android.graphics.Paint.Style.STROKE; setStrokeWidth(strokeWidth * density); color = strokeColor.toArgb()
            }
            val y = size.height / 2f - (paint.ascent() + paint.descent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(value, size.width / 2f, y, paint)
            paint.style = android.graphics.Paint.Style.FILL; paint.color = textColor.toArgb()
            drawContext.canvas.nativeCanvas.drawText(value, size.width / 2f, y, paint)
        }
    }
}

private fun counter(start: LocalDateTime, now: LocalDateTime, daysOnly: Boolean, showHours: Boolean): String =
    CounterFormatter.format(start, now, daysOnly, showHours)
