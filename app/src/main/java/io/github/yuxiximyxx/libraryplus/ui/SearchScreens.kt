package io.github.yuxiximyxx.libraryplus.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yuxiximyxx.libraryplus.LpConfig
import io.github.yuxiximyxx.libraryplus.SearchCfg
import io.github.yuxiximyxx.libraryplus.SearchKeys
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperRadioButton
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun LpConfig.withSearch(f: (SearchCfg) -> SearchCfg): LpConfig = copy(search = f(search))

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.footnote1,
    )
}

@Composable
fun SearchCleanScreen(cfg: LpConfig, update: ((LpConfig) -> LpConfig) -> Unit, onBack: () -> Unit) {
    val s = cfg.search
    var darkText by remember { mutableStateOf(s.darkWordText) }
    MiuixPage(title = "净化", onBack = onBack) {
        item { SmallTitle("请求") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SuperSwitch(
                    title = "请求时关闭广告与个性化推荐",
                    summary = "告诉服务器不要返回广告、关闭个性化（同时关闭原生“广告卡片”）",
                    checked = s.reqNoAd,
                    onCheckedChange = { v -> update { c -> c.withSearch { it.copy(reqNoAd = v) } } },
                )
            }
        }
        item { SmallTitle("结果页隐藏") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SearchKeys.CARDS.forEach { (key, name) ->
                    SuperSwitch(
                        title = name,
                        checked = key in s.hideCards,
                        onCheckedChange = { v -> update { c -> c.withSearch { it.copy(hideCards = if (v) it.hideCards + key else it.hideCards - key) } } },
                    )
                }
            }
        }
        item { SmallTitle("首页隐藏") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SearchKeys.HOME.forEach { (key, name) ->
                    SuperSwitch(
                        title = name,
                        checked = key in s.hideHome,
                        onCheckedChange = { v -> update { c -> c.withSearch { it.copy(hideHome = if (v) it.hideHome + key else it.hideHome - key) } } },
                    )
                }
            }
        }
        item { SmallTitle("搜索框") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SuperDropdown(
                    title = "暗词",
                    summary = "搜索框里滚动显示的推荐词",
                    items = listOf("默认", "隐藏", "固定文字"),
                    selectedIndex = s.darkWord,
                    onSelectedIndexChange = { i -> update { c -> c.withSearch { it.copy(darkWord = i) } } },
                )
                if (s.darkWord == SearchKeys.DARK_FIXED) {
                    TextField(
                        value = darkText,
                        onValueChange = { v ->
                            val nv = v.replace("\n", "").take(20)
                            darkText = nv
                            update { c -> c.withSearch { it.copy(darkWordText = nv) } }
                        },
                        label = "固定显示的文字，如：搜索本机和网络",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth(),
                    )
                }
                SuperSwitch(
                    title = "隐藏 AI 搜索入口",
                    summary = "搜索框里的 YOYO / 多模态按钮",
                    checked = s.hideAi,
                    onCheckedChange = { v -> update { c -> c.withSearch { it.copy(hideAi = v) } } },
                )
                SuperSwitch(
                    title = "跳过开屏",
                    summary = "当前版本（10.0.37）没有开屏广告，无需处理",
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
        }
        item { Footnote("重新打开荣耀搜索后生效；已显示的卡片在下次搜索时隐藏。") }
    }
}

@Composable
fun SearchScopeScreen(cfg: LpConfig, update: ((LpConfig) -> LpConfig) -> Unit, onBack: () -> Unit) {
    val s = cfg.search
    val modes = listOf("跟随系统", "强制开启", "强制关闭")
    MiuixPage(title = "搜索范围", onBack = onBack) {
        item { SmallTitle("搜索来源") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SearchKeys.SCOPES.forEach { (key, name) ->
                    SuperDropdown(
                        title = name,
                        items = modes,
                        selectedIndex = s.scopeOf(key),
                        onSelectedIndexChange = { i ->
                            update { c ->
                                c.withSearch {
                                    val m = it.scope.toMutableMap()
                                    if (i == SearchKeys.SCOPE_FOLLOW) m.remove(key) else m[key] = i
                                    it.copy(scope = m)
                                }
                            }
                        },
                    )
                }
            }
        }
        item { SmallTitle("结果页标签（实验性）") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SearchKeys.TABS.forEach { (id, name) ->
                    SuperSwitch(
                        title = "隐藏「$name」",
                        checked = id in s.hideTabs,
                        onCheckedChange = { v -> update { c -> c.withSearch { it.copy(hideTabs = if (v) it.hideTabs + id else it.hideTabs - id) } } },
                    )
                }
            }
        }
        item { Footnote("“跟随系统”使用荣耀搜索自己的设置。强制开启只对已安装的应用有效。标签隐藏为实验功能，如遇异常请关闭。") }
    }
}

@Composable
fun SearchEngineScreen(cfg: LpConfig, update: ((LpConfig) -> LpConfig) -> Unit, onBack: () -> Unit) {
    val s = cfg.search
    var url by remember { mutableStateOf(s.engineUrl) }
    MiuixPage(title = "搜索引擎", onBack = onBack) {
        item { SmallTitle("网页搜索") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SearchKeys.ENGINES.forEachIndexed { i, name ->
                    SuperRadioButton(
                        name,
                        s.engine == i,
                        { update { c -> c.withSearch { it.copy(engine = i) } } },
                    )
                }
                if (s.engine == SearchKeys.ENGINE_CUSTOM) {
                    TextField(
                        value = url,
                        onValueChange = { v ->
                            val nv = v.replace("\n", "").trim()
                            url = nv
                            update { c -> c.withSearch { it.copy(engineUrl = nv) } }
                        },
                        label = "https://example.com/search?q={\$query}",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).fillMaxWidth(),
                    )
                    Text(
                        if (SearchKeys.isValidTemplate(url)) "模板有效" else "模板需以 http(s):// 开头并包含 {\$query}，否则跟随系统",
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                        color = if (SearchKeys.isValidTemplate(url)) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }
        }
        item { SmallTitle("打开方式") }
        item {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                SuperSwitch(
                    title = "在外部浏览器打开网页结果",
                    summary = "点开网页结果时交给默认浏览器",
                    checked = s.externalBrowser,
                    onCheckedChange = { v -> update { c -> c.withSearch { it.copy(externalBrowser = v) } } },
                )
            }
        }
        item { Footnote("{\$query} 会被替换为搜索词。选择“跟随系统”时使用荣耀搜索自己的引擎设置。") }
    }
}
