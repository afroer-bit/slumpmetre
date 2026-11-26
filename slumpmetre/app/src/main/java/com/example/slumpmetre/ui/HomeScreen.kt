package com.example.slumpmetre.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.slumpmetre.Record
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(records: List<Record>, onNewPhoto: (String) -> Unit) {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let { onNewPhoto(it.path!!) }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val target = File(context.cacheDir, "gal_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        onNewPhoto(target.absolutePath)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("SlumpMetre") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {

            Text(
                "Koni yiginin yanina dik, ayni duzleme konur. Turuncu iki nokta koninin alt ve ust kenarina (300 mm), mavi iki nokta yiginin tabanina, yesil nokta tepesine.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val f = File(context.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
                    pendingUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                    camera.launch(pendingUri!!)
                }, modifier = Modifier.weight(1f)) { Text("Fotograf cek") }

                OutlinedButton(onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f)) { Text("Galeriden sec") }
            }

            Spacer(Modifier.height(20.dp))
            Text("Kayitlar", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (records.isEmpty()) {
                Text("Henuz olcum yok.", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(records, key = { it.id }) { r ->
                        val d = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(r.ts))
                        ListItem(
                            headlineContent = { Text("${r.slumpMm} mm") },
                            supportingContent = { Text("$d · yukseklik ${r.heightMm} mm · aci %.1f°".format(r.angleDeg)) },
                            modifier = Modifier.clickable { shareRecord(context, r) }
                        )
                    }
                }
            }
        }
    }
}

private fun shareRecord(context: android.content.Context, r: Record) {
    val text = """
        Slump tahmini: ${r.slumpMm} mm
        Yukseklik: ${r.heightMm} mm
        Taban: ${r.baseMm} mm
        Aci: %.1f°
        Referans: ${r.refMm} mm
    """.trimIndent().format(r.angleDeg)

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Paylas"))
}
