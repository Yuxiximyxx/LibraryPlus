package io.github.yuxiximyxx.libraryplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.yuxiximyxx.libraryplus.ui.HiddenAppsScreen
import io.github.yuxiximyxx.libraryplus.ui.HomeScreen
import io.github.yuxiximyxx.libraryplus.ui.MappingScreen
import io.github.yuxiximyxx.libraryplus.ui.RenameScreen
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initial = ConfigStore.load(this)
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.System) }
            var cfg by remember { mutableStateOf(initial) }
            var page by rememberSaveable { mutableStateOf("home") }
            val update: ((LpConfig) -> LpConfig) -> Unit = { f -> cfg = ConfigStore.save(this, f(cfg)) }

            MiuixTheme(controller = controller) {
                BackHandler(enabled = page != "home") { page = "home" }
                val back = { page = "home" }
                when (page) {
                    "hidden" -> HiddenAppsScreen(cfg, update, back)
                    "rename" -> RenameScreen(cfg, update, back)
                    "mapping" -> MappingScreen(cfg, update, back)
                    else -> HomeScreen(cfg, ConfigStore.xsharedAvailable, update) { page = it }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ConfigStore.flush(this)
    }
}
