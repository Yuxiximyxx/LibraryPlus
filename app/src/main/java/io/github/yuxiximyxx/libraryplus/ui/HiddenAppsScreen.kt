package io.github.yuxiximyxx.libraryplus.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.yuxiximyxx.libraryplus.LpConfig
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppSearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        label = "搜索应用名或包名",
        useLabelAsPlaceholder = true,
        singleLine = true,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
    )
}

@Composable
fun LoadingHint(text: String = "正在读取应用列表…") {
    Text(
        text,
        modifier = Modifier.padding(28.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
fun HiddenAppsScreen(cfg: LpConfig, update: ((LpConfig) -> LpConfig) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = AppRepo.load(ctx) }

    MiuixPage(title = "隐藏应用", onBack = onBack) {
        item { AppSearchField(query) { query = it } }
        item { SmallTitle("已隐藏 ${cfg.hidden.size} 个 · 勾选的应用不会出现在资源库中") }
        val list = apps
        if (list == null) {
            item { LoadingHint() }
        } else {
            // 已隐藏的排在前面
            val shown = list.search(query).sortedByDescending { it.pkg in cfg.hidden }
            items(shown, key = { it.pkg }) { app ->
                val checked = app.pkg in cfg.hidden
                val toggle = { v: Boolean -> update { c -> c.copy(hidden = if (v) c.hidden + app.pkg else c.hidden - app.pkg) } }
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth()) {
                    BasicComponent(
                        title = app.label,
                        summary = app.pkg,
                        startAction = { AppIcon(app.pkg, Modifier.padding(end = 12.dp)) },
                        endActions = { Checkbox(state = ToggleableState(checked), onClick = { toggle(!checked) }) },
                        onClick = { toggle(!checked) },
                    )
                }
            }
        }
    }
}
