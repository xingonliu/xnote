# XNote

XNote 是一个面向 Android 13 及以上手机和平板的本地优先笔记应用。当前仓库已完成 Android/Jetpack Compose 工程初始化，并提供可继续开发的应用外壳、设计系统入口和首屏状态；完整功能范围见 [`docs`](./docs)。

## 当前基线

- Android Gradle Plugin 9.3.2、Gradle 9.7.1、JDK 17 及以上。
- `compileSdk 37`、`targetSdk 37`、`minSdk 33`。
- Kotlin/Compose Compiler 2.3.21、Compose BOM 2026.08.00。
- AndroidLiquidGlass `io.github.kyant0:backdrop:2.0.1` 与 Shapes `1.2.1` 均固定版本。
- 手机一级导航直接采用 AndroidLiquidGlass 官方 catalog 的 `LiquidBottomTabs` / `LiquidBottomTab`，按钮采用同源 `LiquidButton`；组件源码固定到上游提交 `65ab177`，仅补充 XNote 所需的尺寸、禁用态和无障碍输入。
- AndroidLiquidGlass 发布物只提供 Backdrop/Lens 等底层能力，不打包高层组件；项目优先采用官方 catalog 已有实现，只在 catalog 没有对应组件时创建基于该库的最薄适配层。
- Liquid Glass 没有低版本或低性能替代材质；Android 13 是完整透镜效果的最低系统边界。
- 界面矢量图标统一来自 Lucide `1.34.0`，以 24 × 24 官方 SVG 为源转换为 Android `VectorDrawable`；完整规则见 [UI 设计规范](./docs/XNote%20UI%20设计规范.md)。

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
├─ design              # 主题、令牌、Shape 与库中没有的项目级玻璃适配
│  └─ liquidglass      # 固定版本的 AndroidLiquidGlass 官方 catalog 组件
├─ feature/notes       # 笔记首页的首期界面状态
├─ navigation          # 一级目的地与导航状态
├─ MainActivity.kt     # Android 入口
└─ XNoteApp.kt         # 手机/平板应用外壳
```

初始化阶段不包含笔记 CRUD、数据库、搜索或 Agent 的假实现。后续功能按文档中的交付阶段逐步落地，并持续复用公共组件。
