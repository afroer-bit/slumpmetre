package com.example.slumpmetre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.slumpmetre.ui.HomeScreen
import com.example.slumpmetre.ui.MeasureScreen
import com.example.slumpmetre.ui.SlumpTheme

sealed interface Screen {
    data object Home : Screen
    data class Measure(val path: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SlumpTheme { App() } }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val store = remember { RecordStore(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var records by remember { mutableStateOf<List<Record>>(emptyList()) }

    LaunchedEffect(screen) { records = store.load() }

    when (val s = screen) {
        Screen.Home -> HomeScreen(records = records, onNewPhoto = { screen = Screen.Measure(it) })
        is Screen.Measure -> MeasureScreen(
            imagePath = s.path,
            onBack = { screen = Screen.Home },
            onSaved = { screen = Screen.Home }
        )
    }
}
