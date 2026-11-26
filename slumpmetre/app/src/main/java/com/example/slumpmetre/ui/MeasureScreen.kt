package com.example.slumpmetre.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.slumpmetre.Marks
import com.example.slumpmetre.Record
import com.example.slumpmetre.RecordStore
import com.example.slumpmetre.SlumpMath
import java.io.File
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.min

private val Orange = Color(0xFFD97706)
private val Blue = Color(0xFF2563EB)
private val Green = Color(0xFF16A34A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureScreen(imagePath: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RecordStore(context) }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var marks by remember { mutableStateOf(Marks()) }
    var refMm by remember { mutableStateOf(300f) }
    var tilt by remember { mutableStateOf(10f) }

    LaunchedEffect(imagePath) { bitmap = decode(imagePath) }
    val bmp = bitmap

    val result = bmp?.let {
        SlumpMath.measure(marks, refMm.toDouble(), tilt.toDouble(), it.width.toDouble() / it.height)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Olcum") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } }
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { marks = Marks() }, modifier = Modifier.weight(1f)) { Text("Sifirla") }
                Button(
                    enabled = result != null,
                    onClick = {
                        val r = result ?: return@Button
                        val saved = store.persistPhoto(imagePath)
                        store.add(
                            Record(
                                id = UUID.randomUUID().toString(),
                                ts = System.currentTimeMillis(),
                                slumpMm = r.slumpMm,
                                heightMm = r.heightMm.toInt(),
                                baseMm = r.baseMm.toInt(),
                                angleDeg = r.angleDeg,
                                refMm = refMm.toDouble(),
                                imagePath = saved
                            )
                        )
                        onSaved()
                    },
                    modifier = Modifier.weight(2f)
                ) { Text("Kaydet") }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize()) {

            if (bmp == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                MarkCanvas(
                    bitmap = bmp,
                    marks = marks,
                    refMm = refMm,
                    onMarks = { marks = it },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Referans", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = refMm.toString(),
                    onValueChange = { refMm = it.toFloatOrNull() ?: refMm },
                    singleLine = true,
                    modifier = Modifier.width(90.dp)
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = { refMm = 300f }, label = { Text("koni 300") })
            }

            Text("Kamera egimi: ${tilt.toInt()}°", style = MaterialTheme.typography.bodySmall)
            Slider(value = tilt, onValueChange = { tilt = it }, valueRange = 0f..40f)

            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("tahmini cokme", style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (result != null) "${result.slumpMm} mm" else "-",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    result?.let {
                        Text(
                            "yukseklik ${it.heightMm.toInt()} mm · taban ${it.baseMm.toInt()} mm · aci %.1f° · G/Y %.1f"
                                .format(it.angleDeg, it.ratio),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!it.hasRef) {
                            Text("Referans yok, olcek varsayildi.", color = Orange, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkCanvas(
    bitmap: Bitmap,
    marks: Marks,
    refMm: Float,
    onMarks: (Marks) -> Unit,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var active by remember { mutableStateOf<String?>(null) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    fun dstRect(): Rect {
        if (canvasSize.width == 0f) return Rect.Zero
        val s = min(canvasSize.width / bitmap.width, canvasSize.height / bitmap.height)
        val w = bitmap.width * s
        val h = bitmap.height * s
        return Rect(Offset((canvasSize.width - w) / 2f, (canvasSize.height - h) / 2f), Size(w, h))
    }

    fun toScreen(p: Offset, d: Rect) = Offset(d.left + p.x * d.width, d.top + p.y * d.height)

    fun nearest(pos: Offset, d: Rect): String? {
        val cands = mapOf("r1" to marks.r1, "r2" to marks.r2, "a" to marks.apex, "b" to marks.left, "c" to marks.right)
        var best: String? = null
        var bd = 34f
        for ((k, v) in cands) {
            val s = toScreen(v, d)
            val dd = hypot(s.x - pos.x, s.y - pos.y)
            if (dd < bd) { bd = dd; best = k }
        }
        return best
    }

    Canvas(
        modifier
            .background(Color.Black)
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(marks, canvasSize) {
                detectDragGestures(
                    onDragStart = { pos -> active = nearest(pos, dstRect()) },
                    onDrag = { change, _ ->
                        val d = dstRect()
                        if (d.width == 0f) return@detectDragGestures
                        val nx = ((change.position.x - d.left) / d.width).coerceIn(0f, 1f)
                        val ny = ((change.position.y - d.top) / d.height).coerceIn(0f, 1f)
                        when (active) {
                            "r1" -> onMarks(marks.copy(r1 = Offset(nx, ny)))
                            "r2" -> onMarks(marks.copy(r2 = Offset(nx, ny)))
                            "a" -> onMarks(marks.copy(apex = Offset(nx, ny)))
                            "b" -> onMarks(marks.copy(left = Offset(nx, ny)))
                            "c" -> onMarks(marks.copy(right = Offset(nx, ny)))
                        }
                    },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null }
                )
            }
    ) {
        val d = dstRect()
        if (d.width == 0f) return@Canvas

        drawImage(
            image = image,
            dstOffset = IntOffset(d.left.toInt(), d.top.toInt()),
            dstSize = IntSize(d.width.toInt(), d.height.toInt())
        )

        val r1 = toScreen(marks.r1, d)
        val r2 = toScreen(marks.r2, d)
        val apex = toScreen(marks.apex, d)
        val left = toScreen(marks.left, d)
        val right = toScreen(marks.right, d)

        drawLine(Blue, left, right, strokeWidth = 2f)
        drawLine(Orange, r1, r2, strokeWidth = 3f)

        val dx = right.x - left.x
        val dy = right.y - left.y
        val l2 = (dx * dx + dy * dy).coerceAtLeast(0.001f)
        val t = ((apex.x - left.x) * dx + (apex.y - left.y) * dy) / l2
        val foot = Offset(left.x + t * dx, left.y + t * dy)
        drawLine(Green, apex, foot, strokeWidth = 2f)

        listOf(r1 to Orange, r2 to Orange, left to Blue, right to Blue, apex to Green).forEach { (p, c) ->
            drawCircle(Color.White, 5f, p)
            drawCircle(c, 3.5f, p)
        }

        val style = TextStyle(fontSize = 11.sp, color = Color.White)
        drawText(measurer, "${refMm.toInt()} mm", style = style, topLeft = Offset((r1.x + r2.x) / 2 + 6, (r1.y + r2.y) / 2))
        drawText(measurer, "taban", style = style, topLeft = Offset((left.x + right.x) / 2 - 14, (left.y + right.y) / 2 - 22))
        drawText(measurer, "tepe", style = TextStyle(fontSize = 11.sp, color = Green), topLeft = Offset(apex.x + 6, apex.y - 18))
    }
}

private fun decode(path: String): Bitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    var sample = 1
    while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
