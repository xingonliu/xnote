# XNote Flutter 原生基线与验收追踪

> 基线日期：2026-09-04
>
> 基线提交：`82fb68f`
>
> 用途：冻结原生 S1–S6 的可观察行为，并为 Flutter F1–F10 提供测试归属。本文只描述重构参考，不建立旧数据或旧 API 兼容要求。

## 1. 基线结论

- Git 分支为 `main`，与 `origin/main` 同步，开始清点时工作树干净。
- `./gradlew.bat testDebugUnitTest lintDebug assembleDebug` 执行成功；55 个 Gradle task 中 1 个执行、54 个命中缓存或为最新状态。
- ADB 未发现已连接设备，因此本次未重复运行 `connectedDebugAndroidTest`。用户已明确确认 S1–S6 可用，并要求不再进行原生真机回归。
- 原生测试共 22 个 Kotlin 测试文件、115 个 `@Test`。第 8 节为每个源测试文件建立 Flutter 目标；同一行内的全部测试方法按原方法名和断言语义一对一迁移，不因实现语言变化降低覆盖。
- Flutter SDK 已定位到 `C:\Users\Administrator\.codex\tools\flutter-3.44.3\flutter`；F1 使用该 SDK，并在创建工程前复核版本与 doctor 状态。

## 2. 当前页面与导航

### 2.1 已实现页面

| 产品页面 | 原生入口 | 当前能力 | Flutter 归属 |
| --- | --- | --- | --- |
| P01 笔记首页 | `NotesHomeScreen` | 范围、排序、最近编辑、列表、空状态、多选、新建 | F6 |
| P02 笔记本详情 | `NotebookDetailScreen` | 统计、排序、手动排序、重命名、删除、新建笔记 | F6 |
| P03 普通笔记编辑 | `NoteEditorScreen` | 标题、完整文字样式、表格、折叠、自动保存、撤销重做 | F6–F7 |
| P04 Markdown | `MarkdownNoteScreen` | 编辑、确认保存、结构化预览、返回编辑 | F8 |
| P14 搜索 | `SearchScreen` | 标题/正文、笔记本筛选、片段、最近搜索 | F9 |
| P15 我的 | `ProfileScreen` | 回收站、默认背景入口与未实现能力占位 | F5、F9 |
| P17 回收站 | `RecycleBinScreen` | 恢复、永久删除、清空、多选、删除元数据 | F9 |
| P25 外观子集 | `DefaultBackgroundScreen` | 默认背景选择与预览 | F9 |
| Agent 一级目的地 | `PlaceholderScreen` | 明确占位，不伪造 Agent 能力 | F5 |

P05–P13、P16、P18–P24 尚不属于当前 S1–S6 基线；按重构规划 F11–F15 或对应后续阶段交付。

### 2.2 导航状态

- 一级目的地：`Notes`、`Agent`、`Profile`。
- Notes 栈：`Home`、`Notebook(notebookId)`、`Editor(noteId)`；只保存稳定 ID。
- 独立二级状态：全屏搜索、回收站、外观设置。
- 手机使用底部 Tab；宽度达到 600 dp 时切换左侧 Rail。
- 搜索打开时保留当前一级目的地；平板搜索保留 Rail 并在内容区展开。
- 返回优先级：回收站多选、笔记多选、搜索、回收站、外观、Notes 子栈。
- 二次选择当前一级目的地时，Notes 清空子栈，其他长列表回到顶部；减少动画时即时完成。

Flutter 在 F5 使用三个 Shell 分支表达一级目的地，在 F6–F9 补齐稳定 ID 子路由和二级页面；不得把 Widget、数据库实体或文档 JSON 放入路由状态。

## 3. 领域模型与行为

### 3.1 模型

- 笔记库：`Notebook`、`NotebookStats`、`Note`、`NoteKind`、`NoteListSort`、`NoteRevision`、`RevisionReason`、`Attachment`、`AttachmentKind`、`NoteSearchResult`。
- 设置：`AppSettings`、`ThemeMode`、`BackgroundKey`。
- 文档：`NoteDocument`、`NoteBlock`、`TextBlock`、`TableBlock`、`TableRow`、`TableCell`、`ImageBlock`、`StickerBlock`、`DrawingBlock`。
- 富文本：`InlineRun`、`InlineMarks`、`InlineMark`、`ParagraphStyle`、`TextAlignment`、`ListMarker`、`MediaLayout`。
- 编辑：`EditorSelection`、`EditorChange`、`EditorSnapshot`、`EditorHistory`、`MarkdownEditorHistory`。
- 规则与派生值：`NotebookDeletionPatch`、`ConversionBlocker`、`VisibleTextStats`、可见文本、FTS 文本、搜索片段及 Markdown 元数据。

### 3.2 必须保持的事实

| 领域 | 原生可观察行为 | Flutter 验收阶段 |
| --- | --- | --- |
| 未归档 | `notebookId == null` | F3、F4、F6 |
| 删除空笔记本 | 直接删除，不产生回收站条目 | F3、F4、F6 |
| 删除非空笔记本 | 笔记本永久删除；活动笔记进回收站并保存原名称 | F3、F4、F6、F9 |
| 恢复单篇笔记 | 原笔记本存在则恢复原处，否则未归档 | F3、F4、F9 |
| 恢复随笔记本删除的笔记 | 始终恢复为未归档 | F3、F4、F9 |
| 回收站隔离 | 不进入普通列表、搜索、统计或 Agent Tool | F4、F9 |
| 回收站期限 | 30 天；启动补扫保证最终清理 | F3、F4、F9 |
| 字数 | 正文和表格可见文字；不含标题和媒体 | F3、F4 |
| 自动保存 | composing 变化立即入内存；450 ms 防抖；返回和暂停强制落盘 | F2、F6、F7 |
| 撤销重做 | 会话内历史；连续输入可合并，结构命令形成边界 | F2、F3、F7、F8 |
| 表格 | 默认 2×2；结构变化后焦点落在有效单元格或相邻正文 | F2、F3、F7 |
| 折叠 | 标题后直到同级或更高级标题前的块仅隐藏、不删除 | F2、F3、F7 |
| 转 Markdown | 媒体块阻止；表格允许；先写 Revision；不可逆 | F3、F4、F8 |
| 搜索 | 标题/正文/笔记本筛选；中文连续子串可命中；回收站排除 | F3、F4、F9 |
| 背景 | `note.backgroundKey ?? settings.defaultBackgroundKey` | F3、F4、F9 |
| 默认背景变化 | 只影响无单篇覆盖的笔记；新笔记不复制默认值 | F3、F4、F9 |

## 4. 数据与设置清单

### 4.1 Room schema version 1

| 表 | 关键内容 | Flutter 归属 |
| --- | --- | --- |
| `notebooks` | ID、名称、手动顺序、创建/更新时间 | F4 |
| `notes` | 归属、标题、类型、文档/Markdown、背景、顺序、字数、摘要、时间、删除元数据 | F4 |
| `notes_fts` | noteId、标题、正文；FTS5 `unicode61` | F4 |
| `note_revisions` | noteId、原因、类型、标题、文档/Markdown 快照、创建时间 | F4 |
| `attachments` | 类型、MIME、原名、相对路径、大小、像素尺寸、创建时间 | F4 |

Flutter 使用全新的 Drift schema version 1，不读取上述 Room 文件，不迁移旧 JSON，也不保留双读入口。重构规划要求的 `search_history` 在 Flutter schema 中独立建表。

### 4.2 原生设置键

| DataStore | 键 | 语义 | Flutter 归属 |
| --- | --- | --- | --- |
| `xnote_settings` | `default_background_key` | 默认内置背景 | F4、F9 |
| `xnote_settings` | `theme_mode` | `system` / `light` / `dark` | F4、F5 |

这些键只用于行为清点。Flutter 设置存储不读取原 DataStore。

## 5. 资产与许可证

### 5.1 Keyline 图标

- 固定来源提交：`14cd695f3f2bbe320bbe7a01e65b251df7ba52cf`。
- 风格：手机 Tab 使用 Rounded Fill；其余使用 Rounded Stroke；视口均为 24×24。
- 许可证：MIT，全文已收录在 `THIRD_PARTY_NOTICES.md`。
- 当前 20 个 Keyline 业务资源：`file-text`、`star`、`user`、`square-pen`、`search`、`arrow-left`、`plus`、`more-horizontal`、`check`、`chevron-down`、`chevron-right`、`bin`、`arrow-u-turn-left`、`arrow-u-turn-right`、`inbox`、`grip-vertical`、`square`、`square-check`，以及导航所需的 Fill/Stroke 变体。
- `ic_launcher` 使用 Keyline `square-pen` 轮廓。F1 恢复启动图标，F5 从固定提交恢复原始 SVG，不把 Android VectorDrawable 当作 Flutter 资产。

### 5.2 内置纸张背景

| 稳定 ID | 名称 | 渲染语义 |
| --- | --- | --- |
| `default` | 暖白纸 | 无纹理，初始默认 |
| `cream` | 奶油纹理 | 低对比细密纸纤维 |
| `ruled` | 横线纸 | 低对比横线 |
| `grid` | 方格纸 | 低对比方格 |

纸张由代码渲染，无外部位图资产；浅色与深色分别使用成对纸色和纹理色。Flutter 在 F5 建立令牌，F9 完成共用背景渲染与继承验收。

## 6. S1–S6 关键路径说明

以下说明代替本次重复录屏，并作为后续 integration_test 的固定脚本。每项都必须从空的新 Flutter 数据库开始，不使用原生数据。

1. 新建笔记本，在其中新建笔记，输入中文且在最后一个字符仍处于 composing 时立即返回；重启后标题、正文和归属完整。
2. 对正文应用全部段落、行内、清单、引用、缩进和对齐样式；撤销、重做并冷启动，文档结构不变。
3. 插入 2×2 表格，编辑单元格，增删行列并删除整表；焦点始终落在有效单元格或相邻正文，之后仍可输入。
4. 将无媒体普通笔记转为 Markdown；转换前 Revision 存在，标题、表格与样式按映射生成，转换后只能编辑/预览 Markdown。
5. 搜索标题、正文、中文连续子串和指定笔记本；结果展示原文片段，回收站内容不命中。
6. 删除非空笔记本；笔记只在回收站出现并展示原名称，恢复后为未归档；删除空笔记本不产生条目。
7. 单篇删除后，在原笔记本仍存在和已不存在两种情况下分别恢复，归属符合规则；永久删除、清空和多选均生效。
8. 修改默认背景后，未覆盖笔记立即变化、已有覆盖不变；清除单篇覆盖后重新继承；四款背景在浅色和深色均可辨识且正文可读。
9. 手机验证三个一级目的地、全屏搜索和底部导航；600–899 与 ≥900 宽度验证侧向导航、搜索内容区和 Notes 层级。
10. 在 200% 字号、减少动画、浅/深色和键盘显示状态重复编辑与浮层路径；一次系统返回只关闭最上层浮层。

## 7. 旧行为到 Flutter 验收矩阵

| 原生能力 | 单元/仓库测试 | Widget 测试 | Integration / 设备验收 |
| --- | --- | --- | --- |
| 导航与自适应外壳 | 路由编码、返回状态 | 手机/平板 Shell、二次 Tab | `adaptive_layout_test.dart` |
| Glass 组件编排 | 不迁移 Android 材质实现测试 | F2 PoC 与 F5 组件语义 | F2 真机 Glass 清单 |
| 文档编辑 | 全部纯 Dart 命令和历史 | 选区、工具栏、焦点 | `editor_persistence_test.dart` |
| 笔记库事务 | Drift 内存库、失败回滚、Stream | 依赖覆盖状态 | `notes_flow_test.dart` |
| Markdown | 解析、转换、Revision 事务 | 编辑/预览状态 | `notes_flow_test.dart` |
| 搜索与历史 | FTS5、CJK、片段、去重上限 | 搜索页状态 | `search_recycle_test.dart` |
| 回收站 | 删除/恢复/过期/附件规则 | 列表、多选、危险确认 | `search_recycle_test.dart` |
| 背景与设置 | 解析、继承、持久化 | 四预设与主题 | `notes_flow_test.dart` |
| 输入法与生命周期 | 版本化保存控制器 | composing/selection 保持 | Android 13+ 中文 IME 真机 |

## 8. 旧测试文件一对一迁移表

每行覆盖源文件中的全部 `@Test`；目标文件必须保留同名或明确可追溯的测试描述和相同业务断言。AndroidLiquidGlass/Compose 的实现细节不迁移，但其用户可观察语义进入 F2/F5 Widget 或设备验收。

| 数量 | 原生测试文件 | Flutter 目标 | 阶段 |
| ---: | --- | --- | --- |
| 1 | `data/AppSettingsStoreInstrumentedTest.kt` | `test/data/settings_repository_test.dart` | F4 |
| 14 | `data/NoteLibraryInstrumentedTest.kt` | `test/data/note_repository_test.dart`，跨页面路径补充到 integration_test | F4、F6、F8、F9 |
| 1 | `data/SearchHistoryStoreInstrumentedTest.kt` | `test/data/search_history_repository_test.dart` | F4 |
| 13 | `NotesFlowTest.kt` | `integration_test/notes_flow_test.dart`、`editor_persistence_test.dart`、`search_recycle_test.dart` | F6–F9 |
| 6 | `XNoteAppTest.kt` | `test/features/app_shell_test.dart`、`integration_test/adaptive_layout_test.dart` | F5 |
| 11 | `XNoteDesignSystemTest.kt` | `test/design/glass_components_test.dart`、`test/design/app_shell_test.dart` | F2、F5、F7 |
| 3 | `design/XNotePopupPositionTest.kt` | `test/design/glass_overlays_test.dart` | F2、F5 |
| 4 | `domain/document/EditorHistoryTest.kt` | `test/domain/editor_history_test.dart` | F3 |
| 6 | `domain/document/InlineEditingTest.kt` | `test/domain/inline_editing_test.dart` | F3 |
| 13 | `domain/document/NoteDocumentEditingTest.kt` | `test/domain/note_document_editing_test.dart` | F3 |
| 1 | `domain/document/NoteDocumentJsonTest.kt` | `test/domain/note_document_json_test.dart` | F3 |
| 4 | `domain/markdown/RichNoteMarkdownTest.kt` | `test/domain/rich_note_markdown_test.dart` | F3、F8 |
| 3 | `domain/model/BackgroundKeyTest.kt` | `test/domain/background_key_test.dart` | F3 |
| 5 | `domain/rules/MarkdownConversionRulesTest.kt` | `test/domain/markdown_conversion_rules_test.dart` | F3 |
| 6 | `domain/rules/NotebookRulesTest.kt` | `test/domain/notebook_rules_test.dart` | F3 |
| 4 | `domain/rules/RecycleBinPolicyTest.kt` | `test/domain/recycle_bin_policy_test.dart` | F3 |
| 3 | `domain/text/FtsIndexTextTest.kt` | `test/domain/fts_index_text_test.dart` | F3、F4 |
| 3 | `domain/text/SearchTextTest.kt` | `test/domain/search_text_test.dart` | F3 |
| 4 | `domain/text/VisibleTextStatsTest.kt` | `test/domain/visible_text_stats_test.dart` | F3 |
| 1 | `feature/notes/editor/MarkdownPreviewParserTest.kt` | `test/features/markdown_preview_parser_test.dart` | F8 |
| 2 | `feature/notes/NotesModelsTest.kt` | `test/features/notes_models_test.dart` | F6 |
| 7 | `navigation/XNoteNavigationStateTest.kt` | `test/features/routing_test.dart` | F5 |
| **115** | **22 个文件** | **全部测试均有 Flutter 归属** | **F2–F9** |

## 9. F0 出口审计

- 原生基线命令可复现且已通过。
- 页面、路由、领域模型、数据库表、设置键、图标、背景和现有测试已清点。
- S1–S6 十条关键路径均有固定 Flutter 验收脚本。
- 115 个旧测试均已分配到 F2–F9 的 Dart、Widget 或 Integration 测试目标。
- Keyline 固定提交和 MIT License 已核对。

F0 完成；下一阶段从 F1 Flutter 工程与质量门禁开始。
