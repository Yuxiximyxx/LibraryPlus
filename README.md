# LibraryPlus · 资源库增强

荣耀 MagicOS 桌面「资源库」增强 LSPosed 模块。

- 包名：`io.github.yuxiximyxx.libraryplus`
- 作用域：`com.hihonor.android.launcher`（系统桌面）、`com.hihonor.hiboard`（资源库界面）、`com.hihonor.search`（荣耀搜索）
- 适配版本：桌面 18.0.0.331、资源库（hiboard）11.1.4.302、荣耀搜索 10.0.37.301；其它版本尽量通过 DexKit 自动适配
- 日志 TAG：`LibraryPlus`（`adb logcat -s LibraryPlus`，也会写入 LSPosed 日志）。每个进程加载后输出一行：`LibraryPlus loaded in <包名>, hooks ok: [...], failed: [...]`

## 功能（v0.1.3）

| 功能 | 说明 |
| --- | --- |
| 隐藏应用 | 勾选的应用不再出现在资源库的任何文件夹中（同时作用于资源库的隐藏判断与桌面提供给资源库的应用列表） |
| 文件夹改名 | 为 12 个系统分类文件夹（101–112）设置自定义名称，留空恢复默认 |
| 新建分类 | 在 12 个系统分类之外新建自己的分类文件夹（最多 20 个，名称 ≤12 字），可改名、勾选应用、删除（删除后应用回到系统分类）；资源库中与系统文件夹一样显示，可长按拖动排序 |
| 自定义分类 | 把任意应用放进指定的系统分类（社交通讯、游戏……）或新建分类 |
| 桌面右侧进入 | 在桌面最后一页任意空白处向左滑即可打开资源库，像是桌面最右边的一页；在资源库任意位置向右滑即可关闭并回到桌面 |
| 从右侧滑入动画 | 资源库改为从右往左滑入（可选） |
| 上滑打开荣耀搜索 | 开启右侧进入后，原来上滑进入资源库的位置改为打开荣耀搜索 |
| 自定义分类显示真实分类 | 资源库分类时把每个应用的系统分类上报给模块，列表显示「当前：xx」 |
| 重启作用域 | 一键重启桌面、资源库、荣耀搜索（免 root：模块广播让进程自行结束；可选 root：`am force-stop`），并显示各进程 hook「已生效 x/y」 |
| 荣耀搜索 · 净化 | 请求时关闭广告与个性化；按卡片类型隐藏结果页广告 / 推荐应用 / 推荐资讯 / 热搜 / 横幅 / 置顶内容 / 三方应用 / AI / 服务卡片 / 满意度调查；隐藏首页热搜 / 横幅 / 信息流 / 历史；搜索框暗词默认 / 隐藏 / 固定文字；隐藏 AI 入口 |
| 荣耀搜索 · 搜索范围 | 每个本地来源（备忘录、信息、联系人、设置、日历、文件、邮件、图库、收藏、应用内服务、小红书、大众点评）跟随系统 / 强制开 / 强制关；只看本机结果（默认开启：去掉结果页顶部整行标签，直接显示本机结果）；单独隐藏某个标签（实验性） |
| 荣耀搜索 · 搜索引擎 | 跟随系统 / 百度 / Bing / Google / 自定义 URL 模板（`{$query}` 为搜索词）；可选在外部浏览器打开网页结果 |

修改设置后自动保存，并通知桌面、资源库和荣耀搜索。

## 使用

1. 在 LSPosed 中启用模块，勾选「系统桌面」「资源库」（hiboard）和「荣耀搜索」。
2. 重启手机，或在模块里点「重启作用域」。
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
  - `DealAppIFolderInfoStrategy.collectAppByFolderType()`：跳过隐藏应用、替换分类；新建分类使用 1001 起的 id，原生代码按 id 新建文件夹（`FolderUtil.e(id)` 为 -1 时使用自定义名称）
  - 隐藏判断 usecase（`checkAppIsHidden`）：隐藏应用返回 true
  - 文件夹模型（`FolderAppsCategory`）标题 getter：返回自定义名称 / 新建分类名称
  - `MainView.setTranslationY()`：开启右侧滑入时换成横向位移
  - `HiLibraryWorkSpace.dispatchTouchEvent()` / `determineStartScroll()`：向右的横向手势换算成下拉，复用原生的下拉关闭（跟手 + 吸附）
- 桌面 `HiBoardUtil.scrollToHiLibrary(ev, up)`：开启「上滑打开荣耀搜索」后调用 `Launcher.handleClickViewEnterSearchEvent()`（与点击桌面搜索图标同一路径），失败时用 `honorsearch://com.hihonor.search/home` 兜底
- 荣耀搜索（R8 全混淆，模型 / Strategy / View 类名保留）
  - `GeneralDataReq` / `HomeHotSearchReq` 构造：supportAd=false、supportActivationAd=0、isPersonalizedClosed=true
  - 综合 tab 各 `*Strategy` 创建 / 绑定 ViewHolder 时折叠对应卡片；首页楼层按布局名折叠（`LayoutInflater.inflate`）
  - `DarkWordViewSwitcher`：清空暗词列表 / 显示固定文字
  - 搜索范围：`setting_save_file` 中的开关（SharedPreferences）、MMKV 中的小红书 / 点评开关，以及 DexKit 定位的本地搜索源列表方法
  - `SearchEngine.getUrl/getWebTabUrl/getCombineTabUrl` 返回自定义模板；`WebDetailActivity` 可转交外部浏览器
  - `ResultModel$Tab` / `ResultModel$TabContent` 构造时过滤标签（只看本机：只保留本机并选中）；`TabStrategy` 的标签行折叠
- 所有 hook 都包在 try/catch 中，失败只记日志，不影响桌面、资源库与荣耀搜索运行。

## 已知限制

- 「建议」文件夹（100）及其预览位不支持改名。
- 新建分类不显示资源库展开文件夹里的「设置」按钮；没有应用的新建分类不会显示。
- 未在资源库 ⋮ 菜单中加入「增强设置」入口（菜单项由内部列表 + 混淆的适配器构造，注入风险较高，留待后续版本）。

## 许可

MIT
