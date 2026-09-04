# XNote Flutter 重构执行规划

> 文档版本：v1.0
>
> 文档日期：2026-09-04
>
> 文档状态：可直接作为重构 Agent 的执行提示词
>
> 当前重构基线：原生 Android / Kotlin / Jetpack Compose，已完成原开发顺序 S1–S6
>
> 目标技术栈：Flutter；所有 Liquid Glass 组件统一使用 `liquid_glass_widgets`
>
> 关联文档：[功能清单与页面组成](./XNote%20功能清单与页面组成.md)、[现有开发顺序](./XNote%20开发顺序.md)、[UI 设计规范](./XNote%20UI%20设计规范.md)、[Agent 记忆与上下文架构](./XNote%20Agent%20记忆与上下文架构.md)

## 1. 给执行 Agent 的总指令

你要把 XNote 从原生 Android / Kotlin / Jetpack Compose 完整重写为 Flutter。本文是重构期间的最高优先级执行文档；产品行为仍以关联文档为事实源，但关联文档中的 Kotlin、Compose、Room、DataStore、WorkManager、AndroidLiquidGlass、Android View、Android HWUI 和 Compose Host 等实现描述全部视为旧实现，不得继续沿用。

严格遵守以下要求：

1. 先阅读仓库根目录 `AGENTS.MD`、本文和四份关联文档，再检查当前代码、测试、Git 状态与远端状态。
2. 每次开始工作都执行 `git status`、`git fetch --prune origin`，检查当前分支及上游差异，保护全部已有工作。
3. 从本文“阶段状态表”中第一个未完成阶段继续，不能跳过阶段出口条件，不能同时铺开多个尚未闭环的阶段。
4. 重写不是逐文件翻译。先复现领域行为和验收条件，再用 Dart/Flutter 的直接实现完成；不要保留 Kotlin 风格的无意义包装层。
5. 不迁移旧用户数据，不兼容旧 Room 数据库、DataStore、附件目录、序列化 JSON 或导航状态；不得编写迁移器、双读、旧格式解析器、兼容别名或回退实现。
6. 保留产品规则，不保留旧技术实现。数据库可以从 schema version 1 重新设计，测试夹具也从新格式创建。
7. 所有需要 Liquid Glass 视觉或交互的组件必须直接使用 `liquid_glass_widgets`。不得自研玻璃渲染、Shader、Backdrop、折射、模糊、高光、色散、形变、回弹或 Glass 控件。
8. 不得复制、修改、内嵌或 fork `liquid_glass_widgets` 源码，也不得在项目内创建 `XNoteGlass*`、`CustomGlass*` 等替代组件。
9. 库不存在对应玻璃组件时，优先改用库内语义最接近的组件；仍不满足时改用普通非玻璃 Flutter 组件或报告阻塞，不允许补写自定义玻璃组件。
10. “全部组件使用该库”特指全部玻璃组件。正文、列表内容、表格行、笔记纸张和普通业务内容遵循该库的设计原则保持不透明，不能为了字面上的“全部”把内容层玻璃化。
11. 当前阶段首先达到现有 S1–S6 的功能等价；阅读、导出、图片、Agent、抠图、画笔、MCP、Skill 和 Linux 属于后续产品阶段，不能阻塞 Flutter 基线切换。
12. 任何阶段都必须同步测试和相关文档。阶段完成后运行规定验证、检查最终 diff，只暂存当前阶段文件，按 `AGENTS.MD` 的中文格式提交并推送。
13. 不得用“后续补测试”“暂时保留旧实现”“先造假数据”绕过阶段出口条件。
14. 发现本文与产品文档冲突时：产品行为以产品文档为准，Flutter 技术方案与重写顺序以本文为准；仍无法同时满足时停止写入并报告具体冲突。

## 2. 已确认且不可重新讨论的决策

| 主题 | 决策 |
| --- | --- |
| 重构方式 | 全量 Flutter 重写，最终仓库不保留原生 Android 业务实现。 |
| Flutter 目录 | Flutter 项目的全部文件统一放在仓库根目录的 `flutter/` 中；不得把 `lib/`、`android/`、`ios/`、`test/`、`pubspec.yaml` 或其他 Flutter 工程文件散落到仓库根目录。 |
| 旧数据 | 不迁移、不读取、不兼容；新安装直接创建 Flutter 新数据库。 |
| 兼容层 | 不做 Kotlin/Dart 双实现、旧格式解析、适配器或迁移开关。 |
| Liquid Glass | 只使用 `liquid_glass_widgets`，不自行开发任何玻璃组件或效果。 |
| 库版本 | 初始基线固定 `liquid_glass_widgets: 1.2.3`，不使用 `^`；升级必须单独验证并提交。 |
| Flutter 版本 | Flutter 不低于 `3.41.0`；Dart 不低于包要求的 `3.5.0`，实际使用同一 Flutter SDK 自带的 Dart。 |
| 数据库 | 新建 Drift/SQLite 数据库；需要 FTS5、事务、响应式查询和后台数据库执行。 |
| 应用标识 | Android `applicationId` 和 `namespace` 保持 `com.xnote.app`，除非用户明确要求改变；这不代表兼容旧数据。 |
| 首要发布面 | 先完成 Android 13+ 手机和平板的当前功能等价；同时保持 Dart 业务层可移植，不得宣称未验收平台已经受支持。 |
| 其他平台 | 可以生成 iOS、macOS、Windows、Linux、Web 宿主；各平台只有通过插件审计和验收后才进入正式支持列表。 |
| 当前功能重构完成线 | 原生项目已实现的 S1–S6 在 Flutter 中全部通过验收，并完成旧业务代码删除。 |
| 后续功能 | 按本文后半部分继续原 S7–S13，但不与当前功能等价重写混为一个提交。 |

## 3. 已核实的当前基线

重构开始前，原生代码只作为行为参考和测试依据：

- 正式 Kotlin 源文件约 80 个、约 1.1 万行；测试 Kotlin 文件 22 个、约 2300 行。
- 已完成手机和平板应用外壳、公共设计系统、Liquid Glass 控件、导航状态、笔记本与笔记 CRUD、富文本与表格编辑、自动保存、Markdown 单向转换和预览、FTS5 搜索、最近搜索、回收站、30 天清理、默认及单篇笔记背景。
- 数据层当前包含 `notebooks`、`notes`、`notes_fts`、`note_revisions`、`attachments`。
- 普通笔记采用块模型：`TextBlock`、`TableBlock`、`ImageBlock`、`StickerBlock`、`DrawingBlock`。
- 最大重构风险不是页面导航，而是富文本选择、输入法组合态、表格焦点、撤销重做、自动保存及生命周期强制落盘。
- 尚未开始的功能包括正文图片、阅读、导出、Agent、抠图、贴纸和画笔。

重构前必须先运行原生基线测试，记录失败项；若基线自身失败，不得把失败静默归因于 Flutter：

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

有 Android 13+ 设备或模拟器时同时执行：

```powershell
./gradlew.bat connectedDebugAndroidTest
```

## 4. 重构范围与非目标

### 4.1 当前功能等价范围

Flutter 切换前必须完整复现：

- 手机三个一级目的地：笔记、Agent、我的；未实现目的地只展示明确占位，不伪造功能。
- 平板导航和当前已存在的自适应结构。
- 笔记首页、笔记本详情、普通笔记编辑、Markdown 编辑/预览、搜索、回收站、默认背景设置。
- 笔记本创建、重命名、删除、排序、统计和删除规则。
- 普通笔记标题、完整文字与表格编辑能力、折叠标题、自动保存、撤销重做。
- 普通笔记永久转换为 Markdown，以及转换前历史版本。
- 标题和正文搜索、笔记本筛选、中文连续子串、匹配片段、最近搜索。
- 回收站恢复、永久删除、清空、多选、30 天过期清理。
- 四种内置笔记背景，以及“默认背景 + 单篇覆盖”的两级解析规则。
- 浅色、深色、字体缩放、减少动画、高对比度和基本无障碍语义。

### 4.2 当前重构不包含

- 旧 Room 数据库或旧 DataStore 的数据迁移。
- 旧附件复制、旧缓存导入、旧序列化 JSON 解析。
- 为保留 Kotlin API 而建立 MethodChannel 包装。
- 云同步、账号、协作、标签、密码锁、扫描、录音和数学笔记。
- 在当前功能等价完成前提前实现原 S7–S13。
- 为追求像素一致而修改 `liquid_glass_widgets` 源码。

## 5. 技术基线与依赖策略

### 5.1 Flutter 工程

- 在仓库根目录的 `flutter/` 文件夹内创建完整的标准 Flutter application，不创建 Add-to-App module。
- Flutter 的 Dart 源码、测试、资产、`pubspec.yaml`、锁文件和全部平台宿主必须全部位于 `flutter/` 内；仓库根目录只保留项目级文档、许可证、Agent 规则以及重构完成前的原生参考工程。
- 建议命令：

```powershell
flutter create flutter --project-name xnote --org com.xnote --platforms=android,ios,windows,macos,linux,web
```

- 创建后校正 Android `applicationId` 和 `namespace` 为 `com.xnote.app`。
- Android `minSdk` 保持 33；compile/target SDK 使用当前 Flutter 与 Android 工具链支持的项目基线。
- 提交 Flutter SDK 版本约束、`pubspec.lock` 和各平台宿主文件；不提交构建产物、密钥或本机配置。
- 启用严格静态分析；警告不能以批量 ignore 掩盖。

### 5.2 依赖原则

依赖分为“已确定”“默认选择”“进入阶段前验证”三类：

| 能力 | 方案 | 约束 |
| --- | --- | --- |
| Liquid Glass | `liquid_glass_widgets: 1.2.3` | 精确锁定；Flutter ≥ 3.41；不得 fork 或自行兜底。 |
| SQLite ORM | `drift` + `drift_dev` | 新 schema；使用原生 SQLite；FTS5 必须做运行时验证。 |
| SQLite 执行 | `drift/native.dart` | 移动和桌面优先使用后台创建的 NativeDatabase；Web 单独适配。 |
| 状态管理 | `flutter_riverpod` | 使用当前 Flutter 兼容的稳定版并锁定；领域对象不得依赖 Riverpod。 |
| 导航 | `go_router` | 使用 Shell 路由表达一级目的地和独立栈；路由参数只保存稳定 ID。 |
| 路径与文件 | `path_provider`、`path` | 附件保存相对路径，不把绝对路径写入数据库。 |
| SVG 图标 | `flutter_svg` | 使用 Keyline 原始 SVG，不把 Android VectorDrawable 当 Flutter 资产。 |
| ID | 经过维护状态核实的 UUID 包 | ID 在创建时生成，测试可注入确定性生成器。 |
| 图片选择 | Flutter 官方或维护良好的相机/图片选择包 | 进入图片阶段再添加，不提前引入。 |
| 安全凭据 | 系统安全存储插件 | 进入 Agent 服务商阶段再添加，API Key 不进入普通数据库。 |
| 分享和保存 | 维护良好的跨平台插件 | 进入导出阶段再添加，并逐平台验收。 |

执行 Agent 在增加任何依赖前必须：

1. 用 Context7 或官方文档核实当前 API、Flutter 版本和平台支持。
2. 检查维护状态、许可证、发布频率和已知平台限制。
3. 只加入当前阶段实际使用的依赖。
4. 使用兼容的精确版本或提交锁文件，禁止无依据升级整个依赖树。
5. 在 `THIRD_PARTY_NOTICES.md` 中同步需要声明的许可证。

### 5.3 Drift 与 FTS5 的硬要求

- 使用 `NativeDatabase.createInBackground` 或等价的 Drift 后台执行方式，避免数据库工作阻塞 UI isolate。
- 在 Drift 代码生成配置中启用 FTS5 静态分析，但不能把静态分析通过误当作运行时 FTS5 可用。
- 启动测试必须查询 SQLite compile options 或实际创建 FTS5 虚拟表，证明运行时支持。
- 中文索引继续采用项目现有的 CJK 预处理规则；“笔记本”必须命中“我的笔记本”。
- 所有涉及笔记事实源、派生摘要、字数和 FTS 的保存必须在同一事务中保持一致。
- 测试使用独立的 `NativeDatabase.memory()`，每个测试关闭数据库及 Stream。
- schema version 从 1 开始，只为新的 Flutter schema 服务，不包含原生数据库迁移。

## 6. 目标工程结构

以下目录是职责边界，不要求创建空目录或占位文件：

```text
flutter/
├─ lib/
│  ├─ main.dart
│  ├─ app/
│  │  ├─ bootstrap.dart
│  │  ├─ xnote_app.dart
│  │  ├─ routing/
│  │  └─ theme/
│  ├─ core/
│  │  ├─ database/
│  │  ├─ files/
│  │  ├─ ids/
│  │  ├─ time/
│  │  └─ errors/
│  ├─ domain/
│  │  ├─ model/
│  │  ├─ document/
│  │  ├─ markdown/
│  │  ├─ rules/
│  │  └─ text/
│  ├─ data/
│  │  ├─ notes/
│  │  ├─ settings/
│  │  ├─ search/
│  │  └─ maintenance/
│  ├─ design/
│  │  ├─ tokens/
│  │  ├─ icons/
│  │  ├─ background/
│  │  └─ common/
│  └─ features/
│     ├─ notes/
│     ├─ editor/
│     ├─ markdown/
│     ├─ search/
│     ├─ recycle_bin/
│     ├─ profile/
│     ├─ reading/
│     ├─ export/
│     ├─ media/
│     ├─ agent/
│     └─ settings/
├─ test/
│  ├─ domain/
│  ├─ data/
│  ├─ design/
│  └─ features/
└─ integration_test/
   ├─ notes_flow_test.dart
   ├─ editor_persistence_test.dart
   ├─ search_recycle_test.dart
   └─ adaptive_layout_test.dart
```

### 6.1 依赖方向

```text
features / design
       ↓
application controllers
       ↓
domain interfaces and rules
       ↓
data implementations
       ↓
Drift / files / platform plugins
```

- Domain 层只能依赖 Dart SDK，不得导入 Flutter、Riverpod、Drift 或平台插件。
- Data 层实现 Domain 接口，UI 不直接调用 Drift 表或 SQL。
- Controller 负责用例编排、异步状态和生命周期，Widget 只渲染状态并发出意图。
- 编辑器文档变换、Markdown 转换、搜索文本处理和回收站规则必须是纯 Dart，可直接单元测试。
- 不为只有一个实现的简单函数制造接口；只有数据库、文件、时钟、ID、模型服务和平台能力需要可替换边界。

### 6.2 Dart 文件结构

每个非空源文件按仓库规则组织，使用实际存在的章节：

```dart
// -- Type Definitions
// -- Inputs and Outputs
// -- Constants
// -- State and Variables
// -- Derived Values
// -- Functions
// -- Listeners
// -- Lifecycle Hooks
```

不要为了满足顺序创建空标题；文件过长或职责混合时按责任拆分。

## 7. 领域与数据设计

### 7.1 新数据库表

当前功能等价阶段至少创建：

| 表 | 关键字段 |
| --- | --- |
| `notebooks` | id、name、sortIndex、createdAt、updatedAt |
| `notes` | id、notebookId、title、kind、documentJson、markdownText、backgroundKey、sortIndex、字数、摘要、createdAt、updatedAt、deletedAt、originalNotebookName |
| `notes_fts` | noteId、title、body；FTS5 虚拟表 |
| `note_revisions` | id、noteId、reason、kind、title、documentJson、markdownText、createdAt |
| `attachments` | id、kind、mimeType、originalFileName、relativePath、byteSize、width、height、createdAt |
| `search_history` | query、usedAt；限制数量并去重 |

设置可以使用单独的轻量存储；模型凭据必须使用安全存储。不要因为“不迁移旧数据”而忽略未来 schema version、外键、索引和删除规则。

### 7.2 普通笔记文档模型

新 Dart 模型保留以下业务语义，但 JSON 格式可以重新设计：

```text
NoteDocument
├─ TextBlock
│  ├─ paragraphStyle: body | heading | subheading | monospace
│  ├─ alignment: left | center | right
│  ├─ listMarker: none | bullet | dash | numbered | checklist
│  ├─ indent、quoted、collapsed、checked
│  └─ InlineRun[]
├─ TableBlock
│  └─ TableRow[] → TableCell[] → InlineRun[]
├─ ImageBlock
├─ StickerBlock
└─ DrawingBlock
```

`InlineRun` 支持文本、粗体、斜体、下划线、删除线、高亮和链接。所有模型使用明确的枚举和不可变值对象；未知类型在新 schema 中属于错误，不做旧格式容错。

### 7.3 必须保持的领域规则

- 未归档笔记的 `notebookId == null`。
- 删除空笔记本：直接永久删除笔记本，不创建回收站条目。
- 删除非空笔记本：笔记本永久删除，其笔记进入回收站并保存原笔记本名称快照；恢复后为未归档。
- 单篇笔记进入回收站后，原笔记本仍存在则恢复原位置，否则恢复为未归档。
- 回收站笔记不参与普通列表、搜索、阅读、统计和 Agent Tool。
- 30 天清理由启动补扫保证最终执行，平台后台调度只作为尽力补充。
- 普通笔记存在图片、贴纸或画笔时禁止转 Markdown；表格允许转换。
- 转 Markdown 前保存历史版本；转换不可逆。
- 笔记背景解析：`note.backgroundKey ?? settings.defaultBackgroundKey`。
- 修改默认背景只影响没有单篇覆盖的笔记；新建笔记不复制默认背景值。
- 字数只统计可见正文及表格单元格；不统计笔记标题、图片文件名和画笔。
- 数据库时间保存 UTC epoch；展示时按设备本地时区格式化。

## 8. Liquid Glass 唯一实现规范

### 8.1 初始化

`main()` 必须在 `runApp` 前初始化库并用库提供的根包装器：

```dart
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await LiquidGlassWidgets.initialize();

  runApp(
    LiquidGlassWidgets.wrap(
      brightnessResolver: Theme.maybeBrightnessOf,
      adaptiveQuality: true,
      theme: GlassThemeData.simple(
        quality: GlassQuality.standard,
      ),
      child: const XNoteApp(),
    ),
  );
}
```

实际参数以已锁定版本 API 为准。MaterialApp 必须提供 `brightnessResolver`，避免应用主题与系统亮度不一致时玻璃边框或阴影消失。

### 8.2 组件映射

| 旧原生语义 | Flutter 必用组件 |
| --- | --- |
| `XNotePageScaffold` | `GlassScaffold` |
| `XNoteHeader` | `GlassAppBar` |
| `LiquidBottomTabs` / `LiquidBottomTab` | `GlassTabBar.bottom` 与 `GlassTab` |
| 平板 Navigation Rail / 多栏导航壳 | `GlassNavigationShell`，按库现有 API 组合 |
| `LiquidButton` | `GlassButton` / `GlassIconButton` / `GlassButtonGroup` |
| 胶囊筛选与选择 | `GlassChip` / `GlassSegmentedControl` / `GlassPullDownButton` |
| 开关与数值控制 | `GlassSwitch` / `GlassSlider` |
| 公共工具栏 | `GlassToolbar` |
| 搜索输入 | `GlassSearchBar` |
| 玻璃文本输入 | `GlassTextField` / `GlassTextArea` / `GlassFormField` |
| Panel / Card / 分组 | `GlassContainer` / `GlassCard` / `GlassGroupedSection` |
| 可点击玻璃行 | `GlassListTile` |
| Dialog | `GlassDialog` |
| 手机底部抽屉 | `GlassSheet` / `GlassModalSheet` |
| 操作列表 | `showGlassActionSheet` |
| Dropdown / Popup | `GlassMenu` / `GlassPopover` |
| Toast | `GlassToast` |
| 加载指示 | `GlassProgressIndicator` |
| 顶部和底部渐进模糊 | `ProgressiveBlur` |

业务组件允许直接组合库组件，例如富文本格式栏使用 `GlassToolbar` 加多个 `GlassIconButton`；这属于业务编排，不得复制库的材质或绘制代码。

### 8.3 禁止项

- 禁止 `BackdropFilter`、`FragmentProgram`、自定义 GLSL、CustomPainter 玻璃层或手写折射效果。
- 禁止复制库组件后改名。
- 禁止为库组件套一层新的玻璃容器；该库明确不支持把交互式玻璃控件嵌入 `GlassCard`/`GlassContainer` 形成嵌套折射。
- 禁止在滚动列表大量使用 premium 质量。
- 禁止把正文列表、表格行、笔记纸张和文章卡片全部玻璃化。
- 禁止逐页面覆盖模糊、厚度、形状和高光参数；统一从 `GlassThemeData` 管理。

### 8.4 质量与性能

- 默认使用 `GlassQuality.standard`。
- AppBar、TabBar 等由 `GlassScaffold` 管理的导航层可按库默认提升质量。
- `GlassQuality.premium` 只用于静态、非滚动的焦点区域；不得放入 `ListView`、`CustomScrollView` 或高频重建区域。
- 重复列表背景如确实需要玻璃，只能用 `minimal` 或改为普通不透明内容。
- 启用 adaptive quality，并在低端 Android、GLES 回退、Vulkan、横竖屏和热降频场景验收。
- 记录 profile 模式的 UI/raster 帧耗时；60 Hz 设备单帧预算为 16.67 ms，目标是正常滚动无持续超预算和无首帧 Shader 卡顿。
- 减少动画时依赖库的无障碍处理，并额外验证业务动画已关闭或改为即时状态变化。

### 8.5 形状与内容层

- 玻璃组件形状完全由库管理，不再强制套用原 Android 的 60% SmoothCornerShape。
- 非玻璃业务容器使用 Flutter 内建连续圆角能力和语义半径令牌，不手写 Bézier 或 superellipse。
- 笔记正文保持稳定、不透明和高可读；玻璃只用于浮动导航与控制层。
- 背景、玻璃捕获层和前景内容必须保持正确绘制顺序，优先让 `GlassScaffold` 处理，不自行搭建采样栈。

## 9. 导航、响应式和平台行为

### 9.1 导航

- 使用三个一级 Shell 分支：Notes、Agent、Profile，各自保留独立子导航栈。
- 手机使用 `GlassTabBar.bottom`，搜索为独立入口。
- 二次点击当前一级目的地时：长列表回到顶部或清空该目的地子栈；减少动画时即时完成。
- 二级页面使用 `GlassAppBar`；普通和 Markdown 编辑页标题位于正文，AppBar 中间不得重复标题。
- 路由参数只保存 noteId、notebookId 等稳定值；Widget、数据库实体和大 JSON 不放入路由状态。
- 支持系统返回、Android 预测性返回、深链恢复所需的确定性路由状态。

### 9.2 响应式断点

以窗口宽度而不是设备名称决定布局，初始断点：

| 宽度 | 布局 |
| --- | --- |
| `< 600` | 手机单栏，底部导航，底部 Sheet |
| `600–899` | 平板紧凑双栏或受限多栏，侧向/锚定浮层 |
| `≥ 900` | 宽屏多栏，允许侧边导航、笔记本、笔记列表和正文并列 |

断点是项目初始值，需通过真机/模拟窗口验收后调整。禁止用 `Platform.isAndroid` 代替窗口尺寸判断。

### 9.3 平台边界

- Android 13+ 是当前正式验收平台。
- iOS、桌面和 Web 宿主可以保留，但只有完成数据库、文件、后台任务、图片、分享、安全存储和 Agent 网络插件审计后才能标记为支持。
- 平台能力通过窄接口隔离；不得把 `dart:io` 直接散布到业务和 Web 可编译代码。
- Linux 沙箱属于后续 S13 的平台特有能力，不应伪装成所有 Flutter 平台通用能力。

## 10. 编辑器专项规范

富文本编辑器是重构的阻断性技术风险，必须先做 PoC 再全面实现。

### 10.1 编辑器分层

- `NoteDocument` 和所有编辑命令是纯 Dart、不可变输入输出。
- `EditorSessionController` 管理当前文档、选区、输入样式、撤销重做、保存状态和生命周期。
- Widget 层管理 Flutter `TextEditingController`、FocusNode 和 composing range，但不得直接修改数据库。
- 所有文档操作返回新的文档与新的有效选区；表格结构变化必须同时返回下一焦点单元格。
- 自动保存使用版本号防止旧异步写入覆盖新状态。

### 10.2 输入法与保存

- 输入法组合态的每次可见变化立即进入内存文档，不得等 composing 结束。
- 同步 Controller 时保留 selection 和 composing，避免中文候选被重置。
- 默认可沿用 450 ms 防抖保存；返回、路由离开、应用 `inactive`/`paused`/`detached` 前必须强制 flush。
- 强制 flush 不能被普通 debounce 取消；失败状态可见并可重试。
- 进程被杀无法保证回调执行，因此 debounce 写入必须足够短且每次写入为事务。

### 10.3 撤销、表格与折叠

- 撤销重做是编辑会话状态，不写进历史版本表。
- 连续输入按块和时间合并历史；结构命令、格式命令、表格增删和标题改变形成明确快照边界。
- 表格默认 2×2；单元格只含文字与行内样式。
- 增删行列后选区落在仍有效的原单元格或相邻单元格；删除整表后创建/聚焦相邻正文块。
- 折叠标题只隐藏其后直到同级或更高级标题之前的块，不删除内容。
- 所有命令都必须有纯 Dart 单元测试，并覆盖空文档、边界选区和 Unicode 文本。

## 11. 重写阶段状态表

执行 Agent 每完成一个阶段，更新状态、日期和验证结果。提交哈希写入阶段汇报；避免为了把当前提交哈希回写本文而产生额外提交。只有出口条件全部通过才能标为完成。

| 阶段 | 内容 | 初始状态 |
| --- | --- | --- |
| F0 | 原生基线、资产和行为清点 | 已完成（2026-09-04；基线命令通过，115 项旧测试已分配 Flutter 验收） |
| F1 | Flutter 工程与质量门禁 | 进行中（2026-09-04；六平台工程、格式、分析、测试及 Android debug/release 构建通过；Android 13+ 实机启动待验收） |
| F2 | Liquid Glass 与富文本阻断性 PoC | 未开始 |
| F3 | 纯 Dart 领域模型和规则 | 未开始 |
| F4 | Drift 数据、FTS5、设置和文件层 | 未开始 |
| F5 | 主题、图标、Glass 应用壳和响应式导航 | 未开始 |
| F6 | 笔记本、笔记列表与基础编辑闭环 | 未开始 |
| F7 | 完整富文本、表格、折叠与自动保存 | 未开始 |
| F8 | Markdown 与不可逆转换 | 未开始 |
| F9 | 搜索、回收站、背景与当前功能等价验收 | 未开始 |
| F10 | 移除原生业务实现并完成 Flutter 切换 | 未开始 |
| F11 | 图片基础插入与变换（原 S7） | 未开始 |
| F12 | 阅读、导出、平板收口、统计与外观（原 S8–S10） | 未开始 |
| F13 | Agent、记忆和笔记工具（原 S11） | 未开始 |
| F14 | 抠图、贴纸、环绕布局与画笔（原 S12） | 未开始 |
| F15 | MCP、Skill 与 Linux（原 S13） | 未开始 |

## 12. 各阶段详细执行要求

### F0 原生基线、资产和行为清点

**目标：** 建立可验证的参考，不修改产品行为。

**事项：**

- 运行原生单元测试、lint、debug 构建和可用的仪器测试。
- 记录当前页面、路由、领域模型、数据库表、设置键、图标、纸张背景和测试清单。
- 对 S1–S6 关键路径录制截图或测试说明：新建笔记本、新建笔记、输入中文、格式、表格、转换、搜索、删除与恢复、背景继承。
- 建立“旧行为 → Flutter 测试”追踪表；每个旧测试必须对应新测试或写明因技术实现被删除的原因。
- 核对 Keyline 图标固定提交与许可证。

**出口：** 原生基线结果可复现，全部当前功能都有 Flutter 验收归属。

### F1 Flutter 工程与质量门禁

**目标：** 建立可构建的纯 Flutter application。

**事项：**

- 在仓库根目录的 `flutter/` 中创建 Flutter 工程和平台宿主，设置 applicationId、minSdk、应用名、图标和主题入口；不得在仓库根目录生成 Flutter 工程文件。
- 配置 Flutter/Dart 版本约束、严格 lint、格式化、单元测试、Widget 测试和 integration_test。
- 加入 `liquid_glass_widgets: 1.2.3` 并锁定依赖。
- 建立 CI/本地统一命令，不引入业务页面。
- 确认 Android debug 和 release 构建链路；记录 Flutter doctor 信息但不提交本机路径。

**验证：**

以下命令在 `flutter/` 目录执行：

```powershell
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test
flutter build apk --debug
```

**出口：** 空应用在 Android 13+ 启动，无分析错误，`flutter/` 工程结构稳定，仓库根目录没有散落的 Flutter 工程文件。

### F2 Liquid Glass 与富文本阻断性 PoC

**目标：** 在投入全面移植前验证两个最高风险。

**PoC A：Liquid Glass**

- 初始化 `LiquidGlassWidgets`，建立 `GlassScaffold`。
- 同屏验证 `GlassAppBar`、`GlassTabBar.bottom`、`GlassToolbar`、`GlassIconButton`、`GlassMenu`、`GlassModalSheet`、`GlassToast` 和 `ProgressiveBlur`。
- 验证浅色/深色、系统亮度与应用 ThemeMode 不同、减少动画、字体放大、横屏和低端设备质量降级。
- 验证滚动区域不使用 premium，玻璃层没有嵌套折射和裁剪错误。

**PoC B：编辑器**

- 一个标题、两个正文块、行内粗体、列表、2×2 表格、标题折叠。
- 中文拼音组合输入、Emoji、换行、跨样式选择、表格增删行列和焦点转移。
- 450 ms 自动保存、立即返回强制保存、撤销重做。
- 工具栏必须使用 `GlassToolbar` 与库内按钮。

**出口：** 两个 PoC 在真机通过。若库 API 缺失或编辑器无法稳定保留 composing/selection，停止全面重构并提交具体复现，不得用自定义玻璃兜底。

PoC 代码只有能直接演进为正式实现时才保留；一次性实验应在阶段结束前删除。

### F3 纯 Dart 领域模型和规则

**目标：** 不依赖 Flutter UI 复现产品事实。

**事项：**

- 建立 Notebook、Note、Revision、Attachment、AppSettings、NoteDocument 和全部 Block 模型。
- 实现 ID、时钟注入、可见文本抽取、中文/英文统计、列表摘要、搜索片段与高亮范围。
- 移植文档编辑命令：文本替换、删除、样式、链接、清单、缩进、引用、对齐、表格、折叠和选区恢复。
- 实现 EditorHistory、MarkdownEditorHistory 和普通笔记转 Markdown。
- 实现笔记本删除、回收站恢复、过期判断、背景解析等规则。
- JSON 只服务新 Flutter 数据；新 schema 必须有 round-trip 和非法输入测试。

**出口：** 原 `app/src/test/.../domain` 与编辑器模型测试的业务语义在 Dart 测试中全部覆盖。

### F4 Drift 数据、FTS5、设置和文件层

**目标：** 新 Flutter 数据事实源可独立运行。

**事项：**

- 建立第 7 节表、外键、索引、FTS5 虚拟表和事务仓库。
- 实现 NotebookRepository、NoteRepository、SearchHistoryRepository、SettingsRepository、AttachmentFileStore。
- 保存笔记时原子更新文档、摘要、字数和 FTS。
- 实现删除笔记本、移动、多选、转 Markdown、永久删除和未引用附件清理事务。
- 启动时补扫过期回收站；后台调度通过平台接口延后接入，正确性不能依赖定时器。
- 测试全部查询 Stream、事务失败回滚、FTS 中文连续子串和数据库关闭。

**出口：** 使用内存数据库的仓库测试完整通过，Android 真机 FTS5 验收通过。

### F5 主题、图标、Glass 应用壳和响应式导航

**目标：** 所有页面建立统一 Flutter 导航与视觉基础。

**事项：**

- 建立语义颜色、排版、间距、图标尺寸、浅色/深色和背景令牌。
- 主题主色：浅色 `#E09F3E`，深色 `#FFD60A`。
- 从固定 Keyline 提交恢复 Rounded SVG；手机 Tab 使用 Fill，其余使用 Stroke；保留 24×24 视口和许可证。
- 直接使用 `GlassScaffold`、`GlassAppBar`、`GlassTabBar.bottom`、`GlassNavigationShell` 等库组件，不创建项目玻璃包装器。
- 手机实现三个一级目的地与独立搜索；平板实现侧边导航和内容区骨架。
- 建立加载、空、错误等普通业务状态；它们不是玻璃时使用普通 Flutter 内容组件。
- 接入字体缩放、RTL、键盘避让、SafeArea、系统返回和状态栏样式。

**出口：** 手机/平板、浅色/深色、横竖屏、200% 字号和减少动画的应用壳 Widget/golden 验收通过。

### F6 笔记本、笔记列表与基础编辑闭环

**目标：** 完成第一个真实垂直切片。

**事项：**

- 笔记首页真实读取数据库，支持范围、排序、最近编辑、空状态、长按多选和新建。
- 笔记本详情支持统计、排序、手动排序、重命名和删除确认。
- 普通编辑页先实现标题、基础正文、选择笔记本、保存状态、返回强制落盘和移入回收站。
- 禁止长期存在假仓库或演示数据；Widget 测试通过依赖覆盖注入内存仓库。
- Glass 弹层分别使用库的 Dialog、Sheet、Menu、Popover、Toast。

**出口：** 冷启动后数据存在；创建、编辑、移动、删除笔记本和多选路径集成测试通过。

### F7 完整富文本、表格、折叠与自动保存

**目标：** 达到当前原生编辑器能力等价。

**事项：**

- 段落：正文、标题、小标题、等宽。
- 行内：粗体、斜体、下划线、删除线、链接、高亮。
- 结构：圆点/虚线无序、有序、检查清单、引用、多级缩进。
- 对齐：左、中、右。
- 表格：默认 2×2，增删行列、删除表格、单元格焦点与选区恢复。
- 标题和小标题折叠。
- `GlassToolbar` 反映当前选区状态；不可用命令必须禁用。
- 中文输入法组合态、应用生命周期、保存错误和撤销重做全部真机验收。

**出口：** 编辑器纯 Dart 测试、Widget 测试、真机输入测试和进程重开持久化测试全部通过。

### F8 Markdown 与不可逆转换

**目标：** 复现当前 Markdown 闭环。

**事项：**

- Markdown 保存原文，首行一级标题同步列表标题。
- 编辑/预览双模式、会话撤销重做和确认保存。
- 结构化预览支持当前文档所需标题、段落、清单、引用、代码、高亮和 GitHub 风格表格。
- 普通笔记转换前检查媒体 Block、保存 Revision，并在一个事务内写入 Markdown、摘要、字数和 FTS。
- 转换后不可恢复普通模式，不保留反向兼容入口。

**出口：** 原 Markdown 解析、标题和转换规则的 Dart 测试全部通过，转换集成测试证明事务一致性。

### F9 搜索、回收站、背景与当前功能等价验收

**目标：** 完成原生 S1–S6 的全部 Flutter 等价功能。

**事项：**

- 手机全屏搜索、平板列表栏搜索、自动聚焦、最近搜索、筛选、片段高亮和清空。
- 回收站列表、剩余天数、原笔记本名称、恢复、永久删除、清空和多选。
- 四款内置背景：暖白、奶油纹理、横线、方格；浅色/深色分别定义纸色和纹理。
- 普通编辑、Markdown 编辑/预览使用同一个全屏背景解析器；背景延伸到系统栏和控制层下方。
- 默认背景与单篇覆盖的实时预览和继承恢复。
- 启动补扫过期回收站和未引用附件。
- 对照 F0 追踪表逐项验收 S1–S6。

**出口：** 所有现有功能追踪项已通过，Flutter 版本可以独立构建和运行，不调用原生业务实现。

### F10 移除原生业务实现并完成 Flutter 切换

**目标：** 仓库只保留 Flutter 产品实现。

**删除范围：**

- 根目录旧 `app/` Kotlin/Compose 模块。
- 旧根 Gradle 工程、Room schema、Compose 资源和 AndroidLiquidGlass catalog 源码。
- 仅服务旧原生工程的测试、配置和说明。

Flutter `flutter/android/` 宿主中由 Flutter 创建的最小 Activity 和插件配置可以保留；不得残留 Compose 产品 UI、Room 或旧数据兼容代码。

**文档同步：**

- 重写 README 的技术栈、运行命令、代码结构和当前基线。
- 将功能清单、开发顺序、UI 规范和 Agent 架构中的 Android/Compose/Room/WorkManager/AndroidLiquidGlass 描述替换为最终 Flutter 方案。
- UI 规范中的全部玻璃组件改为 `liquid_glass_widgets` 对应组件，删除允许项目级玻璃适配的旧规则。
- 保留产品决定和仍有效的验收规则，不保留纠错历史或“旧版”标签。

**验证：**

```powershell
rg -n "AndroidLiquidGlass|androidx\.compose|androidx\.room3|XNoteLiquidGlassPanel|LiquidBottomTabs" .
Push-Location flutter
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test
flutter test integration_test -d <android-device-id>
flutter build apk --debug
flutter build apk --release
Pop-Location
```

`rg` 只允许命中确有必要的历史提交说明；最终产品文档和业务源码不得命中旧实现名。

**出口：** 当前功能的 Flutter 重构正式完成并推送；从此后续开发只在 Flutter 上进行。

### F11 图片基础插入与变换（原 S7）

- 图片来源：相机、相册；贴纸入口等 F14。
- 应用私有目录复制原始资源，数据库只保存稳定 ID 和相对路径。
- 块级插入、缩放、旋转、移动、删除、替换、复制和层级。
- 手机来源选择使用库的 Glass Sheet，平板使用 Glass Menu/Popover。
- 权限拒绝、文件丢失、EXIF 方向、大图内存和重复引用必须测试。
- 当前阶段不做抠图、贴纸、文字环绕和画笔。

### F12 阅读、导出、平板收口、统计与外观（原 S8–S10）

- 阅读模式按笔记本顺序分页，每篇从新页开始，媒体块不跨页切断。
- 窗口、字号、方向改变时重新分页，分页结果不写数据库。
- 导出短笔记单图、长笔记多页；固定任务开始时背景快照；不导出 Glass 控件。
- 预览、最终图片和阅读复用同一内容布局与背景渲染器。
- 完成平板四栏结构、我的、统计、存储占用和外观设置。
- 图片保存、系统分享和桌面/Web 能力逐平台审计；未支持平台明确禁用并解释。

### F13 Agent、记忆和笔记工具（原 S11）

严格按《XNote Agent 记忆与上下文架构》实现，技术替换如下：

- Room 表改为 Drift 表，WorkManager 改为“启动补扫 + 可用平台的后台调度接口”。
- Agent 消息、运行、Tool Call 和权限事件以追加式事实源保存。
- 片段、摘要任务、画像、遗忘标记、文档记忆索引和 FTS 均保持原数据语义。
- 模型 API Key 进入安全存储，不进入 Drift、日志、FTS 或错误文本。
- Token 预算、权限先过滤后检索、防提示注入、删除语义和崩溃恢复不得因 Flutter 重写弱化。
- 实施顺序仍为：事实源 → 片段与上下文 → 情景记忆 → 画像 → 文档记忆 → 记忆工具 → 笔记工具与编辑联动 → 故障和权限验收。

### F14 抠图、贴纸、环绕布局与画笔（原 S12）

- 进入阶段前完成模型许可证、包体、内存、算子和平台支持审计。
- 抠图模型通过独立服务接口接入；模型推理不得阻塞 UI isolate。
- 支持结果预览、蒙版添加/擦除、透明 PNG/WebP、贴纸库和引用计数。
- 实现图片文字环绕、浮动、层级以及画板的笔、橡皮、颜色、粗细和撤销重做。
- Glass 控件仍只使用库组件；画布、蒙版和选区是业务渲染，不属于玻璃组件限制。

### F15 MCP、Skill 与 Linux（原 S13）

- 先实现远程 Streamable HTTP MCP 和纯指令型 Skill。
- MCP 授权、OAuth、超时、取消、审计与工具权限独立于笔记三级权限。
- 可执行 Skill 和本地 stdio MCP 只有在 Linux 沙箱完成后开放。
- Linux 是平台特有能力；进入阶段前确定 Android 实现路径、许可证、磁盘、网络和进程隔离。
- 不支持 Linux 环境的平台不能展示虚假可用状态。

## 13. 测试与验收矩阵

### 13.1 每个阶段的最低命令

以下命令在 `flutter/` 目录执行：

```powershell
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test
flutter build apk --debug
```

涉及真实平台、数据库、输入法、文件、Shader、分享或权限时，必须增加指定设备的 integration_test 或人工验收记录。

### 13.2 测试层次

| 层次 | 覆盖 |
| --- | --- |
| 纯 Dart 单元测试 | 文档编辑、Markdown、搜索文本、字数、删除恢复、背景、Token 与权限规则 |
| Drift 测试 | 表约束、事务、FTS5、响应式 Stream、清理、并发保存 |
| Widget 测试 | 页面状态、Glass 组件编排、导航、选区工具栏、响应式布局、语义 |
| Golden 测试 | 关键页面浅/深色、手机/平板、正常/大字号；对 Shader 差异设置合理容差 |
| Integration Test | 新建—编辑—重启、搜索、删除—恢复、Markdown 转换、背景继承 |
| 真机专项 | 中文 IME、预测性返回、键盘/浮层、Vulkan/GLES、低端机、减少动画 |

### 13.3 必测设备状态

- Android 13 最低版本与当前目标 SDK 设备。
- 窄屏手机、横屏手机、600–899 宽度平板、≥900 宽屏。
- 浅色、深色、跟随系统；应用主题与系统主题相反。
- 默认字号、200% 字号、高对比度、减少动画、RTL。
- 软件键盘显示/隐藏、中文拼音组合态、Emoji、外接键盘。
- 正常数据库、空数据库、写入失败、磁盘空间不足、损坏附件。
- Glass standard/minimal/adaptive 降级与滚动压力。

### 13.4 当前功能关键验收流

1. 创建笔记本 → 在其中创建笔记 → 输入中文最后一个组合字符 → 立即返回 → 重启后内容完整。
2. 对正文应用全部行内/段落/列表样式 → 撤销重做 → 冷启动后结构一致。
3. 插入 2×2 表格 → 增删行列 → 输入焦点仍在有效单元格 → 删除表格后可继续正文输入。
4. 删除非空笔记本 → 笔记只出现在回收站 → 展示原名称 → 恢复为未归档。
5. 普通笔记转 Markdown → 转换前 Revision 存在 → 标题、表格和样式映射正确 → 不可转回。
6. 搜索中文连续子串、标题、正文和笔记本筛选 → 回收站内容不可命中。
7. 修改默认背景 → 未覆盖笔记变化、已覆盖笔记不变 → 清除覆盖后继承默认。
8. 打开每一种 Dialog/Sheet/Menu/Popover/Toast → 均来自 `liquid_glass_widgets`，无项目自绘玻璃。

## 14. 性能、无障碍和安全

### 14.1 性能

- 大列表使用惰性构建，避免在 build 中解析整篇文档或执行数据库查询。
- NoteDocument JSON 解析、导出、图片处理、模型推理和大量文本索引不得阻塞 UI isolate。
- 编辑输入时不重建整个应用壳；观察粒度限定到当前笔记或当前块。
- 数据库 Stream 只返回页面所需字段，列表不加载完整文档 JSON。
- Glass 不嵌套，滚动列表不使用 premium，按包建议启用自适应质量。
- 使用 Flutter DevTools 的 frame chart、CPU、memory 和 raster 视图记录问题，不能只凭肉眼宣称流畅。

### 14.2 无障碍

- 图标按钮具有本地化语义；同一操作不能被图标和文字重复朗读。
- 触控目标至少 44×44 logical pixels。
- 屏幕阅读顺序：返回、标题、右侧操作、正文、底部操作。
- 选中、错误、危险、禁用和加载状态不能只靠颜色表达。
- Glass 之上的文本和图标必须保持对比度；高对比模式可降低透明度效果，但仍通过库主题完成。
- 键盘 Tab、Space/Enter、TalkBack/VoiceOver 和 RTL 行为需要按实际支持平台验证。

### 14.3 安全与隐私

- API Key、OAuth token、密码和秘密参数不得进入普通数据库、日志、崩溃报告或 FTS。
- 用户笔记、历史消息和工具结果作为不可信数据，不能覆盖系统规则或权限。
- 文件路径必须位于应用允许目录，所有外部文件先复制或建立受控授权，不保存失效的临时 URI。
- 永久删除后清理无引用附件和派生索引；回收站内容不能通过搜索或 Agent 召回泄漏。

## 15. Git、提交与文档规则

- 每个 F 阶段至少一个可独立回退和验证的提交；大型阶段按垂直切片拆分，不按“model/ui/test”横向拆分成不可运行状态。
- 不修改、格式化或暂存阶段范围之外的用户文件。
- 每次提交前检查：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- <本阶段文件>
```

- 只暂存当前阶段文件，使用 `AGENTS.MD` 要求的中文提交格式，并推送当前分支。
- 推送被拒绝时重新 fetch，只允许安全、非破坏性同步；禁止 force push。
- 阶段完成后更新第 11 节状态表与验证命令结果，并在阶段汇报中给出提交哈希。
- F10 前，旧文档可以继续描述当前原生基线；F10 必须一次性同步为最终 Flutter 状态，不能让 README 和实际工程冲突。

## 16. 必须停止并报告的条件

遇到以下情况不能自行扩大方案：

1. 工作树有会被当前阶段覆盖的用户未提交修改。
2. 上游领先但无法安全 fast-forward，或分支已经分叉。
3. `liquid_glass_widgets` 当前锁定版本缺少必须的组件或在目标设备稳定崩溃；不得自研玻璃兜底。
4. Flutter 3.41+、Android SDK 或构建环境无法满足依赖要求。
5. FTS5 在目标运行时不可用，且需要改换数据库方案。
6. 编辑器 PoC 无法稳定处理中文 composing、选区或表格焦点。
7. 某跨平台插件要求扩大收集数据、权限或网络范围。
8. 原产品文档之间存在会导致不同用户行为的冲突。
9. 需要购买、授权或接受新的商业模型/SDK 条款。

报告必须包含：复现步骤、实际结果、预期结果、已验证替代方案、影响阶段和最小用户决策；不能只说“做不了”。

## 17. Flutter 重构完成定义

只有同时满足以下条件，才能宣布“Flutter 重构完成”：

- F0–F10 全部完成并有对应提交。
- 当前原生 S1–S6 的功能追踪表全部通过。
- 标准 Flutter application 完整位于仓库根目录的 `flutter/` 中，仓库根目录没有散落的 Flutter 工程文件，Android 13+ debug/release 构建成功。
- `flutter analyze`、全部单元/Widget 测试和规定 integration_test 通过。
- 旧 Kotlin/Compose/Room/AndroidLiquidGlass 产品实现已删除，不存在双栈或兼容入口。
- 不存在旧数据库、DataStore、附件或 JSON 迁移代码。
- 所有玻璃组件均直接来自 `liquid_glass_widgets`，项目中没有自定义玻璃绘制和组件副本。
- 数据库、富文本、Markdown、搜索、回收站和背景行为符合产品规则。
- 手机/平板、浅色/深色、大字号、减少动画、中文输入法和无障碍关键路径已验收。
- README、功能清单、开发顺序、UI 规范和 Agent 架构已经同步到 Flutter 实现。
- 最终 diff 已审查，提交使用规定格式并成功推送。

F11–F15 是重构后的产品后续路线，不影响“现有功能已完成 Flutter 重构”的判断；它们分别按自身出口条件交付。

## 18. Agent 每次继续工作的固定开场模板

将本文交给新的执行 Agent 后，可附加以下指令：

```text
请严格按照 docs/XNote Flutter 重构执行规划.md 执行。
先完整阅读 AGENTS.MD、重构规划及其关联文档，运行 Git/远端检查，
从阶段状态表中第一个未完成阶段开始。不要跳阶段，不要迁移旧数据，
不要保留兼容层，不要自行编写任何 Liquid Glass 组件或效果。
完成当前阶段的代码、测试、文档、diff 审查、中文提交和 push 后，
再汇报阶段结果与下一阶段入口条件。
```

## 19. 技术参考

- [Flutter 官方文档](https://docs.flutter.dev/)
- [Flutter 应用架构](https://docs.flutter.dev/app-architecture)
- [Flutter Integration testing](https://docs.flutter.dev/testing/integration-tests)
- [Flutter 响应式与自适应设计](https://docs.flutter.dev/ui/adaptive-responsive)
- [`liquid_glass_widgets` 1.2.3](https://pub.dev/packages/liquid_glass_widgets)
- [`liquid_glass_widgets` 源码与文档](https://github.com/sdegenaar/liquid_glass_widgets)
- [Drift 官方文档](https://drift.simonbinder.eu/)
- [Drift 平台支持](https://drift.simonbinder.eu/platforms/)
- [Keyline Icons](https://keylineicons.com/icons)
