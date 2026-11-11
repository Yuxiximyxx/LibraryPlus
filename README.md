# LibraryPlus · 资源库增强

荣耀 MagicOS 桌面「资源库」增强 LSPosed 模块。

- 包名：`io.github.yuxiximyxx.libraryplus`
- 作用域：`com.hihonor.android.launcher`（系统桌面）、`com.hihonor.hiboard`（资源库界面）
- 适配版本：桌面 18.0.0.331、资源库（hiboard）11.1.4.302；其它版本尽量通过 DexKit 自动适配
- 日志 TAG：`LibraryPlus`（`adb logcat -s LibraryPlus`，也会写入 LSPosed 日志）。每个进程加载后输出一行：`LibraryPlus loaded in <包名>, hooks ok: [...], failed: [...]`

## 功能（v0.1.0）

| 功能 | 说明 |
| --- | --- |
| 隐藏应用 | 勾选的应用不再出现在资源库的任何文件夹中（同时作用于资源库的隐藏判断与桌面提供给资源库的应用列表） |
| 文件夹改名 | 为 12 个系统分类文件夹（101–112）设置自定义名称，留空恢复默认 |
| 自定义分类 | 把任意应用放进指定的系统分类（社交通讯、游戏……） |
| 桌面右侧进入 | 在桌面最后一页任意空白处向左滑即可打开资源库，像是桌面最右边的一页；在资源库任意位置向右滑即可关闭并回到桌面 |
| 从右侧滑入动画 | 资源库改为从右往左滑入（可选） |
| 保留上滑进入 | 开启右侧进入后，可选择关闭原来的上滑进入 |

修改设置后自动保存，并通知桌面刷新资源库。

## 使用

1. 在 LSPosed 中启用模块，勾选「系统桌面」和「资源库」（hiboard）。
2. 重启桌面与资源库（或重启手机）。
3. 打开 LibraryPlus 进行设置。

> 需要 LSPosed（支持 new-xshared）。配置同时通过 XSharedPreferences、ContentProvider 与广播三种方式同步，任一可用即可生效。

## 构建

需要 JDK 17+ 与 Android SDK（compileSdk 36）。

```bash
./gradlew :app:assembleRelease
```

发布签名：在仓库根目录放置 `keystore.properties`（已被 `.gitignore` 忽略，不要提交），或用环境变量 `LP_KEYSTORE_PROPERTIES` 指向该文件：

```properties
storeFile=/path/to/libraryplus.jks
storePassword=******
keyAlias=libraryplus
keyPassword=******
```

没有签名配置时会回落到 debug 签名。

## 实现说明

- 桌面（类名未混淆）
  - `ResourceLibUtil.getAllAppList()`：从返回的 Bundle `value` 列表中移除隐藏应用
  - `DragLayer.handleResourceLibEnter()`：最后一页向左的横向手势调用 `HiBoardUtil.scrollToHiLibrary()` 并拦截（与上滑进入同一入口）
  - `Workspace.determineScrollingStart()`：最后一页向左时不开始翻页，并作为兜底入口
  - `HiBoardClientManager.onTouchEvent()` / `resetTouchEvent()`：把向左的位移换算成向上的位移，复用原有的上滑打开逻辑
  - 配置变化后调用 `ResourceLibUtil.notifyChangeToResourceLibraryNodelay()` 让资源库重新加载
- 资源库 hiboard（部分混淆，用 DexKit 按日志字符串定位，结果按 APK 指纹缓存）
  - `DealAppIFolderInfoStrategy.collectAppByFolderType()`：跳过隐藏应用、替换分类
  - 隐藏判断 usecase（`checkAppIsHidden`）：隐藏应用返回 true
  - 文件夹模型（`FolderAppsCategory`）标题 getter：返回自定义名称
  - `MainView.setTranslationY()`：开启右侧滑入时换成横向位移
  - `HiLibraryWorkSpace.dispatchTouchEvent()` / `determineStartScroll()`：向右的横向手势换算成下拉，复用原生的下拉关闭（跟手 + 吸附）
- 所有 hook 都包在 try/catch 中，失败只记日志，不影响桌面与资源库运行。

## 已知限制

- 「建议」文件夹（100）及其预览位不支持改名。
- 暂不支持新增自定义分类。
- 未在资源库 ⋮ 菜单中加入「增强设置」入口（菜单项由内部列表 + 混淆的适配器构造，注入风险较高，留待后续版本）。

## 许可

MIT
