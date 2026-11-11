package io.github.yuxiximyxx.libraryplus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.yuxiximyxx.libraryplus.ConfigStore
import io.github.yuxiximyxx.libraryplus.Const
import io.github.yuxiximyxx.libraryplus.LpConfig
import io.github.yuxiximyxx.libraryplus.StatusStore
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MappingScreen(cfg: LpConfig, update: ((LpConfig) -> LpConfig) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = AppRepo.load(ctx) }
    val statusVersion = rememberStatusVersion()
    val real = remember(statusVersion) { StatusStore.categories(ctx) }
    val syncTime = remember(statusVersion) { StatusStore.categoriesTime(ctx) }

    // 下拉项：默认 + 12 个系统分类（显示自定义名称）+ 新建分类
    fun nameOf(id: Int): String = cfg.displayName(id)
    val ids = listOf(-1) + Const.FOLDERS.map { it.first } + cfg.customFolders.map { it.id }
    val options = listOf("默认") + ids.drop(1).map { id -> if (Const.isCustomFolderId(id)) "${nameOf(id)}（新建）" else nameOf(id) }

    MiuixPage(title = "自定义分类", onBack = onBack) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (syncTime > 0) "上次同步 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(syncTime)) else "尚未同步",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
                TextButton(
                    text = "刷新",
                    onClick = {
                        ConfigStore.sendSigned(ctx, Const.ACTION_REQUEST_CATEGORIES, Const.HIBOARD_PKG)
                        ConfigStore.sendSigned(ctx, Const.ACTION_REQUEST_CATEGORIES, Const.LAUNCHER_PKG)
                    },
                    colors = ButtonDefaults.textButtonColors(),
                )
            }
        }
        if (real.isEmpty()) {
            item {
                Card(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()) {
                    Text(
                        "请先打开一次资源库",
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
        item { AppSearchField(query) { query = it } }
        item { SmallTitle("为应用指定资源库中的分类，已调整 ${cfg.mapping.size} 个") }
        val list = apps
        if (list == null) {
            item { LoadingHint() }
        } else {
            val shown = list.search(query).sortedByDescending { it.pkg in cfg.mapping }
            items(shown, key = { it.pkg }) { app ->
                val sel = ids.indexOf(cfg.mapping[app.pkg] ?: -1).coerceAtLeast(0)
                val cur = real[app.pkg]?.let { "当前：" + nameOf(it) } ?: "当前：未同步"
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth()) {
                    SuperDropdown(
                        items = options,
                        selectedIndex = sel,
                        title = app.label,
                        summary = cur + if (app.pkg in cfg.mapping) " (已映射)" else "",
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
