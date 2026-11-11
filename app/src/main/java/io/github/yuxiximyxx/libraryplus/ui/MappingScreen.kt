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
import androidx.compose.ui.unit.dp
import io.github.yuxiximyxx.libraryplus.Const
import io.github.yuxiximyxx.libraryplus.LpConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperDropdown

@Composable
fun MappingScreen(cfg: LpConfig, update: ((LpConfig) -> LpConfig) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = AppRepo.load(ctx) }

    // 下拉项：默认 + 12 个分类（显示自定义名称）
    val options = listOf("默认") + Const.FOLDERS.map { (id, def) -> cfg.customName(id) ?: def }
    val ids = listOf(-1) + Const.FOLDERS.map { it.first }

    MiuixPage(title = "自定义分类", onBack = onBack) {
        item { AppSearchField(query) { query = it } }
        item { SmallTitle("为应用指定资源库中的分类，已调整 ${cfg.mapping.size} 个") }
        val list = apps
        if (list == null) {
            item { LoadingHint() }
        } else {
            val shown = list.search(query).sortedByDescending { it.pkg in cfg.mapping }
            items(shown, key = { it.pkg }) { app ->
                val sel = ids.indexOf(cfg.mapping[app.pkg] ?: -1).coerceAtLeast(0)
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth()) {
                    SuperDropdown(
                        items = options,
                        selectedIndex = sel,
                        title = app.label,
                        summary = app.pkg,
                        startAction = { AppIcon(app.pkg, Modifier.padding(end = 12.dp)) },
                        onSelectedIndexChange = { i ->
                            update { c ->
                                val m = c.mapping.toMutableMap()
                                if (i <= 0) m.remove(app.pkg) else m[app.pkg] = ids[i]
                                c.copy(mapping = m)
                            }
                        },
                    )
                }
            }
        }
    }
}
