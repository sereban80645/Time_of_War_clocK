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
    var fontSize by remember { mutableFloatStateOf(prefs.float("fontSize", 24f)) }
    var strokeWidth by remember { mutableFloatStateOf(prefs.float("strokeWidth", 2f)) }
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

    Box(Modifier.fillMaxSize().background(bgColor)) {
        if (backgroundPreset == "custom" && imagePath.isNotBlank()) {
            val bitmap = remember(imagePath) { runCatching { BitmapFactory.decodeFile(imagePath)?.asImageBitmap() }.getOrNull() }
            if (bitmap != null) Image(bitmap, contentDescription = "Власне фонове зображення", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (backgroundPreset != "color") {
            val presetRes = when (backgroundPreset) {
                "ukraine" -> R.drawable.bg_ukraine
                "camo" -> R.drawable.bg_camo
                "redblack" -> R.drawable.bg_red_black
                else -> R.drawable.bg_night
            }
            Image(painter = painterResource(presetRes), contentDescription = "Вбудований фон", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(opacity.coerceIn(0f, 1f)))
        }
        Box(Modifier.fillMaxSize()) {
        // Повноекранний живий попередній перегляд залишається під панеллю налаштувань.
        Column(
            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Text("TIME OF WAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (show2022) Counter("Повномасштабна війна", counter(Event2022, now, daysOnly, showHours), fontSize, strokeWidth, textColor, strokeColor)
        if (show2014) Counter("Війна з 2014 року", counter(Event2014, now, daysOnly, showHours), fontSize, strokeWidth, textColor, strokeColor)
        }
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_launcher_custom), contentDescription = "Іконка Time of War", modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text("НАЛАШТУВАННЯ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f))
        }
        SettingSwitch("Відлік з 24.02.2022", show2022) { show2022 = it; prefs.put("show2022", it) }
        SettingSwitch("Відлік з 20.02.2014", show2014) { show2014 = it; prefs.put("show2014", it) }
        SettingSwitch("Тільки дні", daysOnly) { daysOnly = it; prefs.put("showDaysOnly", it) }
        SettingSwitch("Показувати години", showHours) { showHours = it; prefs.put("showHour", it) }
        SettingSlider("Розмір цифр", fontSize, 12f, 48f) { fontSize = it; prefs.put("fontSize", it) }
        SettingSlider("Товщина контуру", strokeWidth, 0f, 5f) { strokeWidth = it; prefs.put("strokeWidth", it) }
        Text("Колір тексту RGB", fontSize = 11.sp, color = Color.White)
        SettingSlider("R", textR, 0f, 255f) { textR = it; prefs.put("textR", it) }
        SettingSlider("G", textG, 0f, 255f) { textG = it; prefs.put("textG", it) }
        SettingSlider("B", textB, 0f, 255f) { textB = it; prefs.put("textB", it) }
        Text("Колір контуру RGB", fontSize = 11.sp, color = Color.White)
        SettingSlider("R", strokeR, 0f, 255f) { strokeR = it; prefs.put("strokeR", it) }
        SettingSlider("G", strokeG, 0f, 255f) { strokeG = it; prefs.put("strokeG", it) }
        SettingSlider("B", strokeB, 0f, 255f) { strokeB = it; prefs.put("strokeB", it) }
        Text("Колір фону RGB", fontSize = 11.sp, color = Color.White)
        SettingSlider("R", bgR, 0f, 255f) { bgR = it; prefs.put("bgR", it) }
        SettingSlider("G", bgG, 0f, 255f) { bgG = it; prefs.put("bgG", it) }
        SettingSlider("B", bgB, 0f, 255f) { bgB = it; prefs.put("bgB", it) }
        Text("Вбудовані фони", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Button(onClick = { backgroundPreset = "night"; prefs.put("backgroundPreset", backgroundPreset) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333))) { Text("Темний") }
        Button(onClick = { backgroundPreset = "ukraine"; prefs.put("backgroundPreset", backgroundPreset) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333))) { Text("Прапор України") }
        Button(onClick = { backgroundPreset = "camo"; prefs.put("backgroundPreset", backgroundPreset) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333))) { Text("Камуфляж") }
        Button(onClick = { backgroundPreset = "redblack"; prefs.put("backgroundPreset", backgroundPreset) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333))) { Text("Чорно-червоний") }
        Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333))) { Text("Вибрати власне фото") }
        Spacer(Modifier.height(6.dp))
        }
    }
}
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color.White)
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.White.copy(alpha = 0.48f), uncheckedThumbColor = Color.LightGray, uncheckedTrackColor = Color.Black.copy(alpha = 0.35f)))
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    val step = if (label == "Товщина контуру") 0.5f else 1f
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ${"%.1f".format(value)}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.92f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, modifier = Modifier.sizeIn(minWidth = 38.dp, minHeight = 36.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.95f))) { Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max, modifier = Modifier.weight(1f).height(34.dp), colors = SliderDefaults.colors(thumbColor = Color.White.copy(alpha = 0.94f), activeTrackColor = Color.White.copy(alpha = 0.82f), inactiveTrackColor = Color.White.copy(alpha = 0.35f)))
            TextButton(onClick = { onChange((value + step).coerceAtMost(max)) }, modifier = Modifier.sizeIn(minWidth = 38.dp, minHeight = 36.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.95f))) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Counter(title: String, value: String, fontSize: Float, strokeWidth: Float, textColor: Color, strokeColor: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 9.sp, color = Color.LightGray, maxLines = 1)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height((fontSize * 0.80f).dp)) {
            val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = minOf(fontSize * density, size.height * 0.70f); typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER; style = android.graphics.Paint.Style.STROKE; setStrokeWidth(strokeWidth * density); color = strokeColor.toArgb()
            }
            val maxTextWidth = size.width * 0.96f
            val measuredWidth = paint.measureText(value)
            if (measuredWidth > maxTextWidth && measuredWidth > 0f) paint.textSize *= maxTextWidth / measuredWidth
            val y = size.height / 2f - (paint.ascent() + paint.descent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(value, size.width / 2f, y, paint)
            paint.style = android.graphics.Paint.Style.FILL; paint.color = textColor.toArgb()
            drawContext.canvas.nativeCanvas.drawText(value, size.width / 2f, y, paint)
        }
    }
}

private fun counter(start: LocalDateTime, now: LocalDateTime, daysOnly: Boolean, showHours: Boolean): String =
    CounterFormatter.format(start, now, daysOnly, showHours)
