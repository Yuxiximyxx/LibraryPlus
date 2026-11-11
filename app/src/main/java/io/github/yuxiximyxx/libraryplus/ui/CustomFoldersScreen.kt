package io.github.yuxiximyxx.libraryplus.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.yuxiximyxx.libraryplus.Const
import io.github.yuxiximyxx.libraryplus.LpConfig
import io.github.yuxiximyxx.libraryplus.StatusStore
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 校验分类名；返回错误提示，null 表示可用 */
private fun nameError(cfg: LpConfig, name: String, selfId: Int?): String? {
    val n = name.trim()
    if (n.isEmpty()) return "请输入名称"
    if (n.length > Const.FOLDER_NAME_MAX) return "名称最多 ${Const.FOLDER_NAME_MAX} 个字"
    val taken = cfg.customFolders.any { it.id != selfId && it.name == n } ||
        Const.FOLDERS.any { (id, def) -> def == n || cfg.customName(id) == n } || n == "建议"
    return if (taken) "已有同名分类" else null
}

/** 新建 / 重命名对话框 */
@Composable
private fun FolderNameDialog(
    show: Boolean,
    title: String,
    initial: String,
    confirmText: String,
    cfg: LpConfig,
    selfId: Int?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(show, initial) { mutableStateOf(initial) }
    var error by remember(show) { mutableStateOf<String?>(null) }
    SuperDialog(
        show = show,
        title = title,
        summary = error ?: "名称最多 ${Const.FOLDER_NAME_MAX} 个字，不能与已有分类重名",
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = text,
            onValueChange = { v -> text = v.replace("\n", "").take(Const.FOLDER_NAME_MAX); error = null },
            label = "分类名称",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            TextButton(
                text = confirmText,
                onClick = {
                    val e = nameError(cfg, text, selfId)
                    if (e != null) error = e else onConfirm(text.trim())
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 分类的 2×2 小图标（前 4 个应用） */
@Composable
private fun FolderThumb(pkgs: List<String>) {
    Box(
        Modifier.padding(end = 12.dp).size(40.dp)
            .background(MiuixTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0 until 2) {
                        val p = pkgs.getOrNull(r * 2 + c)
                        if (p != null) AppIcon(p, size = 15.dp) else Spacer(Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CustomFoldersScreen(
    cfg: LpConfig,
    update: ((LpConfig) -> LpConfig) -> Unit,
    open: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    val full = cfg.customFolders.size >= Const.CUSTOM_FOLDER_LIMIT

    MiuixPage(title = "新建分类", onBack = onBack) {
        item { SmallTitle("我的分类 · 在资源库中长按文件夹可拖动排序") }
        if (cfg.customFolders.isEmpty()) {
            item {
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth()) {
                    Text(
                        "还没有新建分类",
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        } else {
            item {
                Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    cfg.customFolders.forEach { f ->
                        val pkgs = cfg.mapping.filterValues { it == f.id }.keys.sorted()
                        SuperArrow(
                            title = f.name,
                            summary = "${pkgs.size} 个应用" + if (pkgs.isEmpty()) " · 资源库中暂不显示" else "",
                            startAction = { FolderThumb(pkgs) },
                            onClick = { open("folder:${f.id}") },
                        )
                    }
                }
            }
        }
        item {
            TextButton(
                text = if (full) "最多 ${Const.CUSTOM_FOLDER_LIMIT} 个分类" else "＋ 新建分类",
                onClick = { creating = true },
                enabled = !full,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 16.dp).fillMaxWidth(),
            )
        }
        item {
            Text(
                "新建的分类会出现在资源库里，和系统分类文件夹一样显示。\n没有应用的分类不会显示。\n删除分类后，里面的应用回到原来的系统分类。\n最多 ${Const.CUSTOM_FOLDER_LIMIT} 个。修改后若资源库未刷新，请重启作用域。",
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }

    FolderNameDialog(
        show = creating,
        title = "新建分类",
        initial = "",
        confirmText = "下一步",
        cfg = cfg,
        selfId = null,
        onDismiss = { creating = false },
        onConfirm = { name ->
            creating = false
            if (cfg.customFolders.size >= Const.CUSTOM_FOLDER_LIMIT) {
                Toast.makeText(ctx, "最多 ${Const.CUSTOM_FOLDER_LIMIT} 个分类", Toast.LENGTH_SHORT).show()
            } else {
                var newId = -1
                update { c -> c.withNewFolder(name).also { newId = it.second }.first }
                if (newId > 0) open("folder:$newId")
            }
        },
    )
}

@Composable
fun FolderDetailScreen(
    folderId: Int,
    cfg: LpConfig,
    update: ((LpConfig) -> LpConfig) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val folder = cfg.customFolder(folderId)
    if (folder == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps = AppRepo.load(ctx) }
    val statusVersion = rememberStatusVersion()
    val real = remember(statusVersion) { StatusStore.categories(ctx) }

    // 进入页面时的勾选状态决定排序，避免勾选时列表跳动
    val initialMembers = remember(folderId) { cfg.mapping.filterValues { it == folderId }.keys.toSet() }
    val count = cfg.mapping.count { it.value == folderId }

    fun origin(pkg: String): String = real[pkg]?.let { cfg.displayName(it) } ?: "未同步"

    MiuixPage(title = folder.name, onBack = onBack) {
        item {
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth()) {
                SuperArrow(
                    title = "重命名",
                    endActions = {
                        Text(
                            folder.name,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = { renaming = true },
                )
            }
        }
        item { AppSearchField(query) { query = it } }
        item { SmallTitle("已加入 $count 个 · 勾选即移入本分类") }
        val list = apps
        if (list == null) {
            item { LoadingHint() }
        } else {
            val shown = list.search(query).sortedByDescending { it.pkg in initialMembers }
            items(shown, key = { it.pkg }) { app ->
                val cur = cfg.mapping[app.pkg]
                val checked = cur == folderId
                val summary = when {
                    checked -> "原分类：" + origin(app.pkg)
                    cur != null -> "当前：" + cfg.displayName(cur) + if (Const.isCustomFolderId(cur)) "（新建）" else "（已调整）"
                    else -> "当前：" + origin(app.pkg)
                }
                val toggle = {
                    update { c ->
                        val m = c.mapping.toMutableMap()
                        if (m[app.pkg] == folderId) m.remove(app.pkg) else m[app.pkg] = folderId
                        c.copy(mapping = m, hidden = c.hidden)
                    }
                }
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth()) {
                    BasicComponent(
                        title = app.label,
                        summary = summary + if (app.pkg in cfg.hidden) " · 已隐藏" else "",
                        startAction = { AppIcon(app.pkg, Modifier.padding(end = 12.dp)) },
                        endActions = { Checkbox(state = ToggleableState(checked), onClick = { toggle() }) },
                        onClick = { toggle() },
                    )
                }
            }
        }
        item {
            TextButton(
                text = "删除分类",
                onClick = { deleting = true },
                colors = ButtonDefaults.textButtonColors(),
                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 16.dp).fillMaxWidth(),
            )
        }
    }

    FolderNameDialog(
        show = renaming,
        title = "重命名分类",
        initial = folder.name,
        confirmText = "确定",
        cfg = cfg,
        selfId = folderId,
        onDismiss = { renaming = false },
        onConfirm = { name ->
            renaming = false
            update { it.renameFolder(folderId, name) }
        },
    )

    SuperDialog(
        show = deleting,
        title = "删除「${folder.name}」",
        summary = if (count > 0) "其中的 $count 个应用会回到原来的系统分类。" else "该分类中没有应用。",
        onDismissRequest = { deleting = false },
    ) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(text = "取消", onClick = { deleting = false }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            TextButton(
                text = "删除",
                onClick = {
                    deleting = false
                    update { it.deleteFolder(folderId) }
                    onBack()
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
