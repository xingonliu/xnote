# XNote

XNote 是一个面向 Android 13 及以上手机和平板的本地优先笔记应用。当前仓库已具备统一页面骨架、公共浮层、状态组件、Room 笔记库、完整富文本编辑闭环、可恢复删除、本地全文检索、默认背景和单篇背景覆盖，以及统一的结构化 `NoteDocument`、可关闭的 Markdown 快捷输入、图片插入与变换和分页阅读。S1–S8 已完成；下一优先切片是 S9 导出图片。完整功能范围见 [`docs/XNote 功能清单与页面组成.md`](./docs/XNote%20功能清单与页面组成.md)，开发切片顺序见 [`docs/XNote 开发顺序.md`](./docs/XNote%20开发顺序.md)，单时间线 Agent 的分层记忆、文档记忆与上下文规则见 [`docs/XNote Agent 记忆与上下文架构.md`](./docs/XNote%20Agent%20记忆与上下文架构.md)。

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
- 笔记首页、笔记本详情与笔记编辑页已接通本地笔记库：可创建笔记本、从编辑页 Header 选择归属、编写完整富文本（含表格与标题折叠）、自动保存，并将笔记移入回收站；编辑页标题下显示随成功保存更新的日期，正文不显示默认占位文案；输入法组合文本会逐次进入文档并在返回或后台切换时强制落盘，表格结构操作会同步更新当前单元格焦点；表格后可点击续写，列表标记按首行对齐。
- 首页与笔记本详情的长按多选使用分层操作栏：数量与取消置顶，移动和回收站操作按宽度换行；列表按操作栏实际高度预留底部空间。
- 公共玻璃按钮的禁用态保留完整阴影；全局下拉菜单采用 24 dp 圆角、快速锚点展开及 110 ms 收起，翻转位置后动效仍指向触发按钮。
- 所有笔记只使用结构化 `NoteDocument` 作为正文事实源。Markdown 不是笔记类型；“我的 → 外观、辅助功能与编辑”提供默认开启的快捷输入开关，只处理本次键盘输入，关闭后按普通文本保留。
- 全屏手机搜索与平板列表栏搜索复用 FTS5：支持标题/正文、笔记本筛选、原文片段高亮、最近搜索持久化与清空，连续中文子串可命中且回收站内容始终排除。
- “我的”已提供回收站入口；回收站展示删除时间、剩余天数和原笔记本，可恢复、永久删除、清空及多选，并在启动补扫或后台任务中清理满 30 天的笔记和未引用附件。
- 当前编辑界面统一使用全屏 `XNoteNoteSurface` 作为页面唯一背景，纸张连续延伸到系统栏、Header 与底部工具区下方，不再叠加独立页面底色；支持暖白、奶油纹理、横线、方格四款内置纸张，“我的”可设置默认背景，单篇笔记可覆盖选择或恢复继承。

## 图片编辑

图片使用编辑页右上角“更多 → 添加图片”入口，从系统相机或相册导入。点击正文图片打开操作面板；手机使用底部 Drawer，平板使用锚定菜单。支持缩放、旋转、双指移动、上下移动块、删除、替换、复制和前后层级，所有变更支持自动保存及会话内撤销重做。

图片在光标处分开文字并作为独立块插入；表格内操作会在表格后插入。变换后的包围区域参与排版并限制在正文宽度内，前后层级写入图片绘制顺序，块级排版不会因此与文字重叠。替换保留位置与变换，复制共享不可变附件但独立编辑。导入图像按方向解码并保存为最长边不超过 2560 像素的静态 PNG（动画保留首帧），显示时最多解码 1280 像素；不保留原始文件或照片元数据。取消来源选择不改正文，无法读取的图片提供替换/删除入口。附件仅通过稳定 ID 写入文档，回收站和编辑会话撤销所需附件受到保留，未引用附件由启动及周期清理回收。

## 阅读模式

在笔记本详情的“更多 → 打开阅读模式”中，按当前手动、创建时间、更新时间或标题顺序连续阅读；同值项按稳定 ID 排序，与笔记列表一致。编辑页的同名入口先完成自动保存，只阅读当前一篇。阅读页提供上一页、下一页、页码、进度、笔记目录跳转及编辑当前笔记，编辑后返回保留阅读位置。

每篇从新页开始，长段落按实际文字行高分页，表格按各列的文字行续排，保留行内样式、清单、引用、缩进、对齐和链接。阅读时展开全部标题内容，不改变正文保存的折叠状态。图片、贴纸和画笔附件整块换页，超过整页时等比缩小，保留图片旋转和偏移。贴纸与画笔创作入口仍按 S12 交付；无法解码的附件显示不可读取状态。

阅读使用与编辑相同的全屏背景画布，随当前笔记解析专属或默认背景。手机使用底部目录 Drawer，平板使用侧边 Drawer。屏幕尺寸、字体缩放或窗口变化会重新测量分页，按笔记、块与文字位置恢复阅读锚点；分页及文本测量结果不写入数据库。

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
├─ feature/notes       # 笔记首页、笔记本详情与结构化编辑器
├─ feature/reader      # 阅读分页、富文本/表格/媒体渲染、目录与阅读位置
├─ feature/search      # 手机全屏与平板列表栏搜索
├─ feature/recycle     # 回收站列表、批量操作与危险操作确认
├─ feature/profile     # “我的”中已实现功能的真实入口
├─ navigation          # 一级目的地与导航状态
├─ MainActivity.kt     # Android 入口
└─ XNoteApp.kt         # 手机/平板应用外壳
```
