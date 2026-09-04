# XNote Flutter

XNote 的 Flutter application。当前正式验收平台是 Android 13+；其他宿主已生成，但在插件审计和平台验收前不列为正式支持平台。

当前应用入口已经切换为 F5 正式应用壳：根节点初始化 `LiquidGlassWidgets` 并启用自适应质量、系统无障碍与 Material 亮度桥接；`go_router` 的 `StatefulShellRoute.indexedStack` 为笔记、Agent、我的保留三个独立导航栈，手机使用 `GlassTabBar.bottom`，600 logical pixels 起切换为侧边导航，搜索和二级页面使用稳定 URL 与系统返回。应用壳直接组合 `GlassNavigationShell`、`GlassScaffold`、`GlassAppBar` 和 `GlassTabBar.bottom`，没有项目自研玻璃包装器或渲染层。

F2 Glass 与编辑器 PoC 已与产品入口隔离，仅保留为后续编辑器实现和库浮层回归夹具。该夹具包含行内粗体、列表、可增删行列的 2×2 表格、标题折叠、450 ms 自动保存、返回或生命周期切换强制保存，以及系统 `UndoHistoryController` 驱动的撤销重做。

F3 纯 Dart 领域层位于 `lib/domain/`，覆盖笔记本、笔记、修订、附件、设置、富文本块与严格的新 schema JSON；编辑命令、历史、Markdown 转换、可见文本、中文 FTS、搜索片段、回收站和背景规则均不依赖 Flutter、Riverpod、Drift 或平台 API。UUID 与时钟通过边界接口注入，测试可以完全确定性地控制 ID 和时间。领域测试位于 `test/domain/`。

F4 数据事实源位于 `lib/data/`：Drift schema version 1 包含笔记本、笔记、修订、附件、搜索历史、设置和 FTS5 虚拟表，并启用外键、索引、事务与响应式 Stream。仓储保存笔记时在同一事务内更新新 JSON、摘要、字数和中文 FTS；删除/恢复、移动、多选、不可逆 Markdown 转换、启动回收站补扫及孤立附件元数据清理同样由事务负责。附件正文先写临时文件再原子重命名，拒绝绝对路径和目录穿越；物理孤立文件在元数据事务提交后幂等清理。

F5 视觉基础位于 `lib/design/` 与 `lib/features/shell/`：语义主题支持浅色、深色和高对比模式，主色分别固定为 `#E09F3E` 与 `#FFD60A`；20 枚 Keyline Rounded 原始 SVG 固定自上游提交 `14cd695f3f2bbe320bbe7a01e65b251df7ba52cf`，手机一级导航使用 Fill，其余位置使用 Stroke。Widget 与 Golden 测试覆盖窄屏、平板、浅深色、200% 字号、RTL、减少动画、搜索返回和独立分支栈。

## 环境

- Flutter 3.41.0 或更高版本；当前工程建立于 Flutter 3.44.3 stable。
- Dart 使用同一 Flutter SDK 自带版本；当前为 Dart 3.12.2。
- Android minSdk 33，applicationId 与 namespace 均为 `com.xnote.app`。
- Liquid Glass 依赖精确锁定为 `liquid_glass_widgets: 1.2.3`。
- 实体 ID 使用精确锁定的 `uuid: 4.6.0`，领域用例通过 `IdGenerator` 接口注入。
- 数据层使用 `drift: 2.34.4` 与 `sqlite3: 3.5.2`；生成工具锁定为与当前 Flutter SDK 兼容的 `drift_dev: 2.34.6`、`build_runner: 2.15.1`。
- 应用状态、导航与 SVG 分别锁定为 `flutter_riverpod: 3.4.3`、`go_router: 18.0.1`、`flutter_svg: 2.3.0`。

工程创建时的工具链记录：Flutter 3.44.3 stable、Dart 3.12.2、DevTools 2.57.0、Android platform 37、JDK 21、Windows 11 与 Visual Studio Build Tools 2022。`flutter doctor -v` 验证了 Windows、Web、Android 工具链和网络资源；本机仍提示 Flutter/Dart 未加入 PATH，以及部分 Android SDK 许可证未接受，因此命令可通过 `-FlutterSdk` 显式指定 SDK，且不把本机路径写入仓库。

## Liquid Glass 依赖审计

- 1.2.3 的 pubspec 要求 Dart 3.5+、Flutter 3.41+，并声明 Android、iOS、Linux、macOS、Web、Windows 六个平台。
- 1.2.3 的组件实现使用 `CupertinoIcons`；工程直接声明官方文档要求的 `cupertino_icons` 依赖，由 `pubspec.lock` 固定解析版本，确保 Web 和桌面构建包含对应字体。
- 包使用 MIT License，许可证全文已同步到仓库根目录的 `THIRD_PARTY_NOTICES.md`。
- 包仓库和 1.2.3 changelog 均可用；pub.dev 已存在 1.3.0，但本阶段遵循重构规划保持 1.2.3 精确锁定，不做无依据升级。
- Android Vulkan、iOS 和 macOS 使用完整 Impeller 路径；Android GLES 使用精简 shader 路径；Windows、Linux 和 Web 默认限制在 standard 质量。各平台宿主的存在不代表已经完成正式支持验收。

## 验证

在本目录运行：

```powershell
dart format --output=none --set-exit-if-changed .
flutter analyze
flutter test
flutter build apk --debug
```

也可以从仓库根目录运行统一脚本：

```powershell
.\flutter\tool\verify.ps1
```

Flutter SDK 未加入 PATH 时显式传入 SDK 根目录：

```powershell
.\flutter\tool\verify.ps1 -FlutterSdk C:\path\to\flutter
```

F1 或发布前同时验证 release APK：

```powershell
.\flutter\tool\verify.ps1 -FlutterSdk C:\path\to\flutter -Release
```

GitHub Actions 在 Windows runner 上复用同一脚本并启用 `-Release`，因此 CI 与本地执行相同的格式化、分析、测试和 Android debug/release 构建门禁。工作流固定 Flutter 3.44.3，并将 `actions/checkout` 与 `subosito/flutter-action` 锁定到已核对的提交 SHA；两者只用于 CI，不进入应用发布产物。CI 仅执行静态检查、测试和构建，不创建或启动模拟器。
