# XNote S12 实施与验收

状态：已完成实现与 Windows Android 模拟器验收。范围以功能清单 3.5–3.6、3.21、P06/P17 和开发顺序 S12 为准。

## 1. 模型准入预检（2026-09-29）

候选采用 U2NETP 自动主体分割，加手动蒙版添加/擦除。上游 [U-2-Net 仓库](https://github.com/xuebinqin/U-2-Net) 的 [Apache-2.0 许可证](https://github.com/xuebinqin/U-2-Net/blob/master/LICENSE) 允许商业使用，分发时保留许可证与归属；ONNX Runtime 使用 [MIT 许可证](https://github.com/microsoft/onnxruntime/blob/v1.25.0/LICENSE)。权重取自 [rembg 的固定发布资产](https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx)，由 SHA-256 固定具体文件。模型、运行库许可证与第三方通知已纳入 APK 的 assets/licenses 和仓库 THIRD_PARTY_NOTICES.md。

| 项目 | 检查结果 |
| --- | --- |
| 权重大小 | 4,574,861 字节（4.36 MiB） |
| SHA-256 | `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8` |
| 输入 | Float32 NCHW `[1,3,320,320]`，名称 `input.1` |
| 输出 | 首个融合蒙版 `1959`，Float32 `[1,1,320,320]` |
| 格式/算子 | ONNX opset 11；Add、Cast、Concat、Constant、Conv、Gather、MaxPool、Relu、Resize、Shape、Sigmoid、Slice、Unsqueeze；ONNX checker 通过 |
| 运行库 | ONNX Runtime Android 1.25.0；CPU，2 个 intra-op 线程、1 个 inter-op 线程 |
| AAR 下载大小 | 42,942,188 字节（40.95 MiB），包含四种 ABI；不能当作最终 APK 增量 |
| arm64 原生库体积 | 两个 `.so` 解压合计 27,084,448 字节；四 ABI 通用 APK 中运行库原生条目合计 111,745,296 字节（未压缩）；不是仅权重大小 |
| Android 环境 | Windows 主机，XNoteImageRegression AVD，Android 16/API 36，x86_64，16 KiB 内存页，2 GiB RAM |
| 兼容性 | Android 原生库加载、模型加载、五次推理、有限值/前后台分离、透明 PNG 写出均通过 |

输入按 RGB 最大通道值归一化，再使用均值 `[0.485,0.456,0.406]` 和标准差 `[0.229,0.224,0.225]`，参考 [rembg 预处理](https://github.com/danielgatis/rembg/blob/main/rembg/sessions/base.py) 与 [U2NETP 会话](https://github.com/danielgatis/rembg/blob/main/rembg/sessions/u2netp.py)。黑图分母至少为 1；输出极差为零时不得除零。原图已有 alpha 与蒙版相乘。

模拟器独立进程对比（同一固定样本，五次推理，仅请求首个输出）：

| 配置 | 加载 | 推理耗时 ms | 20 ms 间隔采样峰值 PSS | 会话关闭后 PSS |
| --- | --- | --- | --- | --- |
| CPU arena/memory pattern 开 | 100.34 ms | 366.36 / 416.27 / 253.61 / 275.14 / 264.30 | 623,804 KiB | 123,547 KiB |
| CPU arena/memory pattern 关 | 65.24 ms | 396.62 / 364.85 / 376.23 / 385.25 / 384.18 | 386,891 KiB | 133,591 KiB |

选择关闭 arena 和 memory pattern；限制并发为一次推理，完成后关闭 tensor/result/session。以上是模拟器进程采样数据，峰值可能漏掉采样间隙，包含宿主应用和测试框架，不代表 ARM 真机性能或应用全流程峰值。真机耗时/内存覆盖留待有设备时补充，不能以 AVD 的兼容 ABI 列表声称已执行 ARM 推理。

样本来自上游 `test_data/test_images/0003.jpg`，SHA-256 为 `8d13d397fcbc4c3742d1d30d00cabe53c7b1ecd0a8cbf5a73ab0f78330fca12f`。已检查 Android 输出：主体保留，背景透明，毛发周围存在灰色残留；这支持“自动初始蒙版 + 手动修正”的设计，不能证明所有照片均能准确抠图。

Windows 主机另有可复跑的 CPU 探针；同样关闭 arena 时进程峰值工作集约 328 MiB，开启约 561 MiB。工作集与 Android PSS 口径不同，不作直接比较。

### 复跑

```powershell
./tools/prepare-s12-preflight.ps1
./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.xnote.app.S12ModelPreflightTest' '-Pandroid.testInstrumentationRunnerArguments.s12Preflight=true'
# 比较开启内存池；请独立启动一次 instrumentation 进程。
./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.xnote.app.S12ModelPreflightTest' '-Pandroid.testInstrumentationRunnerArguments.s12Preflight=true' '-Pandroid.testInstrumentationRunnerArguments.s12Arena=true'
```

测试默认跳过，须显式设置 `s12Preflight=true`，下载缺失或哈希不匹配时失败；跳过不能作为准入证据。测试报告位于 `app/build/reports/androidTests/connected/debug`，原始指标也写入 `S12Preflight` logcat。安装包仍在设备时，结果在目标应用 `files/s12-preflight`，可通过 `adb exec-out run-as com.xnote.app cat ...` 读取。

主机探针需要独立 Python 环境中的 `onnxruntime==1.25.0`、`onnx`、`numpy`、`Pillow`、`psutil`。准备相同资产后执行：

```powershell
python tools/s12-model-host-probe.py --output build/s12/host-bounded.json
python tools/s12-model-host-probe.py --output build/s12/host-arena.json --arena
```

## 2. 实施顺序与证据要求

| 阶段 | 实现范围 | 完成证据 |
| --- | --- | --- |
| S12.1 抠图 | 下载/校验/加载/处理进度，失败重试，P06 原图/蒙版/结果，重新处理，蒙版添加/擦除，取消，透明 PNG 保存与插入 | 实际模型推理；下载失败/取消；蒙版坐标和 alpha；相机/相册流程 |
| S12.2 贴纸 | 独立库条目持久化，网格/预览/搜索/排序/重命名/删除，相机/相册创建，笔记插入；图片和贴纸共享变换与前后层级 | 重启恢复；删除库条目后实例、撤销、回收站、版本附件保持有效；只回收无引用附件 |
| S12.4 排版 | 媒体块嵌入/文字环绕/浮于文字；变换后重排；中文输入、选择、撤销和焦点保留 | 不以整段缩窄冒充逐行环绕；图片下方恢复全宽；相邻媒体/长文/表格边界；保存重启 |
| S12.5 阅读与导出 | 图片和贴纸、布局和层级在编辑/阅读/导出一致；整块媒体不切割，超页等比缩小 | 分页测试与实际 PNG；背景透明度、顺序、文字无遗漏 |
| S12.6 收尾 | 公共页面骨架/Header/玻璃控件、手机/平板/动态字号、附件清理、文档、最终 diff、中文提交和推送 | 单测/lint/构建、Android 功能与视觉验收、最终逐项核对 |

实现以 PlacedMediaBlock 统一图片和贴纸变换，Room 17 新增 stickers 表并提供 16→17 自动迁移。编辑、阅读、导出及附件引用清理均已接通。

## 3. 交付与验收

- P06 图片编辑：固定 HTTPS 下载与 SHA-256 校验、状态进度、异常重试、原图/蒙版/结果、手动添加/擦除/撤销、重新处理、取消、透明 PNG 保存及插入。模型推理串行执行，退出取消下载，临时下载文件在 finally 清理；完成推理后关闭会话。
- P17 贴纸库：相机/系统相册入口、持久网格、搜索、最近创建/名称排序、预览、重命名、确认删除和插入。库引用、正文、回收站、编辑会话及历史版本共同保护附件。
- 图片/贴纸：共同变换与层级，嵌入、环绕、浮动；环绕按矩形外接区域逐行避让，下方恢复全宽；表格与独立媒体块构成分组边界。阅读分页不遗漏文字，媒体整块换页或等比缩小，导出递归加载布局中的媒体。
- 公共 UI：P06/P17 共用页面骨架、Header、按钮、Toast、删除确认浮层及系统安全区；透明素材用棋盘格预览。

### 自动化

2026-09-30 执行 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 通过：194 项 JVM 单测中 193 项通过、1 项跳过、0 失败；Lint 无错误，Debug APK 和测试 APK 构建成功。编辑页插入菜单覆盖相机、相册、贴纸和表格，阅读测试保留长图缩放分页，导出测试覆盖透明贴纸。

首次交付时，`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease` 通过，173 项 JVM 单测无失败，包含 Release R8 混淆。该次四 ABI 通用 unsigned Release APK 为 127,207,068 字节（121.31 MiB）；该值是整个应用体积，不是 S12 增量。Android 16 x86_64 / 16 KiB 模拟器已完成抠图与媒体功能验收。当前端侧测试覆盖如下，本轮仅编译测试 APK，尚未执行设备回归：

| 测试集 | 项目数 | 覆盖范围 |
| --- | --- | --- |
| S12CreativeMediaTest | 4 | 真模型蒙版、透明像素/蒙版修正、库持久化与引用清理、16→17 迁移 |
| S12FlowTest | 6 | 抠图修正与保存插入、贴纸管理、环绕编辑与阅读、中文组合输入、逐行环绕/分页、实际 PNG 导出 |
| NoteImagesInstrumentedTest | 3 | 图片变换及原有附件行为 |
| ReadingFlowTest + ExportFlowTest | 12 | 原有阅读与导出回归 |
| 首次联网下载 | 1 | 清除缓存后以 s12Download=true 执行生产模型测试，下载与推理通过 |

测试资产先运行 `tools/prepare-s12-preflight.ps1` 下载并校验。定向复跑：

```powershell
./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.xnote.app.S12CreativeMediaTest,com.xnote.app.S12FlowTest,com.xnote.app.data.NoteImagesInstrumentedTest,com.xnote.app.feature.reader.ReadingFlowTest,com.xnote.app.feature.export.ExportFlowTest'
./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.xnote.app.S12CreativeMediaTest#productionEngineUsesPinnedModelAndReturnsEditableMask' '-Pandroid.testInstrumentationRunnerArguments.s12Download=true'
```

### 视觉验收

首次交付时已检查手机 720×1280 / 280 dpi 的抠图、环绕编辑、阅读与导出截图，以及平板 1280×960 / 160 dpi、系统字号 1.3 的图片编辑和深色贴纸预览。测试结束恢复模拟器原分辨率、密度和字号。

### 覆盖边界

模型端侧兼容性及内存已在上述模拟器实际测量。ARM 真机性能、不同厂商相机和复杂毛发样本准确率未作普遍保证；相机/相册采用 Android 系统来源契约，模型下载依赖首次使用时的网络可达性。原始截图和探针输出在本地 build/s12，未将模型权重、样本或构建产物提交仓库。
