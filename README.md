# XNote

XNote 是一个面向 Android 13 及以上手机和平板的本地优先笔记应用。当前仓库已完成 Android/Jetpack Compose 工程初始化、S1 设计系统与 S2 本地数据层，提供可继续开发的应用外壳、统一页面骨架、公共浮层、状态组件，以及 Room 笔记库；完整功能范围见 [`docs/XNote 功能清单与页面组成.md`](./docs/XNote%20功能清单与页面组成.md)，开发切片顺序见 [`docs/XNote 开发顺序.md`](./docs/XNote%20开发顺序.md)。

## 当前基线

- Android Gradle Plugin 9.3.2、Gradle 9.7.1、JDK 17 及以上。
- `compileSdk 37`、`targetSdk 37`、`minSdk 33`。
- Kotlin/Compose Compiler 2.3.21、Compose BOM 2026.08.00。
- Room 3.0.2、`BundledSQLiteDriver`（SQLite 2.7.0，用于 FTS5）、DataStore Preferences 1.2.1、WorkManager 2.11.2、kotlinx.serialization JSON 1.11.0。
- AndroidLiquidGlass `io.github.kyant0:backdrop:2.0.1` 与 Shapes `1.2.1` 均固定版本。
- 手机一级导航直接采用 AndroidLiquidGlass 官方 catalog 的 `LiquidBottomTabs` / `LiquidBottomTab`，按钮采用同源 `LiquidButton`；组件源码固定到上游提交 `65ab177`，仅补充 XNote 所需的尺寸、禁用态和无障碍输入。
- AndroidLiquidGlass 发布物只提供 Backdrop/Lens 等底层能力，不打包高层组件；项目优先采用官方 catalog 已有实现，只在 catalog 没有对应组件时创建基于该库的最薄适配层。
- Liquid Glass 没有低版本或低性能替代材质；Android 13 是完整透镜效果的最低系统边界。
- 界面矢量图标统一来自 Lucide `1.34.0`，以 24 × 24 官方 SVG 为源转换为 Android `VectorDrawable`；完整规则见 [UI 设计规范](./docs/XNote%20UI%20设计规范.md)。
- `XNotePageScaffold` 已统一系统安全区、页面加载/错误、Toast Host，以及随滚动能力自动显隐的顶部/底部 `Soft` / `Hard` Scroll Edge。
- `XNoteHeader`、Dialog、Drawer、Toast、Popup、Dropdown、加载/空/错误状态与富文本工具栏均由公共设计系统提供；系统动画倍率为 0 时取消弹性、形变和过渡动画。

## 本地运行

1. 安装 Android Studio、JDK 17+、Android SDK Platform 37 和 Build Tools 36.0.0+。
2. 使用 Android Studio 打开仓库，等待 Gradle 同步完成。
3. 运行 `app` 配置，目标设备需为 Android 13（API 33）或更高版本。

命令行验证：

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

如未设置环境变量，可先设置 Android SDK 路径：

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

## 代码结构

```text
app/src/main/java/com/xnote/app
├─ data                # Room 笔记库、附件文件、DataStore 设置、回收站清理
├─ domain              # 笔记文档 JSON、领域规则、纯文本/FTS 抽取
├─ design              # 主题、令牌、Shape 与库中没有的项目级玻璃适配
│  ├─ liquidglass      # 固定版本的 AndroidLiquidGlass 官方 catalog 组件
│  └─ 公共页面骨架、Header、Scroll Edge、浮层、状态与富文本工具栏
├─ feature/notes       # 笔记首页的首期界面状态
├─ navigation          # 一级目的地与导航状态
├─ MainActivity.kt     # Android 入口
└─ XNoteApp.kt         # 手机/平板应用外壳
```

S2 不接通笔记 CRUD 界面。下一切片从 S3 笔记本与普通文字笔记开始，并持续复用现有公共组件与 `NoteLibrary`。
