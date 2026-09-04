# XNote

XNote 是一个面向 Android 13 及以上手机和平板的本地优先笔记应用。当前仓库已完成 Android/Jetpack Compose 工程初始化、S1 设计系统、S2 本地数据层、S3 笔记本及普通文字笔记、S4 Markdown 单向转换、S5 回收站和搜索与 S6 笔记背景，提供统一页面骨架、公共浮层、状态组件、Room 笔记库、完整富文本编辑闭环、Markdown 编辑预览、可恢复删除、本地全文检索，以及默认背景和单篇背景覆盖；完整功能范围见 [`docs/XNote 功能清单与页面组成.md`](./docs/XNote%20功能清单与页面组成.md)，开发切片顺序见 [`docs/XNote 开发顺序.md`](./docs/XNote%20开发顺序.md)，Flutter 全量重写的执行顺序和 Agent 提示词见 [`docs/XNote Flutter 重构执行规划.md`](./docs/XNote%20Flutter%20重构执行规划.md)，单时间线 Agent 的分层记忆、文档记忆与上下文规则见 [`docs/XNote Agent 记忆与上下文架构.md`](./docs/XNote%20Agent%20记忆与上下文架构.md)。

## Flutter 重构进度

`flutter/` 已完成重构规划 F0–F6。Flutter 应用现已使用真实 Drift 本地库启动，并打通笔记首页、笔记本详情与基础文字编辑垂直闭环：支持范围和排序、最近编辑、长按多选、新建与移动笔记、笔记本统计和手动排序、重命名和删除确认，以及 450 ms 自动保存、返回强制落盘和移入回收站。Widget 测试使用依赖覆盖注入内存 Drift，不在生产代码中保留演示仓库或假数据；原生工程在最终切换阶段前继续作为功能等价参考。

## 当前基线

- Android Gradle Plugin 9.3.2、Gradle 9.7.1、JDK 17 及以上。
- `compileSdk 37`、`targetSdk 37`、`minSdk 33`。
- Kotlin/Compose Compiler 2.3.21、Compose BOM 2026.08.00。
- Room 3.0.2、`BundledSQLiteDriver`（SQLite 2.7.0，用于 FTS5）、DataStore Preferences 1.2.1、WorkManager 2.11.2、kotlinx.serialization JSON 1.11.0、kotlinx.coroutines Android/Test 1.11.0。
- AndroidLiquidGlass `io.github.kyant0:backdrop:2.0.1` 与 Shapes `1.2.1` 均固定版本。
- 手机一级导航采用 AndroidLiquidGlass 官方 catalog 的 `LiquidBottomTabs` / `LiquidBottomTab` 默认材质配方；玻璃本体为 56 dp、滑块为 48 dp，外层导航占位保持 88 dp，并用滑块路径切割出主题色图标与文字。
- AndroidLiquidGlass 发布物只提供 Backdrop/Lens 等底层能力，不打包高层组件；项目优先采用官方 catalog 已有实现，只在 catalog 没有对应组件时创建基于该库的最薄适配层。
- 界面矢量图标统一来自 Keyline Icons 提交 `14cd695f` 的 Rounded 资源，以 24 × 24 官方 SVG 为源转换为 Android `VectorDrawable`；手机 Tabbar 使用 Fill，其余界面使用 Stroke，并通过 16/20/24/40 dp 语义令牌分级。完整规则见 [UI 设计规范](./docs/XNote%20UI%20设计规范.md)。
- `XNotePageScaffold` 已统一系统安全区、页面加载/错误、Toast Host 与 AndroidLiquidGlass catalog Progressive blur；所有二级页面的 Header 与页面底部常驻同一套 128 dp 渐进模糊遮罩。
- `XNoteHeader`、Dialog、Drawer、Toast、Popup、Dropdown、加载/空/错误状态与富文本工具栏均由公共设计系统提供；系统动画倍率为 0 时取消弹性、形变和过渡动画。
- 笔记首页、笔记本详情与普通笔记编辑页已接通本地笔记库：可创建笔记本、从编辑页 Header 选择归属、编写完整富文本（含表格与标题折叠）、自动保存，并将笔记移入回收站；输入法组合文本会逐次进入文档并在返回或后台切换时强制落盘，表格结构操作会同步更新当前单元格焦点。
- 普通笔记可在移除媒体块后永久转换为 Markdown；转换事务会先保存历史版本，再按块顺序映射标题、行内样式、清单、引用、代码和 GitHub 风格表格。
- Markdown 笔记以首行一级标题同步列表标题，支持原文编辑、会话内撤销重做、右下角确认保存、结构化预览和从预览返回编辑；不提供转回普通笔记的入口。
- 全屏手机搜索与平板列表栏搜索复用 FTS5：支持标题/正文、笔记本筛选、原文片段高亮、最近搜索持久化与清空，连续中文子串可命中且回收站内容始终排除。
- “我的”已提供回收站入口；回收站展示删除时间、剩余天数和原笔记本，可恢复、永久删除、清空及多选，并在启动补扫或后台任务中清理满 30 天的笔记和未引用附件。
- 普通笔记编辑、Markdown 编辑与预览统一使用全屏 `XNoteNoteSurface` 作为页面唯一背景，纸张连续延伸到系统栏、Header 与底部工具区下方，不再叠加独立页面底色；支持暖白、奶油纹理、横线、方格四款内置纸张，“我的”可设置默认背景，单篇笔记可覆盖选择或恢复继承。

## 本地运行

1. 安装 Android Studio、JDK 17+、Android SDK Platform 37 和 Build Tools 36.0.0+。
2. 使用 Android Studio 打开仓库，等待 Gradle 同步完成。
3. 运行 `app` 配置，目标设备需为 Android 13（API 33）或更高版本。

命令行验证：

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

连接 Android 13 或更高版本的设备或模拟器后，运行真实 Android 验收：

```powershell
./gradlew.bat connectedDebugAndroidTest
```

如未设置环境变量，可先设置 Android SDK 路径：

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

## 代码结构

```text
app/src/main/java/com/xnote/app
├─ data                # Room 笔记库、附件文件、DataStore 设置/搜索历史、回收站清理
├─ domain              # 笔记文档 JSON、领域规则、纯文本/FTS/匹配片段抽取
├─ design              # 主题、令牌、Shape 与库中没有的项目级玻璃适配
│  ├─ liquidglass      # 固定版本的 AndroidLiquidGlass 官方 catalog 组件
│  └─ 公共页面骨架、Header、Progressive blur、浮层、状态与富文本工具栏
├─ feature/background  # 内置背景画布、公共选择器与默认背景设置
├─ feature/notes       # 笔记首页、笔记本详情、普通笔记编辑器与 Markdown 编辑/预览
├─ feature/search      # 手机全屏与平板列表栏搜索
├─ feature/recycle     # 回收站列表、批量操作与危险操作确认
├─ feature/profile     # “我的”中已实现功能的真实入口
├─ navigation          # 一级目的地与导航状态
├─ MainActivity.kt     # Android 入口
└─ XNoteApp.kt         # 手机/平板应用外壳
```

Flutter 重构当前完成至 F6；后续范围与验收顺序以 [Flutter 重构执行规划](./docs/XNote%20Flutter%20重构执行规划.md) 为准。
