# XNote Flutter

XNote 的 Flutter application。当前正式验收平台是 Android 13+；其他宿主已生成，但在插件审计和平台验收前不列为正式支持平台。

当前应用入口已经进入 F2 技术验证：根节点初始化 `LiquidGlassWidgets` 并启用自适应质量、系统无障碍与 Material 亮度桥接；首屏可直接切换 Glass 组件验证和编辑器验证。编辑器 PoC 包含标题、两个正文块、行内粗体、列表、可增删行列的 2×2 表格、标题折叠、450 ms 自动保存、返回或生命周期切换强制保存，以及系统 `UndoHistoryController` 驱动的撤销重做。所有玻璃表面和交互均直接组合 `liquid_glass_widgets`，没有项目自研玻璃渲染层。

## 环境

- Flutter 3.41.0 或更高版本；当前工程建立于 Flutter 3.44.3 stable。
- Dart 使用同一 Flutter SDK 自带版本；当前为 Dart 3.12.2。
- Android minSdk 33，applicationId 与 namespace 均为 `com.xnote.app`。
- Liquid Glass 依赖精确锁定为 `liquid_glass_widgets: 1.2.3`。

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
