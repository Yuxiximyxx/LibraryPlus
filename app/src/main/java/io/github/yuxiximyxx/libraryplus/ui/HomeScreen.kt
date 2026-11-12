package io.github.yuxiximyxx.libraryplus.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.yuxiximyxx.libraryplus.LpConfig
import io.github.yuxiximyxx.libraryplus.ScopeControl
import io.github.yuxiximyxx.libraryplus.SearchKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    cfg: LpConfig,
    moduleActive: Boolean,
    update: ((LpConfig) -> LpConfig) -> Unit,
    open: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val statusVersion = rememberStatusVersion()
    val summary = remember(statusVersion) { ScopeControl.summary(ctx) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    MiuixPage(
        title = "资源库增强",
        onBack = null,
        extras = {
            SuperDialog(
                show = confirm,
                title = "重启作用域",
                summary = "将重启桌面、资源库和荣耀搜索，桌面会短暂黑屏。" + if (cfg.useRoot) "\n将先尝试 root。" else "",
                onDismissRequest = { confirm = false },
            ) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(text = "取消", onClick = { confirm = false }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        text = "重启",
                        onClick = {
                            confirm = false
                            busy = true
                            val useRoot = cfg.useRoot
                            scope.launch {
                                val msg = withContext(Dispatchers.IO) { ScopeControl.restart(ctx.applicationContext, useRoot) }
                                busy = false
                                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) {
        if (!moduleActive) {
            item {
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth()) {
                    Text(
                        "模块似乎未在 LSPosed 中激活（作用域：系统桌面、资源库、荣耀搜索）。激活后请重启作用域。",
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
        item { SmallTitle("资源库") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SuperArrow(
                    title = "隐藏应用",
                    summary = if (cfg.hidden.isEmpty()) "未隐藏任何应用" else "已隐藏 ${cfg.hidden.size} 个应用",
                    onClick = { open("hidden") },
                )
                SuperArrow(
                    title = "文件夹改名",
                    summary = if (cfg.folderNames.isEmpty()) "使用默认名称" else "已自定义 ${cfg.folderNames.count { it.value.isNotBlank() }} 个文件夹",
                    onClick = { open("rename") },
                )
                SuperArrow(
                    title = "新建分类",
                    summary = if (cfg.customFolders.isEmpty()) "在系统分类之外新建自己的分类"
                    else "已创建 ${cfg.customFolders.size} 个：" + cfg.customFolders.joinToString("、") { it.name },
                    onClick = { open("folders") },
                )
                SuperArrow(
                    title = "自定义分类",
                    summary = if (cfg.mapping.isEmpty()) "全部按系统分类" else "已调整 ${cfg.mapping.size} 个应用",
                    onClick = { open("mapping") },
                )
            }
        }
        item { SmallTitle("桌面手势") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SuperSwitch(
                    title = "最后一页左滑打开资源库",
                    summary = "资源库像一个新页面放在桌面最右边",
                    checked = cfg.rightEntry,
                    onCheckedChange = { v -> update { it.copy(rightEntry = v) } },
                )
                SuperSwitch(
                    title = "上滑打开荣耀搜索",
                    summary = if (cfg.rightEntry) "原来上滑进入资源库的位置改为打开荣耀搜索" else "需先开启左滑打开资源库",
                    checked = cfg.swipeUpSearchActive,
                    enabled = cfg.rightEntry,
                    onCheckedChange = { v -> update { it.copy(swipeUpSearch = v) } },
                )
                SuperSwitch(
                    title = "从右侧滑入动画",
                    summary = "资源库从右边滑入，而不是从底部升起",
                    checked = cfg.slideFromRight,
                    onCheckedChange = { v -> update { it.copy(slideFromRight = v) } },
                )
            }
        }
        item { SmallTitle("荣耀搜索") }
        item {
            val s = cfg.search
            val cleanCount = s.hideCards.size + s.hideHome.size + (if (s.hideAi) 1 else 0) + (if (s.darkWord != SearchKeys.DARK_DEFAULT) 1 else 0)
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SuperArrow(
                    title = "净化",
                    summary = (if (s.reqNoAd) "已关闭广告请求" else "广告请求未关闭") + (if (cleanCount > 0) " · 隐藏 $cleanCount 项" else ""),
                    onClick = { open("search_clean") },
                )
                SuperArrow(
                    title = "搜索范围",
                    summary = (if (s.scope.isEmpty()) "来源跟随系统" else "已调整 ${s.scope.size} 个来源") +
                        (if (s.onlyLocal) " · 只看本机" else if (s.hideTabs.isNotEmpty()) " · 隐藏 ${s.hideTabs.size} 个标签" else ""),
                    onClick = { open("search_scope") },
                )
                SuperArrow(
                    title = "搜索引擎 · ${SearchKeys.engineName(s.engine)}",
                    summary = if (s.externalBrowser) "网页结果在外部浏览器打开" else null,
                    onClick = { open("search_engine") },
                )
            }
        }
        item {
            TextButton(
                text = if (busy) "正在重启…" else "重启作用域",
                onClick = { if (!busy) confirm = true },
                enabled = !busy,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 18.dp).fillMaxWidth(),
            )
        }
        item {
            Text(
                if (summary.total == 0) "已生效 0/0 · ${summary.detail}" else "已生效 ${summary.ok}/${summary.total} · ${summary.detail}",
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SuperSwitch(
                    title = "使用 root 强制停止",
                    summary = "没有 root 时自动改用免 root 方式",
                    checked = cfg.useRoot,
                    onCheckedChange = { v -> update { it.copy(useRoot = v) } },
                )
            }
        }
        item {
            Text(
                "修改会自动保存并通知桌面、资源库和荣耀搜索；手势在下一次滑动时生效。荣耀搜索的大部分设置需重新打开搜索，若未生效请重启作用域。",
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }
}
