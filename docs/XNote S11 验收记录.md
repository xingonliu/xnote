# XNote S11 验收记录

## S11.1 事实源与权限基础

实现入口：`domain/agent/AgentContracts.kt`、`AgentPermissionPolicy.kt`、`AgentEditPolicy.kt`，以及 `data/db/AgentEntities.kt`、`AgentDao.kt`、`data/agent/AgentPermissionStore.kt`。

- Room 版本 4 新增独立的消息、片段、运行、队列、工具事件、权限、发送快照及引用、改动来源、单篇审阅、附件引用表。消息具有稳定 ID 和顺序；运行与队列固定模型配置 ID/版本；工具调用按运行/调用 ID 唯一。审阅与改动来源不进入 `NoteDocument`。
- 3→4 自动迁移只添加表；2→3→4 保留已有迁移路径。不再使用缺失迁移时删除重建的回退；没有迁移路径时明确失败并保留原库。
- 初始权限为不可查看、仅附加范围。当前正文读取、编辑和发送快照读取分别判定；回收站与已永久删除笔记不可读。多笔记本范围与权限等级独立。
- 用户每次保存权限递增 revision；运行临时授权和一级发送快照授权绑定 revision，设置变化后旧授权不能继续生效。模型不持有设置写入口。
- 创建目标只来自当前可写范围或用户本次授权；多个目标要求选择；仅附加范围无默认新建目标。新建后的临时编辑权只覆盖本次运行中新建笔记，不扩大笔记本授权。
- 领域校验拒绝未知 JSON 字段、非法文档结构、过期版本和选区，以及媒体内容、变换和相对顺序变动。背景和既有归属不属于可编辑正文输入。
- 接受后的撤回保留期固定为 30 天（边界不含第 30 天）；删除拒绝区分用户已恢复、原笔记本不存在和永久删除。实际差异合并、整篇回退事务及 UI 在 S11.6–S11.7 接入，本片不开启写工具。
- 附件回收计入 Agent 独立引用，后续消息与快照生命周期由 S11.5、S11.11 接入。

验证用例：`AgentPermissionPolicyTest`（权限矩阵、快照生命周期、撤权、创建归属）、`AgentEditPolicyTest`（媒体与结构边界、版本与选区、撤回期限和恢复判定）、`AgentFoundationTest`（3→4 保留正文、附件文件与设置，事实源与权限重开）、`NotebookMigrationTest`（2→3→4）。2026-09-22：112 项单元测试及 Android 16 模拟器上上述 3 项端侧测试全部通过；`testDebugUnitTest`、`lintDebug`、`assembleDebug`、专项 `connectedDebugAndroidTest` 成功。Lint 0 错误，16 条既有依赖版本、公共控件及资源警告。

迁移依据：[Room 官方迁移文档](https://developer.android.com/training/data-storage/room/migrating-db-versions)，通过 Context7 核查自动迁移、schema 导出及删除重建回退的行为。

## S11.2 三家模型接入与配置

实现入口：`domain/agent/ModelContracts.kt`、`data/agent/Model*`、`feature/agent/ModelSettingsScreen.kt`。

- P18 从“我的 → 模型与服务商”进入；主页只展示已配置列表与新增入口；新增和编辑为独立页面，编辑页管理测试、默认项和删除。预设从 Models.dev 拉取厂商、官方模型 ID、发布日期、容量和 SDK 适配信息；厂商缺少 `api` 时，通过 All LLM Provider List 的 slug/aliases 匹配并补齐官方基地址。两份 JSON 在运行时并行拉取，不内置厂商地址表或模型名单。主流厂商优先，其余按厂商名称排序；同厂商按发布时间倒序。厂商、模型、自定义协议复用 `XNoteSelectField` 与 `XNoteSelectMenu`，由 `XNoteDropdownMenu` 提供锚定菜单、搜索与滚动。选择后补全官方地址、协议、模型及预算，使用对应厂商自己的 API Key；切换厂商清空当前输入的密钥。自定义手填地址和模型 ID，支持三种协议。目录失败可重试或使用自定义，编辑已有配置不依赖目录成功。
- Room 5 新增 Profile 表，4→5 自动迁移保留 Agent 事实源。普通配置不包含 API Key；凭据使用 Android Keystore AES/GCM 加密，密文位于 `noBackupFilesDir/model-credentials`。替换和删除成功后移除旧密文。界面不回填密钥，临时输入不写入保存状态。
- 配置保存递增版本；当前运行和待处理队列使用的配置禁止替换、删除或切换默认项。修改无关配置不会干扰当前运行；无有效默认项时明确要求处理，不自动选其他配置。认证信息只进入请求 Header，不放进 URL、消息、普通配置或错误日志。
- 统一消息、文字增量、工具调用、工具结果、用量与结束原因；OpenAI 需要结束原因和 `[DONE]`，Anthropic 需要结束原因和 `message_stop`，Gemini 需要 `finishReason`。EOF 不冒充完成；输出上限与过滤结果独立于正常完成。Gemini 工具回传保留原始 parts 与 thoughtSignature。
- 能力测试提示最多三次请求及可能产生费用；先测试文字流式，再用无副作用的函数验证调用及结果回传。仅文字成功时仍可聊天，工具保持未验证。模型、厂商适配、服务、密钥或预算配置变化使能力验证失效。
- OkHttp 5.5.0 处理 HTTPS、取消与超时；不自动重试或跟随重定向。连接/写入 15 秒、读取 90 秒、单请求总时长 180 秒；SSE 单事件最多 1 Mi 字符、总响应最多 4 Mi 字符。网络、认证、配额、服务、协议、超时分别给出固定提示，不展示服务错误正文。
- 两份目录请求均不携带用户凭据，各 15 秒总超时、响应上限 8 MiB，不自动重试或跟随重定向；仅选择支持文字输入与输出的模型，不自动证明工具能力。预设上下文采用目录值（不超过本地 200 万上限），输出取 4096、目录输出上限及上下文预留后的较小值；用户可调整。
- 自定义 Profile 默认上下文 32768 Token、输出 4096 Token，要求用户依据服务能力填写；它们是预算输入，不能作为已验证的模型容量。测试输出最多 1024 Token；工具预算预留 1024 Token。用量取服务返回值，不显示推测费用。

验证：`ModelProtocolTest` 在三种协议上覆盖文字、取消、HTTP 认证失败、工具参数分片与结果序列化、结束标记、SSE 边界和秘密隔离；`ModelProfileStoreTest` 验证 Keystore 密文、删除及配置锁；`ModelSettingsFlowTest` 验证新增、编辑、能力状态与删除。2026-09-22 上述测试通过。通过本地 `XNOTE_LIVE_CONFIG` 指向用户的 `apikey.env`，OpenAI 兼容服务上的 `gpt-5.6-terra` 已通过文字流式、工具调用及工具结果回传三步真实请求；密钥不写进代码或测试输出。Anthropic、Gemini 当前为受控协议测试，尚无对应真实服务凭据；三家真实服务全覆盖仍由 S11.12 验收。

目录规则：依据远端 SDK 类型映射已实现的 OpenAI 兼容、Anthropic Messages 和 Gemini 协议。主目录地址优先，补充目录仅填缺失地址；根域名形式的 Anthropic/Gemini 地址补齐对应版本路径。缺失地址、不安全 URL、未知适配器、模型级接口覆盖、已弃用或不支持文字的条目不展示。当前不提供仅支持 Responses 的 OpenAI 专用模型。远端刷新不会改写已保存配置，离线编辑沿用其厂商名称、地址、协议与模型。主流排序为 OpenAI、Anthropic、Google、DeepSeek、智谱/Z.AI、Kimi、MiniMax、Qwen、xAI。目录数据不是可用性或工具能力证明，仍需用户测试连接。

目录来源：[Models.dev](https://models.dev)、[All LLM Provider List](https://github.com/foisalislambd/all-llm-provider-list)。前者通过 Context7 核查 `/api.json` 格式；后者读取公开 `data/providers.json`，用于缺失地址补全。预设使用各厂商官方账户与 Key，未以所有厂商真实付费推理作为验收证据。

2026-09-23 动态配置验收：133 项单元测试中 132 项通过、1 项真实服务测试因未提供凭据跳过。目录测试覆盖远端新增厂商、原生模型 ID、别名地址补全、地址变更、三种协议映射、主流排序、容量边界、无效地址与未知适配器过滤。端侧 `ModelSettingsFlowTest` 4 项、`ModelProfileStoreTest` 2 项、`XNoteDesignSystemTest` 15 项通过，覆盖新增/编辑/删除、离线编辑、厂商切换清空密钥、模型联动、50 项下拉滚动与搜索、公共浮层定位和退出。已查看下拉菜单及独立配置页截图。`assembleDebug` 成功，`lintDebug` 0 错误、16 条既有警告。

协议依据：[OpenAI Chat Completions 流式事件](https://developers.openai.com/api/reference/resources/chat/subresources/completions/streaming-events)、[Anthropic 流式 Messages](https://platform.claude.com/docs/en/build-with-claude/streaming)、[Gemini GenerateContent](https://ai.google.dev/api/generate-content)、[Android Keystore](https://developer.android.com/privacy-and-security/keystore)、[OkHttp 取消与超时](https://square.github.io/okhttp/recipes/)。Anthropic、Gemini 及 OkHttp 文档同时通过 Context7 核查。

## S11.3 单时间线与流式对话

实现入口：`domain/agent/AgentContextBudget.kt`、`data/agent/AgentTimeline.kt`、`feature/agent/AgentScreen.kt`。

- P10 通过应用级运行对象使用明确保存的默认配置；每次运行绑定配置版本。用户消息、助手占位、运行记录和草稿清空在同一事务提交，流式正文逐步保存，最终消息与运行状态在同一事务提交。
- Room 6 新增独立草稿表，5→6 自动迁移。冷启动恢复同一时间线和草稿，将遗留运行及未完成消息标记中断；未完成任务可保留内容并结束。失败、取消、断流与正常完成独立，输出上限和服务上下文超限进入容量暂停状态。
- 单条输入先按预算校验；超限保留完整草稿、不创建运行、不发送请求。预算从配置容量扣除最大输出及 1024 Token 工具预留；输入估算采用 UTF-8 字节数加每条 64 Token 开销，明确属于保守估算，不替代服务 Token 用量。
- 上下文只收录当前话题内已完成的完整问答对，排除带笔记来源的历史。接近预算时按最近优先选择完成轮次，每条长消息保留首尾各 120 字符并标记省略；当前输入始终保留全文。完整时间线不截断，模型摘要及长期召回由 S11.8 继续实现。
- 容量片段仅在上一运行已结束、发送下一条输入前关闭；“开始新话题”保留时间线并明确重置后续请求历史。此片未开放真实工具；模型异常返回工具调用会失败，不能触发笔记操作。
- 手机键盘打开时收起底部导航及空状态说明，为输入和发送保留空间；收起键盘后恢复导航。文字可选择复制；服务用量仅显示实际返回值。

验证：`AgentContextBudgetTest` 覆盖完整当前输入、完成轮次压缩、容量边界及保守估算；`AgentTimelineTest` 覆盖草稿超限、流式持久化、断流、输出/上下文超限、取消、磁盘库重开恢复、新话题上下文隔离；`AgentFlowTest` 覆盖发送、键盘布局、页面往返和新话题。2026-09-22 Android 16 模拟器上真实 `AgentLiveTimelineTest` 通过，从受保护凭据读取到真实回复及最终事实落库完整验证；测试传入的临时明文文件随即删除，结束后移除测试凭据。

最终构建：`testDebugUnitTest`、`lintDebug`、`assembleDebug` 成功；128 项单元测试中 127 项通过，1 项真实服务测试默认跳过，该项已单独显式开启并通过。Lint 0 错误、16 条既有警告。

端侧最终记录（Android 16、720×1280）：S11 事实源/迁移、模型配置、时间线及两页 UI 专项共 14 项通过，真实 Android 对话另行通过。检查手机配置页、键盘输入及完整对话截图，发送按钮可见、页面往返保留记录。

扩大到全部端侧用例时，运行 112 项出现 7 项失败，不能视为全套回归通过。对照本次接入前的 `f7424b3` 基线，复现 5 项相同失败：`existingTableAtDocumentEndAllowsTypingAfterItAndSaving`、`tabletPanesKeepDraftAndSelectionAcrossSearchResizeAndRecreation`、`wideWindowKeepsTheNavigationRailWhileSearchExpandsInTheListPane`、`notebookGridAdaptsToThemeWindowAndLargeText`、`edgesFadeIntoBothThemeBackgroundsAndRestoreWhenHidden`。其余 `insertBubbleAndImagePillWorkInEditor`、`existingImageAtDocumentEndAllowsTypingAfterItAndSaving` 在当前版本与基线单独重跑均通过，记录为不稳定用例；本次未改动这些用例或编辑器业务逻辑。完整手机/平板回归仍归入 S11.12。

## S11.4 运行控制、队列与后台恢复（进行中）

实现入口：`data/agent/AgentTimeline.kt`、`AgentRunService.kt`、`domain/agent/AgentRunLimits.kt`、`feature/agent/AgentScreen.kt`。

- 运行中发送保存为当前运行的待补充消息，在完整响应边界消费。结束判定与补充提交共用互斥锁；原始目标、此前部分回复及补充完整参与后续请求预算。已完成历史可以压缩，当前执行不能静默截断。
- 队列消息落库，支持编辑、删除及上下排序，绑定模型 ID/版本。取出消息、创建运行及标记已派发处于同一事务；成功后顺序派发下一项，停止、失败、中断或容量暂停时暂停队列。冷启动不自动派发，用户选择继续。队列存在时设置页继续锁定所绑定配置。
- 继续原运行保留部分回复并追加执行事件，不创建新的目标或切换模型。继续前检查工具提交记录，结果未知或仍在执行的工具阻止继续。当前工具审计骨架记录未经开放的调用并拒绝执行；真实工具及本地提交核查随 S11.5–S11.7 接入。
- 运行默认最多 16 次模型请求（计入重试，跨继续累计）、每次执行窗口最多 10 分钟、队列最多 50 条。仅对尚未产生正文的网络、服务和超时错误重试两次，间隔 1 秒、2 秒；认证、配额、非法请求及已产生正文的错误不自动重试。参数集中在 `AgentRunLimits`。
- `dataSync` 前台服务承载模型网络请求，使用有限时长的部分唤醒锁；离页、后台与熄屏可继续。通知提供停止入口。通知未开启时页面说明可见性限制并提供授权入口；应用内停止始终可用。服务启动失败、系统超时或服务销毁进入可恢复中断，使用 `START_NOT_STICKY` 避免系统自动重放请求。取消清除运行临时授权。
- 原生服务回调在请求协程启动前触发取消时，也必须进入持久化取消处理，避免数据库残留“运行中”。协程以 `ATOMIC` 启动，先完成前台服务启动请求，再允许模型请求；取消后的本地状态提交使用 `NonCancellable`。
- 消息支持复制、单条删除及清空聊天。删除单条消息保留其他时间线条目，但使整轮失去后续上下文资格，防止相关回复重传被删除输入。清空前停止任务、清除队列与快照引用；独立笔记改动及审阅表不受影响。清空操作使用确认弹层。

当前验证：134 项单元测试中 133 项通过，真实服务测试因未显式提供凭据跳过；`testDebugUnitTest`、`lintDebug`、Debug APK 构建和安装通过。Lint 0 错误、18 条现有依赖/公共控件/资源警告。运行层端侧测试覆盖补充及队列恰好一次派发、编辑排序、停止暂停与配置锁、有限重试、清空取消、继续、磁盘库重开、删除历史安全投影、累计循环限制、服务启动拒绝和启动前取消。界面测试覆盖发送、页面往返、新话题；已检查 720×1280 手机截图。

2026-09-24：运行层 16 项、界面 1 项、通知停止 1 项共 18 项端侧专项全部通过；通知权限拒绝用例在预先撤权后独立执行并通过（合计 19 项）。

原生后台测试使用无真实凭据的保留测试地址挂起有界请求，验证系统前台服务、桌面/熄屏及通知停止；它不是模型服务联调证据。通知拒绝用例必须在启动 instrumentation 前撤销权限，避免测试进程自身被权限撤销终止：

```text
adb shell pm revoke com.xnote.app android.permission.POST_NOTIFICATIONS
adb shell am instrument -w -e class com.xnote.app.data.AgentBackgroundTest#deniedNotificationsKeepForegroundExecutionAndInAppStopAvailable com.xnote.app.test/androidx.test.runner.AndroidJUnitRunner
```

尚待完成的 S11.4 验收：实际杀进程后重启并继续、Android 系统 `dataSync` 超时回调及后台启动限制的端到端验证。只读工具等待授权后的运行对象重建已在 S11.5 验证，实际进程终止、写入提交恢复和冲突等待仍需完成；S11.4 尚未标记完整验收，S11.5 进行中，S11.6–S11.7 待开发。

平台依据：[前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[服务超时](https://developer.android.com/develop/background-work/services/fgs/timeout)、[通知权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)、[CoroutineStart.ATOMIC](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-start/-a-t-o-m-i-c/)。Android 与协程 API 均通过 Context7 核查。

## S11.5 发送快照、范围与只读工具（进行中）

实现入口：`AgentNoteStore.kt`、`AgentConversationContext.kt`、`AgentNoteTools.kt`、`AgentNoteDialogs.kt`，以及时间线的工具循环。

- Room 7 在消息中新增可恢复的模型交互 JSON、工具事件中新增笔记来源、草稿中新增所选笔记 ID；6→7 自动迁移保留已有记录。原有 2→3→4→5→6 迁移链继续衔接。模型原始函数调用与 Gemini 原始 parts/签名独立保存，可在用户继续时恢复完整工具调用与结果配对。
- 附加笔记在发送或加入队列时原子保存标题、结构化正文、背景与归属快照，并绑定稳定版本和消息。相同版本复用快照；媒体仅增加独立引用，不复制文件。正文后来变更不会替换旧快照，卡片提供只读预览。草稿保留所选笔记，发送预算失败不丢失选择；已删除的选择可手动移除。
- 最多附加 8 篇笔记。快照作为明确标记的不可信文本资料进入模型上下文；纯文字模型也可以在预算内理解快照，但只有工具能力验证通过的配置才开放 `read`、`note_search`。快照完整正文、工具 schema、参数、结果和原始 parts 均计入请求预算，当前执行不截断。
- `read` 显式区分发送快照与当前版本，默认每页最多 6000 个 UTF-16 字符，返回版本和续读位置，不拆开代理对。`note_search` 先过滤当前权限、范围和回收站状态，再匹配标题/正文并分页；每页最多 20 条、查询最多 200 字符。结果不提供全库命中数，`has_more` 的来源也受权限检查。每次响应最多 8 个工具调用，读取工具参数最多 4096 字符；未知参数或工具不执行笔记操作。
- 一级临时权限只能读取当前片段内、当前权限 revision 下主动发送的有效快照。排队消息在派发前不扩大当前运行范围。二级只能读取范围内笔记；权限等级与范围独立，支持指定笔记本多选。设置变化会中断当前请求，后续读取和每次请求重新投影权限。附加来源失效时保留事实记录并中断，提示重新授权或附加。
- 模型回复和工具结果带笔记来源。无权使用的历史问答整轮排除；当前工具调用与结果成对排除，保留协议完整性。用户补充与工具结果按完整调用边界组装，不把新输入插到尚未配对的调用中间。进入回收站立即阻止向模型提供快照和派生内容；永久删除由外键清理快照，并回收其独立媒体引用。
- 越界读取进入持久化的等待授权状态，队列不向前执行。用户可查看请求、拒绝、仅本次运行允许或保存为全局权限；模型不持有升级权限入口。运行授权只保存用户所选范围内的具体笔记 ID，并绑定权限 revision。继续时已提交调用复用事实结果，但返还前仍重新检查来源；同一调用 ID 不得复用为不同操作。清空聊天一并清理工具载荷，审阅事实源独立保留。
- P11/P19 已接入附加笔记选择、权限等级、范围及笔记本多选；P12 可查看工具名称、调用运行、参数、权限 revision、状态、结果和起止时间。待补齐 P12 的历史权限/范围决策说明及失败重试入口的界面验收。

2026-09-24 最终验证：Android 16 模拟器上 `AgentNoteStoreTest` 10 项、`AgentTimelineTest` 20 项、`AgentFoundationTest` 2 项、`AgentFlowTest` 2 项，共 34 项全部通过。覆盖快照版本与复用、撤权/片段关闭/回收站/永久删除、先授权再分页、结果去重与撤权后缓存失效、一次运行授权、参数校验和分片拼接、派生来源隔离、附件引用、授权等待后重建运行对象、拒绝回传、原文修改后仍读取旧快照、队列快照隔离和整批快照回滚。已检查 720×1280 手机快照预览和输入页截图，快照版本、原正文及关闭操作显示完整。

单元验证：136 项中 135 项通过，真实服务项未显式提供凭据而跳过。`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest` 通过，Lint 0 错误、18 条现有警告。一次构建因宿主机原生内存不足退出；已改用单 worker、较小临时 JVM 堆并分开运行构建与模拟器，未改动项目默认构建配置。受控工具测试不代表三家真实服务联调；实际服务全覆盖仍见 S11.12。

剩余范围：P12 补充验收、实际进程终止后的工具恢复，以及 S11.6–S11.7 的变更事务、单篇 Diff、写工具和编辑器联动。整体 S11.4–S11.7 尚未完成。
