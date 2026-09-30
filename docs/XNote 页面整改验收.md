# XNote 页面整改验收

日期：2026-09-30。范围：画板、抠图、贴纸库、我的、各设置子页、阅读、导出、搜索、回收站及 Agent 辅助面板。首页、Agent 主对话和笔记编辑页保留既有主体布局。

## 页面与交互

| 范围 | 当前实现 | 核对依据 |
| --- | --- | --- |
| 边缘效果 | 创作、贴纸、阅读、导出及固定搜索区不显示渐变遮罩；滚动设置和列表随边界显隐 | CreativePage、ReaderScreen、ExportScreen、XNoteApp、TabletNotesWorkspace |
| 画板 | 自适应画布、竖屏底部工具与宽横屏侧栏、颜色选择、粗细预览、撤销重做、清空、未保存退出确认 | DrawingScreen、CreativeTools；S12FlowTest |
| 抠图 | 原图／选区／结果分段、保留／擦除、粗细、撤销重做、重试、保存命名、退出确认 | CutoutScreen；S12FlowTest |
| 贴纸库 | 固定搜索、排序和创建菜单、自适应网格、详情、重命名、删除确认与插入 | StickerLibraryScreen；S12FlowTest |
| 我的与设置 | 真实入口分组、导航行、即时操作、整行开关、等宽分段选择 | ProfileScreen、XNoteSettingsComponents、AppearanceScreen |
| 统计与存储 | 摘要、分类和列表，统计口径弹窗；缓存与笔记记忆清理、失败重试 | StatisticsScreen、StorageScreen；S10FlowTest |
| 模型配置 | 配置列表、分组表单、高级选项、能力验证、默认选择与删除确认 | ModelSettingsScreen、ModelProfileEditor；ModelSettingsFlowTest |
| 记忆管理 | 画像列表与编辑确认，聊天记录和模型用量独立子页 | AgentMemoryScreen；AgentMemoryFlowTest |
| 搜索与集合 | 固定搜索与筛选，加载、空结果、读取失败及重试 | SearchScreen、NoteCollectionScreen、XNoteApp |
| 回收站 | 条目操作菜单，长按多选，可换行批量工具，永久删除确认 | RecycleBinScreen、RecycleBinChrome；NotesFlowTest |
| 阅读与导出 | 内容按实际工具栏避让，精简分页，导出进度、失败与保存结果 | ReaderScreen、ExportScreen；既有阅读与导出测试 |
| Agent 辅助面板 | 中文操作摘要、目标和内容预览，可展开完整固定请求，权限状态和改动审阅 | AgentNoteDialogs、AgentToolPresentation、AgentReviewDrawer；AgentFlowTest、AgentLifecycleFlowTest |
| 公共弹窗 | 系统安全区与键盘避让，正文可滚动，窄屏／大字号动作纵向排列，危险动作语义色 | XNoteOverlays |

导航层级、工具分组与设置组织参考 [Apple HIG Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars)、[Settings](https://developer.apple.com/design/human-interface-guidelines/settings) 和 [Segmented controls](https://developer.apple.com/design/human-interface-guidelines/segmented-controls)，保留 Android 系统返回和安全区行为。

## 自动化验证

执行 `./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`。

- 单元测试共 191 项：190 项通过、1 项跳过、0 失败。新增审批摘要测试核对创建目标笔记本、标题与选区变化、未知工具身份保留。
- Lint 无错误；报告保留依赖版本、未使用资源等警告，详细结果见 `app/build/reports/lint-results-debug.html`。
- Debug APK 与 instrumentation 测试 APK 构建通过。
- 端侧测试同步了审批、记忆、配置删除、贴纸管理、画板和回收站的新交互，并新增未保存绘画的退出确认用例。

## 视觉与端侧执行状态

本轮尚未执行设备测试和截图验收。检查时没有连接 Android 设备，项目规则要求明确授权后才能启动模拟器。上述端侧用例已编译打包，不能据此宣称运行通过。

待设备可用后检查手机竖屏、宽横屏、深色和大字号：创作画布与工具可达性、搜索键盘、弹窗操作可见性、设置与列表末项、审批长内容、阅读与导出分页。验证结果须以实际截图及 instrumentation 执行结果补充。
