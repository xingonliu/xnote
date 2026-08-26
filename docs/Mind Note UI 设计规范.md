# Mind Note UI 设计规范

> 文档版本：v0.1
>
> 适用平台：Android 手机、Android 平板
>
> 视觉基准：Apple Notes
>
> 关联文档：[Mind Note 功能清单与页面组成](./Mind%20Note%20功能清单与页面组成.md)

## 1. 目标与原则

Mind Note 的界面以 Apple Notes 的清晰、克制和内容优先为视觉方向，在 Android 与平板形态中保持一致的组件语义和交互反馈。

所有新页面和重构页面必须遵守以下原则：

1. 笔记内容始终是视觉主体，装饰效果不能降低文字、图片和画笔内容的可读性。
2. 按钮统一使用 Liquid Glass 材质，并通过形状、尺寸和前景色表达层级，不为同一功能重复设计局部样式。
3. 所有页面统一接入 `ScrollEdgeEffectStyle` 视觉规范；页面内容可滚动时，固定 Header、底部工具区与滚动内容之间必须有连续的边缘过渡。
4. 二级页面统一使用公共 Header；除笔记编辑页外，Header 中间显示当前页面标题。
5. 弹窗、抽屉、Toast、Popup、下拉菜单、按钮等通用交互只能通过公共组件提供，业务页面不得复制实现。
6. 优先使用系统字体、系统手势、动态字号、无障碍语义和平台返回行为；视觉接近 Apple Notes 不等于破坏 Android 的基础交互习惯。

## 2. 平台与技术边界

`ScrollEdgeEffectStyle` 是 SwiftUI 中用于定义滚动视图边缘模糊过渡的类型，`scrollEdgeEffectStyle(_:for:)` 用于配置指定边缘。Mind Note 当前是 Android/Jetpack Compose 应用，因此本文中的 `ScrollEdgeEffectStyle` 表示项目级视觉规范，不表示 Android 代码直接调用 SwiftUI API。

Android 实现统一封装为 `MindNoteScrollEdgeEffect`，由 `MindNotePageScaffold` 接入。它至少包含以下能力：

- 根据滚动状态分别控制顶部和底部边缘效果。
- 默认提供 `soft`，需要强化固定控件边界时提供 `hard`。
- 使用 AndroidLiquidGlass 可用的背景采样、模糊和渐变能力实现；不支持完整效果时降级为渐变叠层与半透明材质。
- 不拦截触摸、滚动、选择、拖放或无障碍焦点。
- 遵守“减少动画”、高对比度和低性能降级设置。

SwiftUI 语义与 Compose 项目语义的映射如下：

| 规范语义 | SwiftUI 参照 | Mind Note Android 实现 |
|---|---|---|
| 柔和滚动边缘 | `ScrollEdgeEffectStyle.soft` | `MindNoteScrollEdgeStyle.Soft` |
| 清晰滚动边界 | `ScrollEdgeEffectStyle.hard` | `MindNoteScrollEdgeStyle.Hard` |
| 指定生效边缘 | `scrollEdgeEffectStyle(_:for:)` | `MindNoteScrollEdgeEffect(edges, style)` |
| 页面统一接入 | View hierarchy modifier | `MindNotePageScaffold` 内置效果层 |
| 玻璃按钮 | `.buttonStyle(.glass)` | `MindNoteLiquidGlassButton` |
| 自定义玻璃形状 | `glassEffect(_:in:)` | AndroidLiquidGlass 的项目级 Shape 封装 |

## 3. 页面骨架与 Scroll Edge

### 3.1 统一页面骨架

所有一级和二级页面必须以 `MindNotePageScaffold` 为根容器，由它统一处理：

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

所有二级页面使用 `MindNoteHeader`，结构固定为：

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

| 项目 | 手机 | 平板 |
|---|---:|---:|
| Header 内容高度 | 44 dp | 44 dp |
| Header 水平安全边距 | 16 dp | 24 dp |
| 圆形按钮 | 44 × 44 dp | 44 × 44 dp |
| SVG 图标视觉尺寸 | 20 × 20 dp | 20 × 20 dp |
| 标题与两侧最小间距 | 12 dp | 16 dp |

所有按钮的最小可点击区域为 44 × 44 dp。SVG 使用 `currentColor` 语义，由组件根据普通、按下、禁用和危险状态提供前景色；业务页面不得维护不同版本的返回图标。

### 4.3 笔记编辑页例外

普通笔记编辑页和 Markdown 编辑页不在 Header 中间显示固定页面标题。笔记标题属于文档内容，统一置于正文顶部并可编辑；Header 中间保持空白，可仅展示短暂的保存状态，但不能与笔记标题形成重复标题。

编辑页仍必须使用统一的左侧圆形 SVG 返回按钮、右侧功能按钮和顶部 Scroll Edge。

### 4.4 页面标题与右侧功能

| 页面 | Header 标题 | 右侧主要功能 |
|---|---|---|
| 笔记本详情 | 当前笔记本名称 | 更多 |
| 润色 Diff | 润色结果 | 无或帮助 |
| 阅读模式 | 阅读模式 | 目录、编辑 |
| 图片编辑与抠图 | 图片编辑 | 完成 |
| 画板 | 画板 | 完成 |
| 导出预览 | 导出预览 | 分享 |
| Agent 对话 | 当前会话标题 | 更多 |
| 笔记范围选择 | 笔记范围 | 完成 |
| 工具调用详情 | 工具调用详情 | 重试或取消，按状态显示 |
| 搜索页 | 搜索 | 清空，仅存在历史记录时显示 |
| 统计 | 统计 | 无 |
| 回收站 | 回收站 | 选择、更多 |
| 贴纸库 | 贴纸库 | 添加 |
| 模型与服务商 | 模型与服务商 | 添加 |
| Agent 权限 | Agent 权限 | 无 |
| Skill 管理 | Skill 管理 | 安装 |
| MCP 管理 | MCP 管理 | 添加 |
| Linux 环境 | Linux 环境 | 更多 |
| 存储与隐私 | 存储与隐私 | 无 |
| 外观与辅助功能 | 外观与辅助功能 | 无 |

危险操作不直接作为 Header 的常驻主按钮；永久删除、清空、重置等操作放入更多菜单或确认弹窗。

## 5. Liquid Glass 按钮

### 5.1 使用范围

所有独立按钮统一使用 `MindNoteLiquidGlassButton` 系列，包括：

- Header 图标按钮。
- 底部导航与侧边导航按钮。
- 浮动按钮和编辑工具按钮。
- 胶囊按钮、确认按钮和筛选按钮。
- 弹窗、抽屉、Popup 与下拉菜单中的独立操作按钮。

列表整行点击区域、文本输入框、开关、单选项和复选项属于对应控件，不额外包裹玻璃按钮；行内独立的图标操作仍使用玻璃按钮。

### 5.2 按钮类型

| 类型 | 形状 | 典型用途 |
|---|---|---|
| `Icon` | 圆形 | 返回、更多、搜索、关闭 |
| `Capsule` | 胶囊 | 完成、保存、筛选、模式切换 |
| `Rect` | 连续圆角矩形 | 弹窗主操作、较长文字操作 |
| `Floating` | 圆形或胶囊 | 新建笔记、发送、编辑 |

所有类型共享同一套材质、交互状态、无障碍语义和性能降级逻辑，不允许页面复制模糊、描边、阴影或按压动画。

### 5.3 视觉与状态

- 默认材质使用透明采样、柔和高光和低对比描边，前景文字与图标保持清晰。
- 主操作使用 `accentNoteYellow` Tint；普通操作使用中性 Tint；危险操作使用 `destructive` 前景色与轻量危险 Tint。
- 按下时缩放与高光变化必须轻微，不能造成按钮位置跳动。
- 选中状态通过 Tint、前景色和图标状态共同表达，不能只依赖颜色。
- 禁用状态必须降低前景与材质强调，同时保持文字可读，并彻底阻止点击。
- 加载状态保持按钮尺寸不变，防止布局位移；重复提交操作必须锁定。
- “减少动画”开启时取消形变和弹性动画，仅保留即时的颜色或透明度反馈。
- 完整 Liquid Glass 不可用时，回退到统一的半透明填充、细描边和最小阴影，不改变组件尺寸与交互语义。

## 6. 公共组件

### 6.1 组件目录

| 公共组件 | 职责 | 禁止行为 |
|---|---|---|
| `MindNotePageScaffold` | 页面骨架、安全区域、Header、Scroll Edge、Toast Host | 承载业务数据请求 |
| `MindNoteHeader` | 二级页面返回、标题和右侧操作 | 页面自定义高度或返回图标 |
| `MindNoteLiquidGlassButton` | 所有独立按钮及交互状态 | 页面私有玻璃参数 |
| `MindNoteDialog` | 阻断式确认、危险操作、关键说明 | 承载长表单或多级导航 |
| `MindNoteDrawer` | 长内容、选择器、辅助工作流 | 替代简单确认弹窗 |
| `MindNoteToastHost` | 短时、非阻断反馈 | 承载需要用户决策的信息 |
| `MindNotePopup` | 锚定提示、预览和轻量操作 | 承载破坏性确认 |
| `MindNoteDropdownMenu` | 与锚点相关的离散操作列表 | 展示复杂表单或无限层级 |
| `MindNoteLoadingState` | 页面、区域和按钮加载态 | 隐藏可恢复错误 |
| `MindNoteEmptyState` | 空数据说明与下一步操作 | 使用无操作价值的装饰图 |
| `MindNoteErrorState` | 错误原因、重试和恢复入口 | 只显示错误码 |

### 6.2 复用规则

- 公共组件只接收展示数据、状态和回调；导航、数据请求与业务判断由页面层负责。
- 状态由调用方提升并保持单向数据流，组件不得在内部复制业务状态。
- 内容通过受控 Slot 扩展，不能通过任意参数暴露材质、阴影、圆角等基础视觉令牌。
- 同类组件必须共享进入、退出、按压、拖拽和加载反馈。
- 所有组件必须提供无障碍名称、角色、状态和必要的操作提示。
- 公共组件变更必须同时验证手机、平板、浅色、深色、字体放大和“减少动画”。

### 6.3 浮层选型

| 场景 | 使用组件 |
|---|---|
| 删除、转换、覆盖等必须确认的操作 | `MindNoteDialog` |
| 手机端长选择流程、图片来源、筛选与表单 | `MindNoteDrawer` 底部形态 |
| 平板端辅助面板或长选择流程 | `MindNoteDrawer` 侧边或锚定形态 |
| 保存成功、已恢复、网络中断等短反馈 | `MindNoteToastHost` |
| 针对某个控件的说明或轻量预览 | `MindNotePopup` |
| 排序、更多、单组选项等锚定操作 | `MindNoteDropdownMenu` |

同一时刻只显示一个模态浮层。后显示的非模态 Toast 可以排队，但不能遮挡 Header 返回按钮、主要操作或系统导航区域。

## 7. Apple Notes 视觉一致性

### 7.1 色彩与材质

- 使用温和的浅色纸张背景与低亮度深色背景，正文保持最高对比度。
- `accentNoteYellow` 是笔记相关主强调色，用于主操作、选中态和关键图标，不大面积铺满页面。
- 分隔线、次级文字和卡片边界保持低对比；依靠留白与分组建立层级。
- 玻璃材质主要用于固定、浮动和可交互控件，正文承载区优先保持稳定、平整和易读。
- 浅色、深色、高对比度与降低透明度模式均使用语义色，不在业务页面写死颜色。

### 7.2 排版

- 使用系统字体和动态字号，不引入与系统气质冲突的装饰字体。
- 页面标题、笔记标题、分组标题、正文、辅助信息和时间信息使用统一文本令牌。
- 笔记正文优先保证舒适行高；统计数值和列表摘要不能挤压主要标题。
- 字号放大时允许 Header 标题省略、按钮文字收敛为图标，但主要功能不能消失。

### 7.3 布局与动效

- 手机页面水平边距默认 16 dp，平板默认 24 dp；阅读和编辑内容可以设置最大内容宽度并居中。
- 页面分组使用留白优先于重阴影，圆角只用于明确的容器或交互区域。
- 页面切换、抽屉、Popup 与按钮反馈使用短而克制的动画，避免持续漂浮、强烈弹跳或大范围折射。
- 内容滚动时 Header 保持稳定，Scroll Edge 负责表达固定层与内容层的关系。
- 支持系统返回手势、预测性返回、键盘避让和横竖屏切换。

## 8. 页面适配

### 8.1 手机

- 二级页面使用单栏结构和顶部 Header。
- 长选择、图片来源和筛选优先使用底部抽屉。
- 底部导航、编辑工具区和输入区均触发底部 Scroll Edge。
- 浮动按钮不得遮挡列表最后一项，页面 Scaffold 负责补足底部内容间距。

### 8.2 平板

- 一级导航转换为左侧导航栏，内容区可使用双栏或多栏。
- 每一栏的独立滚动容器分别判断边缘效果，但同一视觉层只渲染一次，避免重复模糊。
- 详情栏仍使用统一 Header；左侧返回按钮在当前布局确实存在可返回层级时显示，否则保留等宽布局空间。
- 短菜单和选择器优先锚定显示，长流程使用侧边抽屉或受限宽度面板。

## 9. 无障碍与性能

- 所有图标按钮必须提供可朗读名称，不能只依赖图标外观表达含义。
- 文字和关键图标的对比度不得因玻璃背景变化而失效；必要时增加动态遮罩或切换高对比材质。
- 触控目标不得小于 44 × 44 dp，并为相邻危险操作保留足够间距。
- 屏幕阅读器顺序遵循：返回、标题、右侧操作、页面内容、底部操作。
- 动画、模糊与背景采样不能阻塞滚动；低性能设备自动使用静态材质降级。
- 玻璃 Overlay 与被采样内容分层，禁止将玻璃组件录入自身背景造成递归采样。

## 10. 验收清单

每个页面交付前至少验证：

- 页面由 `MindNotePageScaffold` 承载，并接入所需顶部与底部 Scroll Edge。
- 二级页面使用统一 Header、圆形 Liquid Glass 返回按钮和唯一的 SVG 返回图标。
- 非编辑页的 Header 中间显示准确的页面标题，且标题在左右操作不对称时仍视觉居中。
- 编辑页的笔记标题位于正文，不与 Header 重复。
- 页面中的独立按钮全部来自 `MindNoteLiquidGlassButton` 系列。
- Dialog、Drawer、Toast、Popup 和 DropdownMenu 均来自公共组件，不存在页面私有副本。
- 浅色、深色、平板、横屏、字体放大、减少动画和 Liquid Glass 降级状态均可用。
- 页面视觉接近 Apple Notes 的内容层级和克制风格，同时保留 Android 返回、无障碍和窗口适配能力。

## 11. 技术参考

- [Apple SwiftUI：ScrollEdgeEffectStyle](https://developer.apple.com/documentation/swiftui/scrolledgeeffectstyle)
- [Apple SwiftUI：Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views)
- [Android Developers：Compose layouts basics](https://developer.android.com/develop/ui/compose/layouts/basics)
- [Android Developers：Compose Scaffold](https://developer.android.com/develop/ui/compose/components/scaffold)
- [Android Developers：Compose menus](https://developer.android.com/develop/ui/compose/components/menu)
