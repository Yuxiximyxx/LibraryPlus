package io.github.yuxiximyxx.libraryplus.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yuxiximyxx.libraryplus.LpConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    cfg: LpConfig,
    moduleActive: Boolean,
    update: ((LpConfig) -> LpConfig) -> Unit,
    open: (String) -> Unit,
) {
    MiuixPage(title = "资源库增强", onBack = null) {
        if (!moduleActive) {
            item {
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth()) {
                    Text(
                        "模块似乎未在 LSPosed 中激活（作用域：系统桌面、资源库）。激活后请重启桌面与资源库。",
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 12.dp).padding(top = 6.dp).fillMaxWidth()) {
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
                    title = "自定义分类",
                    summary = if (cfg.mapping.isEmpty()) "全部按系统分类" else "已调整 ${cfg.mapping.size} 个应用",
                    onClick = { open("mapping") },
                )
            }
        }
        item { SmallTitle("桌面右侧进入") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SuperSwitch(
                    title = "在桌面最后一页左滑打开资源库",
                    summary = "资源库像一个新页面放在桌面最右边",
                    checked = cfg.rightEntry,
                    onCheckedChange = { v -> update { it.copy(rightEntry = v) } },
                )
                SuperSwitch(
                    title = "从右侧滑入动画",
                    summary = "资源库从右边滑入，而不是从底部升起",
                    checked = cfg.slideFromRight,
                    onCheckedChange = { v -> update { it.copy(slideFromRight = v) } },
                )
                SuperSwitch(
                    title = "保留上滑进入",
                    summary = if (cfg.rightEntry) "关闭后只能从右侧进入" else "需先开启右侧进入才能关闭",
                    checked = cfg.keepSwipeUp || !cfg.rightEntry,
                    enabled = cfg.rightEntry,
                    onCheckedChange = { v -> update { it.copy(keepSwipeUp = v) } },
                )
            }
        }
        item {
            Text(
                "修改会自动保存并通知桌面刷新资源库；右侧进入与动画在下一次手势时生效。若未生效，请在 LSPosed 中重启桌面与资源库。",
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }
}
