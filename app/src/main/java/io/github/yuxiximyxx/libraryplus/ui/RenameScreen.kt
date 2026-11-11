package io.github.yuxiximyxx.libraryplus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yuxiximyxx.libraryplus.Const
import io.github.yuxiximyxx.libraryplus.LpConfig
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RenameScreen(cfg: LpConfig, update: ((LpConfig) -> LpConfig) -> Unit, onBack: () -> Unit) {
    // 输入框本地状态，避免每个字符都回写导致光标跳动
    var resetKey by remember { mutableStateOf(0) }
    MiuixPage(title = "文件夹改名", onBack = onBack) {
        item { SmallTitle("留空即使用默认名称（“建议”文件夹不支持改名）") }
        items(Const.FOLDERS, key = { "${it.first}-$resetKey" }) { (id, def) ->
            var text by remember(resetKey) { mutableStateOf(cfg.folderNames[id].orEmpty()) }
            Card(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "$def  ·  $id",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TextField(
                        value = text,
                        onValueChange = { v ->
                            val nv = v.replace("\n", "").take(12)
                            text = nv
                            update { c ->
                                val m = c.folderNames.toMutableMap()
                                if (nv.isBlank()) m.remove(id) else m[id] = nv
                                c.copy(folderNames = m)
                            }
                        },
                        label = def,
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            TextButton(
                text = "恢复默认",
                onClick = {
                    update { it.copy(folderNames = emptyMap()) }
                    resetKey++
                },
                colors = ButtonDefaults.textButtonColors(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth(),
            )
        }
    }
}
