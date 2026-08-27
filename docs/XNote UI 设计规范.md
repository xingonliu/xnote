# XNote UI 设计规范

> 文档版本：v0.9
>
> 适用平台：Android 13（API 33）及以上的手机、平板
>
> 视觉基准：Apple Notes
>
> 关联文档：[XNote 功能清单与页面组成](./XNote%20功能清单与页面组成.md)、[XNote 开发顺序](./XNote%20开发顺序.md)

## 1. 目标与原则

XNote 的界面以 Apple Notes 的清晰、克制和内容优先为视觉方向，在 Android 手机与平板形态中保持一致的组件语义和交互反馈。

所有新页面和重构页面必须遵守以下原则：

1. 笔记内容始终是视觉主体，装饰效果不能降低文字、图片和画笔内容的可读性。
2. 玻璃交互优先直接采用 AndroidLiquidGlass 官方 catalog 组件；只有官方 catalog 没有对应组件时，才允许基于该库创建项目级组件，不为同一功能重复设计局部样式。
3. 所有页面统一接入 `ScrollEdgeEffectStyle` 视觉规范；页面内容可滚动时，固定 Header、底部工具区与滚动内容之间必须有连续的边缘过渡。
4. 二级页面统一使用公共 Header；除笔记编辑页外，Header 中间显示当前页面标题。
5. 弹窗、抽屉、Toast、Popup、下拉菜单、按钮等通用交互只能通过公共组件提供，业务页面不得复制实现。
6. 所有由应用绘制的非圆形圆角统一使用接近 iOS 连续曲线的 60% 平滑圆角，不得混用普通四分之一圆圆角。
7. 优先使用系统字体、系统手势、动态字号、无障碍语义和平台返回行为；视觉接近 Apple Notes 不等于破坏 Android 的基础交互习惯。

## 2. 平台与技术边界

`ScrollEdgeEffectStyle` 是 SwiftUI 中用于定义滚动视图边缘模糊过渡的类型，`scrollEdgeEffectStyle(_:for:)` 用于配置指定边缘。XNote 当前是 Android/Jetpack Compose 应用，因此本文中的 `ScrollEdgeEffectStyle` 表示项目级视觉规范，不表示 Android 代码直接调用 SwiftUI API。

Android 实现统一封装为 `XNoteScrollEdgeEffect`，由 `XNotePageScaffold` 接入。它至少包含以下能力：

- 根据滚动状态分别控制顶部和底部边缘效果。
- 默认提供 `soft`，需要强化固定控件边界时提供 `hard`。
- 使用固定版本的 AndroidLiquidGlass 实现背景采样、模糊、折射、色散和渐变能力；所有受支持设备使用同一套完整材质。
- 不拦截触摸、滚动、选择、拖放或无障碍焦点。
- 遵守“减少动画”和高对比度设置，同时保留完整 Liquid Glass 材质。

Android 13+ 的系统动画倍率通过 `ValueAnimator.getDurationScale()` 与倍率变更监听接入公共交互设置；倍率为 0 时，Scroll Edge 直接切换显隐，Liquid Button 与 Bottom Tabs 停止形变和弹性反馈，公共浮层不执行进入或退出过渡。页面不得各自读取系统动画设置。

SwiftUI 语义与 Compose 项目语义的映射如下：

| 规范语义       | SwiftUI 参照                    | XNote Android 实现                  |
| -------------- | ------------------------------- | -------------------------------------- |
| 柔和滚动边缘   | `ScrollEdgeEffectStyle.soft`    | `XNoteScrollEdgeStyle.Soft`            |
| 清晰滚动边界   | `ScrollEdgeEffectStyle.hard`    | `XNoteScrollEdgeStyle.Hard`            |
| 指定生效边缘   | `scrollEdgeEffectStyle(_:for:)` | `XNoteScrollEdgeEffect(edges, style)`  |
| 页面统一接入   | View hierarchy modifier         | `XNotePageScaffold` 内置效果层         |
| 玻璃按钮       | `.buttonStyle(.glass)`          | AndroidLiquidGlass catalog `LiquidButton`                  |
| 手机底部标签栏 | `TabView` / bottom tabs         | AndroidLiquidGlass catalog `LiquidBottomTabs` / `LiquidBottomTab` |
| 自定义玻璃形状 | `glassEffect(_:in:)`            | AndroidLiquidGlass 的项目级 Shape 适配                     |

## 3. 页面骨架与 Scroll Edge

### 3.1 统一页面骨架

所有一级和二级页面必须以 `XNotePageScaffold` 为根容器，由它统一处理：

- 系统状态栏、导航栏和安全区域。
- 页面背景和明暗主题。
- Header、底部导航、底部工具区和浮动操作区的层级。
- `ScrollEdgeEffectStyle` 的顶部与底部效果。
- Toast Host、全局加载态和页面级错误态。
- 手机、横屏和平板的内容宽度与边距。

弹窗、Popup 和下拉菜单不视为页面，不单独套用页面 Scaffold；它们自身存在可滚动内容时，必须在内部滚动容器接入相同的边缘效果。

### 3.2 Scroll Edge 规则

- 所有页面必须声明顶部边缘效果，默认使用 `Soft`。
- 存在固定底部导航、编辑工具栏、输入框或悬浮操作区时，同时声明底部边缘效果。
- 内容未溢出、无法继续向对应方向滚动时，边缘效果自动隐藏，不保留无意义的模糊或阴影。
- 内容滚动到固定 Header 或底部控件下方时，效果平滑出现；离开边缘后平滑消失。
- 普通列表、设置页和统计页使用 `Soft`；高密度编辑工具区或需要明确分隔的固定控件可使用 `Hard`。
- 边缘效果只负责层级过渡，不能额外占据布局高度，也不能遮挡标题、首行内容、滚动条或底部最后一项。
- 长列表、网格、富文本编辑器、Markdown 编辑器、阅读模式和对话消息区均遵循同一规则。

## 4. 二级页面 Header

### 4.1 标准结构

所有二级页面使用 `XNoteHeader`，结构固定为：

```text
┌──────────────────────────────────────────┐
│ [圆形 SVG 返回按钮]   页面标题   [功能按钮] │
└──────────────────────────────────────────┘
```

- 左侧固定为圆形 Liquid Glass 返回按钮，内部只使用统一的 SVG 返回图标。
- 禁止以文字字符、Emoji、字体符号或页面私有图标替代返回 SVG。
- 中间标题必须是当前页面的标题，单行显示并保持视觉居中；过长时尾部省略。
- 右侧按页面功能放置零至两个按钮，使用公共 Liquid Glass 图标按钮或胶囊按钮。
- 右侧没有功能时保留与左侧按钮等宽的布局占位，确保标题不偏移；占位不可点击，也不暴露无障碍语义。
- Header 固定在页面顶部，并与页面的顶部 `ScrollEdgeEffectStyle` 协同工作。

### 4.2 尺寸与间距

| 项目                |       手机 |       平板 |
| ------------------- | ---------: | ---------: |
| Header 内容高度     |      44 dp |      44 dp |
| Header 水平安全边距 |      16 dp |      24 dp |
| 圆形按钮            | 44 × 44 dp | 44 × 44 dp |
| SVG 图标视觉尺寸    | 20 × 20 dp | 20 × 20 dp |
| 标题与两侧最小间距  |      12 dp |      16 dp |

所有按钮的最小可点击区域为 44 × 44 dp。SVG 使用 `currentColor` 语义，由组件根据普通、按下、禁用和危险状态提供前景色；业务页面不得维护不同版本的返回图标。

### 4.3 笔记编辑页例外

普通笔记编辑页和 Markdown 编辑页不在 Header 中间显示固定页面标题。笔记标题属于文档内容，统一置于正文顶部并可编辑；Header 中间保持空白，可仅展示短暂的保存状态，但不能与笔记标题形成重复标题。

编辑页仍必须使用统一的左侧圆形 SVG 返回按钮、右侧功能按钮和顶部 Scroll Edge。

普通笔记的段落样式、行内样式、清单、对齐、表格和折叠命令只能通过 `XNoteRichTextToolbar` 发出。格式工具栏贴在键盘上方或底部插入工具区之上，必须触发底部 Scroll Edge，不得与“添加图片”“画笔”“Agent”操作混成同一组无分区按钮。

### 4.4 页面标题与右侧功能

| 页面           | Header 标题    | 右侧主要功能               |
| -------------- | -------------- | -------------------------- |
| 笔记本详情     | 当前笔记本名称 | 更多                       |
| 润色 Diff      | 润色结果       | 无或帮助                   |
| 阅读模式       | 阅读模式       | 目录、编辑                 |
| 图片编辑与抠图 | 图片编辑       | 完成                       |
| 画板           | 画板           | 完成                       |
| 导出预览       | 导出预览       | 分享                       |
| Agent 对话     | 当前会话标题   | 更多                       |
| 笔记范围选择   | 笔记范围       | 完成                       |
| 工具调用详情   | 工具调用详情   | 重试或取消，按状态显示     |
| 搜索页         | 搜索           | 清空，仅存在历史记录时显示 |
| 统计           | 统计           | 无                         |
| 回收站         | 回收站         | 选择、更多                 |
| 贴纸库         | 贴纸库         | 添加                       |
| 模型与服务商   | 模型与服务商   | 添加                       |
| Agent 权限     | Agent 权限     | 无                         |
| Skill 管理     | Skill 管理     | 安装                       |
| MCP 管理       | MCP 管理       | 添加                       |
| Linux 环境     | Linux 环境     | 更多                       |
| 存储与隐私     | 存储与隐私     | 无                         |
| 外观与辅助功能 | 外观与辅助功能 | 无                         |

危险操作不直接作为 Header 的常驻主按钮；永久删除、清空、重置等操作放入更多菜单或确认弹窗。

## 5. Lucide 图标系统

### 5.1 来源与版本

- 应用界面中的 SVG 图标统一选自 [Lucide Icons](https://lucide.dev/icons/)，当前资产基线为 `lucide-static 1.34.0`。
- Android 工程将 Lucide 官方 SVG 转换为等价的 `VectorDrawable`，不引入运行时图标依赖；转换时必须保留官方路径、视口和描边语义。
- 新增或替换图标时，先从 Lucide 图标目录选择语义最接近的图标，并在资源文件头记录 Lucide 名称、版本、许可证和原始页面链接。
- 禁止混用 Material Icons、自绘轮廓、Emoji、字体符号或其他第三方图标。Lucide 暂无合适图标时，应先补充或调整本规范，不得在业务页面创建私有替代图标。
- Lucide 的许可证全文统一保存在仓库根目录的 [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md)。

### 5.2 几何与样式

| 项目 | 统一值 |
| ---- | ------ |
| 原始视口 | 24 × 24 |
| 默认描边 | 2，随图标整体等比缩放 |
| 线端 | `round` |
| 转角 | `round` |
| 默认填充 | `none`；仅当 Lucide 原图明确包含填充时例外 |
| Header 与普通按钮 | 20 × 20 dp |
| 一级导航 | 22 × 22 dp |
| 空状态主图标 | 44 × 44 dp |
| 最小触控区域 | 44 × 44 dp |

- 业务代码通过 Compose `Icon.tint` 提供前景色，对应 Lucide SVG 的 `currentColor` 语义；不得为普通、按下、选中或禁用状态复制不同颜色的矢量资源。
- 不得拉伸、压扁、旋转、增删路径或单独改变某条路径的粗细。尺寸变化必须保持 24 × 24 视口的原始比例。
- 返回、前进等方向性图标按交互语义启用 RTL 自动镜像；无方向性的图标不得镜像。
- 图标外部的 Liquid Glass 容器、状态底色和应用启动背景不属于图标轮廓，可以使用 XNote 设计令牌；容器内的图标轮廓仍必须来自 Lucide。

### 5.3 资源命名与当前映射

Android 资源以 `ic_lucide_<官方名称>` 命名，将 Lucide 名称中的连字符转换为下划线。系统要求固定名称的启动资源可保留 `ic_launcher`，但文件头必须标明实际使用的 Lucide 图标。

| XNote 资源 | Lucide 名称 | 用途 |
| ---------- | ----------- | ---- |
| `ic_lucide_notebook_pen` | `notebook-pen` | 笔记导航、笔记空状态 |
| `ic_lucide_sparkles` | `sparkles` | Agent 导航 |
| `ic_lucide_user_round` | `user-round` | 个人中心导航 |
| `ic_lucide_search` | `search` | 搜索操作 |
| `ic_lucide_arrow_left` | `arrow-left` | 返回操作 |
| `ic_lucide_plus` | `plus` | 新建操作 |
| `ic_launcher` | `notebook-pen` | 应用图标与启动页图形 |

### 5.4 无障碍

- 独立图标按钮必须提供本地化、可朗读的 `contentDescription`。
- 图标与可见文字共同表达同一操作时，图标使用 `contentDescription = null`，由父级按钮或文字提供唯一语义，避免屏幕阅读器重复朗读。
- 选中、禁用、危险和加载状态不能只靠图标颜色表达；同时提供容器状态、文字或无障碍状态描述。

## 6. AndroidLiquidGlass 组件

Backdrop 捕获层只能包含背景内容；所有使用同一 `Backdrop` 的玻璃控件必须作为捕获层的同级节点绘制，不能嵌套在 `layerBackdrop` 子树中，否则 Android HWUI 可能形成循环渲染并导致 `RenderThread` 原生崩溃。

AndroidLiquidGlass 的 Maven 发布物提供 Backdrop、Lens、Blur、Vibrancy、高光与阴影等底层能力，不包含高层组件。XNote 将官方仓库 catalog 作为高层组件的唯一上游，当前源码固定到提交 `65ab177`；纳入项目时只允许补充业务无关的尺寸、禁用态和无障碍输入，不得改写官方材质参数。

### 6.1 使用范围

已有官方 catalog 组件必须优先使用：

- 手机一级导航直接使用 `LiquidBottomTabs` 和 `LiquidBottomTab`，保留官方容器、选中滑块、拖拽、回弹、高光与色散实现。
- Bottom Tabs 选中胶囊的几何中心必须始终与当前 tab 的图标和文字中心重合；按压放大、拖拽形变和色散不得改变中心锚点或产生累计偏移。
- Bottom Tabs 按压时，当前选中项的中性基础内容层与主题色内容层必须共享同一中心缩放和位移；不得只变换主题色层而让灰色图标或文字停留在原位。
- Bottom Tabs 的整条玻璃容器都是滑块手势区；手指在任意 tab 或间隙按下时，滑块立即映射到落点并连续跟手，松手后吸附到最近的 tab。
- Header 图标按钮、浮动按钮、胶囊按钮、确认按钮和筛选按钮使用 `LiquidButton`。
- 出现开关或连续数值输入时，优先纳入同一 catalog 的 `LiquidToggle` 或 `LiquidSlider`，不得先创建项目私有样式。
- catalog 没有 Panel 和竖向 Navigation Rail；`XNoteLiquidGlassPanel` 与平板 Rail 因此可以作为项目级适配，但必须直接组合 AndroidLiquidGlass API，不得另建玻璃渲染引擎。

列表整行点击区域、文本输入框、开关、单选项和复选项属于对应控件，不额外包裹玻璃按钮；行内独立的图标操作仍使用玻璃按钮。

### 6.2 组件来源

| 组件 | 来源 | XNote 用途 |
| ---- | ---- | ---------- |
| `LiquidBottomTabs` / `LiquidBottomTab` | 官方 catalog | 手机一级底部导航 |
| `LiquidButton` | 官方 catalog | 图标、胶囊、确认与浮动操作 |
| `LiquidToggle` | 官方 catalog，按需纳入 | 设置开关 |
| `LiquidSlider` | 官方 catalog，按需纳入 | 连续数值设置 |
| `XNoteLiquidGlassPanel` | catalog 无对应组件时的项目适配 | 卡片、工具栏与同窗口面板 |
| 平板 Navigation Rail | catalog 无竖向组件时的项目适配 | 平板一级导航 |

业务页面只能调用上述公共组件，不允许复制模糊、描边、阴影、按压动画或 Bottom Tabs 的选中滑块逻辑。新增组件前必须先核对当前 AndroidLiquidGlass 官方 catalog；存在对应实现时直接采用，不得创建功能重复的 XNote 版本。

### 6.3 视觉与状态

| 主题语义 | 浅色模式 | 深色模式 |
| -------- | -------- | -------- |
| `primary` / 主强调色 | `#E09F3E` | `#FFD60A` |

- 所有需要主强调色的组件通过 `MaterialTheme.colorScheme.primary` 读取当前主题值，不得在业务页面或组件内部另写近似色。
- `LiquidBottomTabs` 的选中图标与选中文字统一使用当前主题主色；未选中图标与文字继续使用中性前景色，保持导航层级清晰。
- 默认材质使用透明采样、柔和高光和低对比描边，前景文字与图标保持清晰。
- 主操作使用当前主题 `primary` Tint；普通操作使用中性 Tint；危险操作使用 `destructive` 前景色与轻量危险 Tint。
- 按下时缩放与高光变化必须轻微，不能造成按钮位置跳动。
- 选中状态通过 Tint、前景色和图标状态共同表达，不能只依赖颜色。
- 禁用状态必须降低前景与材质强调，同时保持文字可读，并彻底阻止点击。
- 加载状态保持按钮尺寸不变，防止布局位移；重复提交操作必须锁定。
- “减少动画”开启时取消形变和弹性动画，仅保留即时的颜色或透明度反馈。

## 7. 60% 平滑圆角

### 7.1 全局规则

所有由 XNote 绘制且包含圆角的界面元素，统一使用 `cornerSmoothing = 0.60` 的连续平滑曲线，包括：

- Liquid Glass 按钮、卡片和工具栏。
- Header、底部导航、侧边导航和浮动操作区的容器。
- Dialog、Drawer、Toast、Popup 和 DropdownMenu。
- 输入框、搜索框、筛选器、列表分组和状态容器。
- 图片、贴纸、预览区域及其选中、裁剪和焦点轮廓。

60% 是 XNote 的设计令牌，用于统一模拟 iOS 风格的连续圆角；它不是 Apple 对所有系统圆角公开规定的固定数值。SwiftUI 参照语义为 `RoundedRectangle(cornerRadius: radius, style: .continuous)`，Android 侧必须通过项目公共 Shape 实现等效曲线。

### 7.2 组件与实现约束

- Android 统一使用 `XNoteSmoothCornerShape`，默认 `smoothing` 固定为 `0.60`；业务组件只选择语义化半径令牌，不能覆盖平滑度。
- 禁止业务页面直接使用普通 `RoundedCornerShape`、局部 Bézier Path 或各自实现的 superellipse。
- 背景填充、内容裁剪、描边、阴影、Liquid Glass 背景采样、按压反馈和焦点轮廓必须复用同一个 Shape Path，不能出现边缘错位。
- 圆角半径由组件尺寸令牌决定；调整半径时仍保持 60% 平滑度，禁止通过改变平滑度模拟不同层级。
- Shape 计算结果应按尺寸与半径缓存，避免列表滚动和玻璃动画期间重复生成路径。
- Figma 或其他设计稿交付时，所有非圆形圆角将 Corner smoothing 设置为 60%；导出矢量资产时保留连续曲线路径。

### 7.3 适用边界

- 正圆按钮使用统一 `Circle`，胶囊按钮使用统一 `Capsule`；两者由几何形状本身确定，不再套用数值平滑度。
- Android 系统权限窗口、系统分享、输入法和其他非应用绘制界面不受本规范控制。
- 用户提供的图片、贴纸和画笔内容不改变原始轮廓；只有应用为其添加圆角容器时才应用 60% 平滑圆角。

## 8. 公共组件

### 8.1 组件目录

| 公共组件                 | 职责                                                 | 禁止行为                       |
| ------------------------ | ---------------------------------------------------- | ------------------------------ |
| `XNotePageScaffold`      | 页面骨架、安全区域、Header、Scroll Edge、Toast Host  | 承载业务数据请求               |
| `XNoteHeader`            | 二级页面返回、标题和右侧操作                         | 页面自定义高度或返回图标       |
| `LiquidButton`          | 官方 catalog 按钮材质与交互                          | 页面私有玻璃参数               |
| `LiquidBottomTabs`      | 官方 catalog 手机底部导航、选中滑块与拖拽交互        | 自写 tabbar 样式或选中动画      |
| `XNoteLiquidGlassPanel` | 官方 catalog 没有 Panel 时的卡片与面板适配           | 重复实现 Backdrop 或 Lens       |
| `XNoteDialog`            | 阻断式确认、危险操作、关键说明                       | 承载长表单或多级导航           |
| `XNoteDrawer`            | 长内容、选择器、辅助工作流                           | 替代简单确认弹窗               |
| `XNoteToastHost`         | 短时、非阻断反馈                                     | 承载需要用户决策的信息         |
| `XNotePopup`             | 锚定提示、预览和轻量操作                             | 承载破坏性确认                 |
| `XNoteDropdownMenu`      | 与锚点相关的离散操作列表                             | 展示复杂表单或无限层级         |
| `XNoteBackgroundPicker`  | 默认背景与单篇笔记专属背景的选择、预览和继承状态     | 绕过页面状态直接持久化业务数据 |
| `XNoteNoteSurface`       | 在编辑、预览、阅读和导出中渲染同一笔记背景与内容画布 | 绘制应用 Header 或工具栏       |
| `XNoteLoadingState`      | 页面、区域和按钮加载态                               | 隐藏可恢复错误                 |
| `XNoteEmptyState`        | 空数据说明与下一步操作                               | 使用无操作价值的装饰图         |
| `XNoteErrorState`        | 错误原因、重试和恢复入口                             | 只显示错误码                   |
| `XNoteRichTextToolbar`   | 普通笔记段落样式、行内样式、清单、对齐、表格与折叠   | 页面私有格式栏或直接改文档模型 |

所有包含圆角的公共组件必须使用 `XNoteSmoothCornerShape` 或统一的 `Circle`、`Capsule`，不得向页面层暴露 `cornerSmoothing` 参数。

### 8.2 复用规则

- 公共组件只接收展示数据、状态和回调；导航、数据请求与业务判断由页面层负责。
- 状态由调用方提升并保持单向数据流，组件不得在内部复制业务状态。
- 内容通过受控 Slot 扩展，不能通过任意参数暴露材质、阴影、圆角等基础视觉令牌。
- 同类组件必须共享进入、退出、按压、拖拽和加载反馈。
- 所有组件必须提供无障碍名称、角色、状态和必要的操作提示。
- 公共组件变更必须同时验证手机、平板、浅色、深色、字体放大和“减少动画”。

### 8.3 浮层选型

| 场景                                   | 使用组件                     |
| -------------------------------------- | ---------------------------- |
| 删除、转换、覆盖等必须确认的操作       | `XNoteDialog`                |
| 手机端长选择流程、图片来源、筛选与表单 | `XNoteDrawer` 底部形态       |
| 平板端辅助面板或长选择流程             | `XNoteDrawer` 侧边或锚定形态 |
| 保存成功、已恢复、网络中断等短反馈     | `XNoteToastHost`             |
| 针对某个控件的说明或轻量预览           | `XNotePopup`                 |
| 排序、更多、单组选项等锚定操作         | `XNoteDropdownMenu`          |

同一时刻只显示一个模态浮层。后显示的非模态 Toast 可以排队，但不能遮挡 Header 返回按钮、主要操作或系统导航区域。

## 9. Apple Notes 视觉一致性

### 9.1 色彩与材质

- 使用温和的浅色纸张背景与低亮度深色背景，正文保持最高对比度。
- 当前主题 `primary` 是笔记相关主强调色：浅色模式固定为 `#E09F3E`，深色模式固定为 `#FFD60A`；用于主操作、选中态和关键图标，不大面积铺满页面。
- 分隔线、次级文字和卡片边界保持低对比；依靠留白与分组建立层级。
- 玻璃材质主要用于固定、浮动和可交互控件，正文承载区优先保持稳定、平整和易读。
- 浅色、深色与高对比度模式均使用语义色，不在业务页面写死颜色。

### 9.2 笔记背景

- 笔记正文统一由 `XNoteNoteSurface` 承载，在普通笔记编辑、Markdown 编辑与预览、阅读模式、润色 Diff、导出预览和最终导出中复用相同背景渲染规则。
- 背景只覆盖笔记内容画布，不延伸到 Header、底部工具栏、导航栏、Dialog、Drawer、Toast、Popup 或 DropdownMenu。
- 编辑页的更多菜单提供“笔记背景”入口。手机使用 `XNoteDrawer` 底部形态，平板使用锚定面板或受限宽度 Drawer。
- `XNoteBackgroundPicker` 必须展示背景缩略图、选中状态、实时预览、“使用默认背景”选项，以及明确的影响范围说明。
- 编辑页背景选择器的影响范围文案固定为“仅当前笔记”；设置页中的默认背景选择器复用同一组件，影响范围文案为“所有未设置专属背景的笔记”。
- 背景缩略图、选中轮廓和预览卡片统一使用 60% 平滑圆角。
- 背景变化不能降低正文可读性；文本、光标、选择区、Diff 标记和图片控制柄根据背景明暗使用语义前景色或必要的对比遮罩。
- 背景加载期间保持内容可编辑；加载失败时显示默认背景和非阻断提示。
- 导出预览必须准确展示最终文件中的背景，且不能包含任何编辑控件或 Liquid Glass Overlay。

### 9.3 排版

- 使用系统字体和动态字号，不引入与系统气质冲突的装饰字体。
- 页面标题、笔记标题、分组标题、正文、辅助信息和时间信息使用统一文本令牌。
- 笔记正文优先保证舒适行高；统计数值和列表摘要不能挤压主要标题。
- 字号放大时允许 Header 标题省略、按钮文字收敛为图标，但主要功能不能消失。

### 9.4 布局与动效

- 手机页面水平边距默认 16 dp，平板默认 24 dp；阅读和编辑内容可以设置最大内容宽度并居中。
- 页面分组使用留白优先于重阴影，圆角只用于明确的容器或交互区域，并统一使用 60% 平滑圆角。
- 页面切换、抽屉、Popup 与按钮反馈使用短而克制的动画，避免持续漂浮、强烈弹跳或大范围折射。
- 内容滚动时 Header 保持稳定，Scroll Edge 负责表达固定层与内容层的关系。
- 支持系统返回手势、预测性返回、键盘避让和横竖屏切换。

## 10. 页面适配

### 10.1 手机

- 二级页面使用单栏结构和顶部 Header。
- 长选择、图片来源和筛选优先使用底部抽屉。
- 底部导航、编辑工具区和输入区均触发底部 Scroll Edge。
- Bottom Tabs 的玻璃容器底边直接贴合系统导航安全区，仅保留 8 dp 顶部间距，不再叠加额外底部间距。
- 浮动按钮不得遮挡列表最后一项，页面 Scaffold 负责补足底部内容间距。

### 10.2 平板

- 一级导航转换为左侧导航栏，内容区可使用双栏或多栏。
- 每一栏的独立滚动容器分别判断边缘效果，但同一视觉层只渲染一次，避免重复模糊。
- 详情栏仍使用统一 Header；左侧返回按钮在当前布局确实存在可返回层级时显示，否则保留等宽布局空间。
- 短菜单和选择器优先锚定显示，长流程使用侧边抽屉或受限宽度面板。

## 11. 无障碍与性能

- 所有图标按钮必须提供可朗读名称，不能只依赖图标外观表达含义。
- 文字和关键图标的对比度不得因玻璃背景变化而失效；必要时增加动态遮罩或切换高对比材质。
- 触控目标不得小于 44 × 44 dp，并为相邻危险操作保留足够间距。
- 屏幕阅读器顺序遵循：返回、标题、右侧操作、页面内容、底部操作。
- 动画、模糊与背景采样不能阻塞滚动；组件需要控制采样区域、叠层数量和动画范围以满足性能预算。
- 玻璃 Overlay 与被采样内容分层，禁止将玻璃组件录入自身背景造成递归采样。

## 12. 验收清单

每个页面交付前至少验证：

- 页面由 `XNotePageScaffold` 承载，并接入所需顶部与底部 Scroll Edge。
- 二级页面使用统一 Header、圆形 Liquid Glass 返回按钮和唯一的 SVG 返回图标。
- 所有界面矢量图标均能追溯到当前固定版本的 Lucide 官方资源，文件命名、24 × 24 视口、2 单位圆端描边和颜色语义符合图标规范。
- 非编辑页的 Header 中间显示准确的页面标题，且标题在左右操作不对称时仍视觉居中。
- 编辑页的笔记标题位于正文，不与 Header 重复。
- 手机一级导航直接来自 `LiquidBottomTabs` / `LiquidBottomTab`，不存在自写 tabbar 样式或选中滑块。
- Bottom Tabs 在静止、按压放大和拖拽状态下，选中胶囊均以当前 tab 中心为锚点，不向任一侧产生非交互预期的偏移。
- Bottom Tabs 按压时，选中项的中性基础图标与文字不会残留在原位置，且另外两个未选中项不会随之缩放。
- 从 Bottom Tabs 内任意位置按下并横向拖动时，滑块从落点立即跟手，松手后选中最近的 tab；普通点击仍能准确切换目标。
- Bottom Tabs 的选中图标和文字在浅色模式使用 `#E09F3E`，在深色模式使用 `#FFD60A`，且不存在组件私有强调色。
- 页面中的独立按钮全部来自官方 catalog `LiquidButton`；只有 catalog 缺少的组件才允许项目级适配。
- Dialog、Drawer、Toast、Popup 和 DropdownMenu 均来自公共组件，不存在页面私有副本。
- 普通笔记编辑页的格式工具栏来自 `XNoteRichTextToolbar`，不存在页面私有格式栏。
- 编辑页可通过公共背景选择器修改当前单篇笔记背景，设置页可修改默认背景，并准确展示各自影响范围。
- 继承默认背景与设置专属背景的笔记均按优先级显示正确背景；移动笔记所属的笔记本不会改变背景。
- 导出预览与最终导出包含相同背景，且不包含 Header、工具栏、按钮或 Liquid Glass Overlay。
- 所有应用自绘圆角均来自公共 Shape，平滑度固定为 60%；不存在普通圆角与连续圆角混用。
- 填充、裁剪、描边、阴影、玻璃采样、按压反馈和焦点轮廓的圆角路径完全重合。
- 浅色、深色、平板、横屏、字体放大和减少动画状态均可用。
- 页面视觉接近 Apple Notes 的内容层级和克制风格，同时保留 Android 返回、无障碍和窗口适配能力。

## 13. 技术参考

- [Apple SwiftUI：ScrollEdgeEffectStyle](https://developer.apple.com/documentation/swiftui/scrolledgeeffectstyle)
- [Apple SwiftUI：Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views)
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
- [AndroidLiquidGlass LiquidBottomTabs 官方 catalog 源码](https://github.com/Kyant0/AndroidLiquidGlass/blob/65ab177e90e5c1d8c62e70cf7755841982da65f6/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt)
- [Apple SwiftUI：RoundedRectangle continuous corner style](https://developer.apple.com/documentation/swiftui/roundedrectangle/init%28cornerradius%3Astyle%3A%29)
- [Android Developers：Compose layouts basics](https://developer.android.com/develop/ui/compose/layouts/basics)
- [Android Developers：Compose Scaffold](https://developer.android.com/develop/ui/compose/components/scaffold)
- [Android Developers：Compose menus](https://developer.android.com/develop/ui/compose/components/menu)
- [Lucide Icons：图标目录](https://lucide.dev/icons/)
- [Lucide Guide：图标基础](https://lucide.dev/guide/basics)
- [Lucide：ISC License](https://lucide.dev/license)
