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
- 连接测试使用一次文字流式请求并提示可能产生费用，仅记录模型可用性。工具按权限默认启用，连接状态不影响工具清单。模型、厂商适配、服务、密钥或预算配置变化重置连接状态。
- OkHttp 5.5.0 处理 HTTPS、取消与超时；不自动重试或跟随重定向。连接/写入 15 秒、读取 90 秒、单请求总时长 180 秒；SSE 单事件最多 1 Mi 字符、总响应最多 4 Mi 字符。网络、认证、配额、服务、协议、超时分别给出固定提示，不展示服务错误正文。
- 两份目录请求均不携带用户凭据，各 15 秒总超时、响应上限 8 MiB，不自动重试或跟随重定向；仅选择支持文字输入与输出的模型，不自动证明工具能力。预设上下文采用目录值（不超过本地 200 万上限），输出取 4096、目录输出上限及上下文预留后的较小值；用户可调整。
- 自定义 Profile 默认上下文 32768 Token、输出 4096 Token，要求用户依据服务能力填写；它们是预算输入，不能作为已验证的模型容量。测试输出最多 1024 Token；工具预算预留 1024 Token。用量取服务返回值，不显示推测费用。

验证：`ModelProtocolTest` 在三种协议上覆盖文字、取消、HTTP 认证失败、工具参数分片与结果序列化、结束标记、SSE 边界和秘密隔离；`ModelProfileStoreTest` 验证 Keystore 密文、删除及配置锁；`ModelSettingsFlowTest` 验证新增、编辑、能力状态与删除。2026-09-22 上述测试通过。通过本地 `XNOTE_LIVE_CONFIG` 指向用户的 `apikey.env`，OpenAI 兼容服务上的 `gpt-5.6-terra` 已通过文字流式、工具调用及工具结果回传三步真实请求；密钥不写进代码或测试输出。Anthropic、Gemini 当前为受控协议测试，尚无对应真实服务凭据；三家真实服务全覆盖仍由 S11.12 验收。

目录规则：依据远端 SDK 类型映射已实现的 OpenAI 兼容、Anthropic Messages 和 Gemini 协议。主目录地址优先，补充目录仅填缺失地址；根域名形式的 Anthropic/Gemini 地址补齐对应版本路径。缺失地址、不安全 URL、未知适配器、模型级接口覆盖、已弃用或不支持文字的条目不展示。当前不提供仅支持 Responses 的 OpenAI 专用模型。远端刷新不会改写已保存配置，离线编辑沿用其厂商名称、地址、协议与模型。主流排序为 OpenAI、Anthropic、Google、DeepSeek、智谱/Z.AI、Kimi、MiniMax、Qwen、xAI。目录数据不是可用性证明；用户可通过测试连接检查服务状态，工具按权限默认启用。

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

## S11.4 运行控制、队列与后台恢复

实现入口：`data/agent/AgentTimeline.kt`、`AgentRunService.kt`、`domain/agent/AgentRunLimits.kt`、`feature/agent/AgentScreen.kt`。

- 运行中发送保存为当前运行的待补充消息，在完整响应边界消费。结束判定与补充提交共用互斥锁；原始目标、此前部分回复及补充完整参与后续请求预算。已完成历史可以压缩，当前执行不能静默截断。
- 队列消息落库，支持编辑、删除及上下排序，绑定模型 ID/版本。取出消息、创建运行及标记已派发处于同一事务；成功后顺序派发下一项，停止、失败、中断或容量暂停时暂停队列。冷启动不自动派发，用户选择继续。队列存在时设置页继续锁定所绑定配置。
- 继续原运行保留部分回复并追加执行事件，不创建新的目标或切换模型。继续前检查工具提交记录，结果未知或仍在执行的工具阻止继续。当前工具审计骨架记录未经开放的调用并拒绝执行；真实工具及本地提交核查随 S11.5–S11.7 接入。
- 运行默认最多 16 次模型请求（计入重试，跨继续累计）、每次执行窗口最多 10 分钟、队列最多 50 条。仅对尚未产生正文的网络、服务和超时错误重试两次，间隔 1 秒、2 秒；认证、配额、非法请求及已产生正文的错误不自动重试。参数集中在 `AgentRunLimits`。
- `dataSync` 前台服务承载模型网络请求，使用有限时长的部分唤醒锁；离页、后台与熄屏可继续。通知提供停止入口。通知未开启时页面说明可见性限制并提供授权入口；应用内停止始终可用。服务启动失败、系统超时或服务销毁进入可恢复中断，使用 `START_NOT_STICKY` 避免系统自动重放请求。取消清除运行临时授权。
- 原生服务回调在请求协程启动前触发取消时，也必须进入持久化取消处理，避免数据库残留“运行中”。协程以 `ATOMIC` 启动，先完成前台服务启动请求，再允许模型请求；取消后的本地状态提交使用 `NonCancellable`。
- 消息支持选择复制与消息选项中的单条删除。删除单条消息保留其他时间线条目，但使整轮失去后续上下文资格，防止相关回复重传被删除输入。独立笔记改动通过 Header 左侧入口打开公共底部 Drawer，支持列表和单篇详情切换。

当前验证：134 项单元测试中 133 项通过，真实服务测试因未显式提供凭据跳过；`testDebugUnitTest`、`lintDebug`、Debug APK 构建和安装通过。Lint 0 错误、18 条现有依赖/公共控件/资源警告。运行层端侧测试覆盖补充及队列恰好一次派发、编辑排序、停止暂停与配置锁、有限重试、清空取消、继续、磁盘库重开、删除历史安全投影、累计循环限制、服务启动拒绝和启动前取消。界面测试覆盖发送、页面往返、新话题；已检查 720×1280 手机截图。

2026-09-24：运行层 16 项、界面 1 项、通知停止 1 项共 18 项端侧专项全部通过；通知权限拒绝用例在预先撤权后独立执行并通过（合计 19 项）。

原生后台测试使用无真实凭据的保留测试地址挂起有界请求，验证系统前台服务、桌面/熄屏及通知停止；它不是模型服务联调证据。通知拒绝用例必须在启动 instrumentation 前撤销权限，避免测试进程自身被权限撤销终止：

```text
adb shell pm revoke com.xnote.app android.permission.POST_NOTIFICATIONS
adb shell am instrument -w -e class com.xnote.app.data.AgentBackgroundTest#deniedNotificationsKeepForegroundExecutionAndInAppStopAvailable com.xnote.app.test/androidx.test.runner.AndroidJUnitRunner
```

2026-09-24 系统补充验收：`AgentProcessRecoveryTest` 在磁盘测试库中保存一次已提交读取、部分流式回复和排队任务后，由宿主机对已核对 PID 的应用进程发送 SIGKILL。独立进程重新打开数据库，验证任务为 `process_interrupted`、队列暂停且没有自动模型请求；用户继续后沿用原工具结果，保留部分回复，仅发送一次后续请求，已提交工具事件保持不变。该用例使用受控模型，不依赖真实服务。

`AgentBackgroundTest#systemDataSyncTimeoutPersistsRecoverableInterruption` 在 Android 16 上通过：依据官方测试接口把 `data_sync_fgs_timeout_duration` 暂设为 3000 毫秒，启动真实前台服务后回桌面，由系统触发 `onTimeout`；运行持久化为 `foreground_service_timeout`，保留原输入并停止服务。测试后已删除临时 device_config 值、重置兼容性开关。该用例需显式传入 `systemTimeout=true`，普通回归跳过。

进程用例需依次单独调用 `prepareAndWaitForKill`（`processPhase=prepare`）和 `recoverAndContinueWithoutReplayingCommittedTools`（`processPhase=recover`）。准备阶段写入私有 `files/agent-process-recovery-ready`，内容为 `<PID>:ready`；宿主核对 `pidof com.xnote.app` 后执行 `run-as com.xnote.app kill -9 <PID>`。准备阶段 instrumentation 报进程终止是预期结果；恢复阶段 1 项断言测试通过才算验收成功。测试使用独立 `agent-process-recovery-test.db`，恢复验收结束后清理该测试库和临时凭据。

2026-09-28 后台启动限制端到端验证通过：Debug 专用 `AgentBackgroundRestrictionService` 在普通服务中创建独立测试数据库，退出 Activity 后等待 35 秒，不使用 instrumentation。Android 16 实际拒绝 `AgentRunService.start`，抛出 `ForegroundServiceStartNotAllowedException`；进程重要性为 300，系统日志记录 `mAllowStartForeground false`。原输入持久化，运行进入 `Interrupted/background_unavailable`，页面状态提示回到前台继续，模型请求数为 0。重新打开 Activity 并显式继续后，同一运行完成，模型请求数为 1，原输入没有重复插入。测试数据库清理完成。该用例验证平台限制与运行恢复，使用本地受控模型，不作为真实模型服务联调证据。

复现时先安装 Debug APK，执行以下命令；每次运行应等到 `passed` 后再重新开始。`armed` 后立即回桌面，至少等 35 秒再检查 `restricted`，随后停止辅助服务以验证重建，回前台继续并检查 `passed` 及 `recreatedForContinuation=true`：

```text
adb shell am start -W -n com.xnote.app/.MainActivity
adb shell am startservice -n com.xnote.app/.debug.AgentBackgroundRestrictionService
adb shell run-as com.xnote.app cat files/agent-background-restriction-result.json
adb shell input keyevent 3
adb shell run-as com.xnote.app cat files/agent-background-restriction-result.json
adb shell am stopservice -n com.xnote.app/.debug.AgentBackgroundRestrictionService
adb shell am start -W -n com.xnote.app/.MainActivity
adb shell am startservice -n com.xnote.app/.debug.AgentBackgroundRestrictionService -a com.xnote.app.debug.CONTINUE_BACKGROUND_TEST
adb shell run-as com.xnote.app cat files/agent-background-restriction-result.json
```

验证入口仅存在于 `src/debug`，导出的服务要求 shell 持有的 `android.permission.DUMP`；`processReleaseMainManifest` 通过，Release 合并清单不包含该服务。本机证据为 `app/build/s11-4-background-restricted.json`、`s11-4-background-continued.json` 和 `s11-4-background-platform.log`。同期 `lintDebug`、Debug/测试 APK 构建、单元测试通过：154 项中 153 项通过、1 项真实服务用例默认跳过。

读取和写入提交后的实际进程终止、重启继续、授权与写入冲突等待后的运行对象重建已验证；创建/删除的恢复补充证据见下文。S11.4 平台专项及 S11.4–S11.7 最终逐项验收均已完成。

平台依据：[后台启动限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)、[前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[服务超时](https://developer.android.com/develop/background-work/services/fgs/timeout)、[通知权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)、[CoroutineStart.ATOMIC](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-start/-a-t-o-m-i-c/)。Android 与协程 API 均通过 Context7 核查。

## S11.5 发送快照、范围与只读工具

实现入口：`AgentNoteStore.kt`、`AgentConversationContext.kt`、`AgentNoteTools.kt`、`AgentNoteDialogs.kt`，以及时间线的工具循环。

- Room 7 在消息中新增可恢复的模型交互 JSON、工具事件中新增笔记来源、草稿中新增所选笔记 ID；6→7 自动迁移保留已有记录。原有 2→3→4→5→6 迁移链继续衔接。模型原始函数调用与 Gemini 原始 parts/签名独立保存，可在用户继续时恢复完整工具调用与结果配对。
- 附加笔记在发送或加入队列时原子保存标题、结构化正文、背景与归属快照，并绑定稳定版本和消息。相同版本复用快照；媒体仅增加独立引用，不复制文件。正文后来变更不会替换旧快照，卡片提供只读预览。草稿保留所选笔记，发送预算失败不丢失选择；已删除的选择可手动移除。
- 最多附加 8 篇笔记。模型初始仅接收 ID、快照 ID、版本、标题、更新时间和可选选区定位，正文保存在本地并由 `read` 按需读取。工具按当前权限提供；元信息、工具 schema、实际读取结果和原始 parts 均计入请求预算，当前执行不截断。
- `read` 显式区分发送快照与当前版本，默认每页最多 6000 个 UTF-16 字符，返回版本和续读位置，不拆开代理对。`note_search` 先过滤当前权限、范围和回收站状态，再匹配标题/正文并分页；每页最多 20 条、查询最多 200 字符。结果不提供全库命中数，`has_more` 的来源也受权限检查。每次响应最多 8 个工具调用，读取工具参数最多 4096 字符；未知参数或工具不执行笔记操作。
- 一级临时权限只能读取当前片段内、当前权限 revision 下主动发送的有效快照。排队消息在派发前不扩大当前运行范围。二级只能读取范围内笔记；权限等级与范围独立，支持指定笔记本多选。设置变化会中断当前请求，后续读取和每次请求重新投影权限。附加来源失效时保留事实记录并中断，提示重新授权或附加。
- 模型回复和工具结果带笔记来源。无权使用的历史问答整轮排除；当前工具调用与结果成对排除，保留协议完整性。用户补充与工具结果按完整调用边界组装，不把新输入插到尚未配对的调用中间。进入回收站立即阻止向模型提供快照和派生内容；永久删除由外键清理快照，并回收其独立媒体引用。
- 越界读取进入持久化的等待授权状态，队列不向前执行。用户可查看请求、拒绝、仅本次运行允许或保存为全局权限；模型不持有升级权限入口。运行授权只保存用户所选范围内的具体笔记 ID，并绑定权限 revision。继续时已提交调用复用事实结果，但返还前仍重新检查来源；同一调用 ID 不得复用为不同操作。清空聊天一并清理工具载荷，审阅事实源独立保留。
- P11/P19 已接入附加笔记选择、权限等级、范围及笔记本多选；P12 可查看工具名称、调用运行、参数、权限 revision、状态、结果和起止时间。Room 8 通过 7→8 迁移新增授权判定历史，保留请求等待、用户拒绝和获准执行时各自的全局权限、范围、有效运行授权与判定依据；后续设置变化不改写历史。旧调用没有判定记录时明确说明。非法参数标记失败，越权与未开放工具标记未获允许。失败或停止的所属任务可从工具详情继续，沿用已提交结果并重新检查当前权限；该入口不重放已完成工具。

2026-09-24 最终验证：Android 16 模拟器上 `AgentNoteStoreTest` 10 项、`AgentTimelineTest` 20 项、`AgentFoundationTest` 2 项、`AgentFlowTest` 2 项，共 34 项全部通过。覆盖快照版本与复用、撤权/片段关闭/回收站/永久删除、先授权再分页、结果去重与撤权后缓存失效、一次运行授权、参数校验和分片拼接、派生来源隔离、附件引用、授权等待后重建运行对象、拒绝回传、原文修改后仍读取旧快照、队列快照隔离和整批快照回滚。已检查 720×1280 手机快照预览和输入页截图，快照版本、原正文及关闭操作显示完整。

单元验证：136 项中 135 项通过，真实服务项未显式提供凭据而跳过。`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest` 通过，Lint 0 错误、18 条现有警告。一次构建因宿主机原生内存不足退出；已改用单 worker、较小临时 JVM 堆并分开运行构建与模拟器，未改动项目默认构建配置。受控工具测试不代表三家真实服务联调；实际服务全覆盖仍见 S11.12。

P12 补充回归：上述端侧专项增至 35 项并全部通过。新增界面用例覆盖等待授权、仅本次运行允许、历史授权依据展示、后续模型失败及工具详情内继续；继续后全局权限仍为一级，原调用只有一条已提交记录。实际进程终止后工具恢复另行通过，过程见 S11.4。

最终界面回归 3 项通过，并检查了工具详情截图。Agent 弹层打开期间暂停展示外层标题和导航，防止覆盖弹层或接收其上方点击，关闭后恢复；验收断言同时覆盖这些状态。发送用例明确等待助手完成并滚动到完成状态。最终 Debug/测试 APK 构建和 Lint 通过，Lint 0 错误、18 条现有警告。S11.5 已完成。

S11.6–S11.7 的变更事务、单篇 Diff、写工具和编辑器联动验收见下文。

## S11.6 编辑合并与单篇审阅

实现入口：`AgentContentMerge.kt`、`AgentReviewStore.kt`、`AgentReviewScreen.kt`，以及 `NoteLibrary` 的用户来源和永久删除清理。

- 编辑事务重新检查运行与当前三级可写范围，校验结构、媒体和选区，比较读取基线、最新正文和拟应用内容。标题、段落属性、文字与格式、不同表格单元格和块顺序分别合并；不重叠的用户改动保留。过期选区要求重新选择。没有内容差异时不制造待审阅记录。
- 文字按 Unicode 码点及其格式比较，不拆开代理对。差异使用有界 Myers 路径，追踪数组最多 262144 个整数（约 1 MiB，不含输入与输出）；并行差异过大时返回冲突。表格单元格没有稳定 ID，因此双方同时改变表格结构时不猜测对应关系；须基于最新版本重新调整。媒体内容及相对顺序保持受保护。
- 正文、派生统计、搜索索引、改动前后完整版本、Agent 来源和单篇审阅状态处于同一 Room 写事务。同篇未审阅改动跨运行累计；已提交的运行/调用复用改动记录。用户保存的实际内容变化在有效审阅或撤回窗口内另存 User 来源，不混入 Agent 的审阅批次。事实按插入顺序处理，系统时钟回拨不会倒置回退顺序或选错最近接受批次。该领域层基于既有改动表；当前版本 9 的读取引用扩展见下文。
- 接受只确认现有内容，不首次保存或再次改写正文。拒绝先在内存逆序合并整批 Agent 改动，全部成功后才提交一次正文；任一处冲突只更新审阅状态，整篇正文与索引不部分回退。回退保存时沿用最新背景、归属等元数据。最近接受的批次在 30 天内可撤回，并执行同样的完整冲突检查；已撤回批次标记 `Undone`，不会接着撤回更早的接受。
- 待审阅和有效撤回记录独立引用媒体，不复制文件。拒绝、撤回及过期接受解除相应引用，笔记和其他有效引用仍保护文件。永久删除清理该篇改动载荷与引用，审阅卡片保留“无法恢复”状态；回收站的既有清理期限未延长。
- P04 通过 Agent 页“笔记改动”进入，与聊天记录独立。显示当前笔记背景、累计来源与版本、红色删除/绿色新增、格式与表格差异；每篇提供全部接受、全部拒绝和最近接受的撤回。冲突显示当时的改动记录，可保留当前内容或准备重新调整。重新调整保留已有草稿并附加该篇笔记，等待用户发送，不自行扩大权限。

验证：`AgentContentMergeTest` 7 项覆盖多处非重叠改动、Unicode、格式、表格、用户媒体变动、长文本及 1000 组随机差异重建。全体 143 项单元测试中 142 项通过、1 项真实服务测试默认跳过。Android 16 上审阅事务、审阅界面、既有 Agent 和笔记库合计 64 项专项通过；新增时钟回拨边界后，最终 14 项审阅事务及界面专项全部通过。Debug/测试 APK 和 Lint 构建通过，Lint 0 错误、18 条现有警告。

已验证数据库重开后待审阅状态与正文一致、跨轮累计、局部冲突阻止整批回退、接受不重复保存、30 天边界、用户编辑保留、调用去重、权限撤回后仍可本地审阅，以及清空聊天后独立审阅。已检查 720×1280 手机累计 Diff 与冲突页截图。

S11.6 的领域事务、编辑 Diff 与单篇审阅已完成。生产 `create`/`write`/`delete` 工具、创建与删除审阅及编辑器联动验收见下文。

事务参考：[Room 数据访问与写事务](https://developer.android.com/training/data-storage/room/accessing-data)，通过 Context7 核查连接写事务与 Flow 查询；外层异常引发正文和事实记录共同回滚由端侧用例验证。

## S11.7 版本化写工具与冲突恢复

- 模型按当前权限默认获得 `read`、`note_search`、`create`、`write` 和 `delete`，连接测试状态不参与工具授权。`write` 只接受笔记 ID、读取基线版本、完整标题、结构化正文 JSON 及可选选区；三级权限与当前可写范围在事务内重新检查，一级和二级进入等待授权，不写入正文。创建和删除规则见下节。
- `read` 将完整读取基线及其媒体引用与工具结果一同提交，并返回可用于后续分页的 `snapshot_id`；分页传回该 ID 可固定版本。Room 9 新增独立的工具读取引用，8→9 自动迁移衔接原迁移链。读取引用不加入主动附加集合，不授予笔记、笔记本或下一话题的权限。清空工具记录、清空聊天和永久删除仍可清理不再使用的基线与附件引用。
- `write.base_version` 必须对应当前话题已提交的读取基线或已派发消息的发送快照，搜索结果中的版本本身不足以写入。正文与基线由应用读取，不接收模型提交的伪造旧正文。拟修改版本与当前版本通过三方合并保存，沿用最新背景与归属；正文、搜索索引、审阅、来源和工具结果共用事务。调用 ID/工具名/参数绑定，重复调用返回已提交结果，不重复写入；返回历史结果仍受当前来源权限控制。
- 全部写入参数上限为 262144 个 UTF-16 字符，读取/搜索参数仍为 4096；这是独立防护上限，实际模型上下文与输出预算继续生效。未知字段、非法文档、媒体删除/重排、越界选区均不写入。选区校验保护范围外文字及格式、其他块、标题和段落属性，并拒绝拆开 Unicode 代理对；选区实际编辑入口仍待接入。
- 写入冲突持久化为 `WaitingConflict`，保留正文并暂停队列；重建运行对象不自动发送。Agent 页提供“重新读取并调整”及“保留内容并结束任务”。用户确认重新调整后旧调用固定为失败结果，模型收到要求重新读取的冲突反馈，旧写入不再重试；新版本下的后续写入进入独立单篇审阅。
- `AgentProcessRecoveryTest` 的显式两阶段用例已扩展为读取提交、写入提交、部分回复和队列保存后由宿主机杀进程，再在新进程验证中断、用户继续、调用与改动不重复、独立审阅和保留用户补充的拒绝操作。

最终验证：144 项单元测试中 143 项通过、1 项真实服务用例默认跳过。Android 16 上 `AgentWriteToolTest` 9 项、`AgentTimelineTest` 21 项、`AgentNoteStoreTest` 10 项、`AgentFoundationTest` 3 项、`AgentReviewStoreTest` 11 项、`NoteLibraryInstrumentedTest` 16 项以及写入/审阅/Agent 界面 7 项，合计 77 项全部通过。覆盖数据库重开后的基线、写入去重、权限与媒体保护、整笔事务回滚、当前话题隔离、固定版本分页、冲突后的运行对象重建、队列暂停及 8→9/3→9 迁移。已检查 720×1280 手机写入冲突页与实际写工具生成的累计 Diff；拒绝只移除 Agent 新增内容，用户改写保留。`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest` 最终通过，Lint 0 错误、18 条现有警告。

实际进程恢复复验通过：显式 `processPhase=prepare` 在读取和写入均提交、部分回复及队列保存后留下就绪标记；宿主机核对运行 PID 为 5936 后发送 SIGKILL，准备端如预期报告进程终止。`processPhase=recover` 在另一个 PID 中通过全部断言，且 1 项恢复测试通过：没有自动模型请求，运行显示中断，原读取与写入事件及单条改动保持不变，用户继续仅发送一次后续请求，队列保持暂停；最后拒绝 Agent 改动仍保留重启后用户补充。测试使用专用数据库及测试凭据，完成后清理。该证据验证本地恢复，不代表真实模型服务写工具联调。

编辑器携带笔记、全文／选区润色及最终逐项验收见下文；S11.4–S11.7 已完成。

迁移参考：[Room 数据库迁移](https://developer.android.com/training/data-storage/room/migrating-db-versions)，通过 Context7 核查导出 schema、自动迁移和迁移验证；当前 schema 为 `app/schemas/com.xnote.app.data.db.XNoteDatabase/10.json`。

## S11.7 创建、删除与单篇审阅补充

- `create` 在同一事务提交正文、索引、审阅、工具结果及运行内新笔记权限。明确且可写的目标优先，唯一可写目标自动采用；多个目标或没有创建权限时暂停，由用户选择归属并授权当前调用。该授权绑定当前权限 revision，不授权下一次创建，也不扩大整个笔记本的权限。新笔记 ID 单独记录，原先只读的其他笔记不会被升级为可编辑。
- `delete` 只移入回收站，要求当前可写权限及当前话题内可信读取版本；版本已变化则等待重新读取。正文和媒体继续保留，工具不提供永久删除能力。继续模型请求时移除已删除笔记的正文、快照、派生回复和原始调用上下文，只保留应用生成的执行回执。其他运行不借该回执取得已删除笔记的内容。
- 创建、编辑、删除沿用同篇累计审阅。拒绝新建遇到用户内容、背景或归属变化时整篇暂停；拒绝删除保留用户自行恢复的当前版本，原笔记本消失时恢复到未归档。逆序规划全部改动后才提交，任一编辑冲突均阻止部分恢复。新建后删除的同批拒绝不会延长已有回收站期限；永久删除仍清理事实载荷和引用。
- 时间线创建/删除卡片可直达该篇审阅。新建显示完整新增 Diff，删除显示回收站与恢复说明。回收站内冲突笔记提供明确的“恢复笔记并重新调整”入口，用户点击后恢复并准备草稿，等待发送。
- Gemini 工具声明采用完整 JSON Schema 的 `parametersJsonSchema`，保留创建目标的可空笔记本 ID 与未知字段限制；协议单元测试覆盖该请求结构。依据：[Gemini FunctionDeclaration](https://ai.google.dev/api/generate-content)，已通过 Context7 核查。这不是 Gemini 真实服务联调证据。

2026-09-28 验证：`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest` 全部通过；145 项单元测试中 144 项通过，1 项真实服务测试默认跳过。Lint 0 错误、19 条依赖版本、公共控件和资源警告。Android 16 上创建/删除工具 12 项、时间线 23 项、审阅事务 11 项、写工具 9 项、读取工具 10 项、基础与迁移 3 项、笔记库 16 项及界面 10 项，合计 94 项全部通过。已检查 720×1280 手机创建归属、创建 Diff、删除入口和删除审阅截图。页面测试通过窗口 IME 控制器收起键盘，避免返回键在键盘已关闭时退出测试 Activity。

实际进程恢复补充通过：磁盘测试库中依次提交读取、写入、创建、读取新笔记和删除新笔记，保存部分回复与队列；宿主核对 PID 8825 与就绪标记一致后发送 SIGKILL。独立进程恢复测试 1 项通过：任务中断且没有自动请求，队列暂停，用户继续仅发出一次后续请求，5 条工具记录、两篇笔记及各自改动记录均不重复。拒绝既有笔记改动保留用户补充；拒绝新建并删除的批次保持回收站状态。专用测试库与凭据在恢复阶段清理，准备阶段的进程终止报告符合预期。

编辑器携带笔记、全文／选区润色及最终逐项验收见下文；S11.4–S11.7 已完成。

## S11.7 编辑器并行保存补充

- 编辑器正文保存必须传入其读取基线，在同一数据库事务比较基线、最新正文与用户输入。不重叠改动合并；重叠文字、格式或属性保留用户输入。并行表格改动涉及结构、无法确定单元格对应关系时保留用户表格；超出有界差异预算时保留该字段的用户版本。正文、派生统计、搜索索引和 User 来源一同提交。已移入回收站的笔记可保存尚未落盘的用户输入，不自行恢复笔记。
- 打开的编辑器收到笔记变化后重新读取最新正文，合并尚未保存的输入，并同步光标、选区和撤销/重做历史。Agent 的独立改动不会被下一次旧正文保存或用户撤销操作整体覆盖。段落重排、删除与新增相遇时保留用户编辑和新增段落；光标偏移按 Unicode 码点转换，不拆开代理对。未改变内容或选区的输入法回执不会重置显式选择的输入样式，远端刷新也保留该样式。
- 加载、刷新和保存共用会话互斥锁。一次提交开始后完成基线回写，再响应后续输入对延迟保存任务的取消；数据库等待期间的新输入继续留在编辑器并由下一次保存提交。输入法组合态暂缓后台正文刷新和延迟保存，离页/后台的显式 flush 仍保存所有可见文字。
- 删除无基线的正文保存接口，编辑器统一使用带基线的 `saveNoteContent`；测试的直接内容准备使用已有完整笔记保存 API。未新增兼容入口或数据库字段。

协程依据：[Mutex.withLock](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/with-lock.html)、[withContext](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html)，已通过 Context7 核查取消和互斥语义。


2026-09-28 最终验证：`lintDebug`、`testDebugUnitTest`、Debug 和测试 APK 构建全部通过。154 项单元测试中 153 项通过，1 项真实服务测试默认跳过；Lint 0 错误、19 条现有依赖/公共控件/资源警告。新增单元用例覆盖冲突时用户优先、段落删除与重排、表格、格式和 Unicode 光标，含 500 组随机段落合并。

Android 16 最终专项 111 项全部通过：原有 94 项 Agent 工具、时间线、审阅与笔记库回归，加上 8 项编辑器并行保存、1 项打开编辑器的实时刷新与继续输入、3 项图片持久化和 5 项既有编辑页流程。覆盖同处冲突、独立改动合并、拒绝后保留用户编辑、数据库等待期间继续输入、输入法组合态强制保存、删除后保存未落盘内容、格式回执和撤销/重做、表格及 Markdown。该轮验证使用数据库版本 9；当前版本 10 的迁移与最终回归见下文。

编辑器独立“与 Agent 对话”按钮、携带笔记卡片与全文／选区润色入口已完成，逐项核对及最终验证见下文。


## Agent 页面交互布局（2026-09-28）

- Header 无标题，左侧全部笔记改动，右侧新话题与更多；使用现有 Keyline 图标。外层导航 Header 在 Agent 页停用，避免覆盖页面控件。
- 输入容器左下角依次为附件加号、权限范围摘要，右下角发送。附件菜单复用 `XNoteDropdownMenu`，当前只包含笔记；选中笔记支持正文预览和移除。空输入禁用发送，运行时明确提示补充当前任务。
- 全部笔记改动及单篇详情复用 `XNoteDrawer` 底部弹层，支持返回列表、关闭和系统返回；弹层覆盖期间外层导航不接收点击。关闭后保留草稿和聊天位置，重新调整会把说明同步回输入框。
- 更多菜单集中任务队列、模型配置和后台通知；队列支持加入当前输入、编辑、排序、删除及恢复。执行控制在输入区上方，授权与冲突在对话内。消息用量和删除通过消息选项查看。
- 验证：154 项 JVM 单元测试通过；11 项 Android 交互回归通过，覆盖发送、新话题、发送快照、权限、工具续执行、写入冲突、创建与删除审阅、接受/拒绝/撤回。另补验队列加入、编辑、删除及审阅返回后的草稿同步。
- 本机界面截图保存于 `captures/agent-ui/`，覆盖空白页、附件菜单、键盘输入、队列弹层、笔记改动弹层与对话。截图为 Android 16 模拟器结果，尚未覆盖真机及平板视觉验收。


## S11.7 携带笔记与全文／选区润色

- 手机和平板编辑页提供独立“与 Agent 对话”按钮；更多菜单提供“润色全文”和“润色所选文字”。离页前 flush 当前编辑内容，保存失败或仍有未落盘输入时留在编辑器并提示。进入对话只准备草稿，不自动发送；原输入和已有附件保留，同篇不重复附加。
- 附加卡片展示标题、摘要、所属笔记本和修改时间，可预览、移除。发送后展示固定版本的卡片，保存原笔记修改时间；历史快照未记录该时间时不推测。上下文明确标记“用户显式提供的笔记内容”。
- 选区携带内容版本、块 ID、UTF-16 文字范围，表格选区额外携带行列。草稿、排队消息和实际运行的原始用户消息保存应用生成的定位；发送事务再次校验版本、非空范围及 Unicode 边界。原文改变时要求重新选择，失败保留草稿。
- 写工具从原始用户消息读取约束，模型省略 selection 也不能绕过；模型伪造范围、修改其他笔记、创建或删除均不执行。选区外的文字、格式、标题、段落属性、其他块和表格单元格／结构保持不变。成功后重复调用复用已提交结果；再次改写须由用户重新选择当前版本。选区任务为独立运行，运行中可加入队列，不作为当前任务补充；普通文字补充继续遵守原任务的选区。
- 全文与选区润色均走既有版本化 write、改动记录及单篇审阅事务，不另建正文保存或回退路径。用户需要的操作权限仍由现有授权流程确认，携带笔记不会提升编辑权限。
- 单篇 Diff 展示段落位置，普通文字按词比较，等宽文字按完整行比较；删除为红色删除线，新增为绿色。工具详情在所属任务运行时可直接停止，已提交改动仍保留并可审阅。
- Room 9→10 自动迁移新增草稿 selectionJson 和快照 noteUpdatedAtEpochMs；现有草稿、附件选择及快照保留。旧快照的未知修改时间默认 0，草稿选区默认 null。导出 schema 位于 `app/schemas/com.xnote.app.data.db.XNoteDatabase/10.json`。

迁移 API 已经 Context7 核查；日期读取使用可观察的系统配置，依据 [Android 配置变化](https://developer.android.com/guide/topics/resources/runtime-changes) 响应语言设置变化。


## S11.4–S11.7 逐项验收对照

| 要求 | 实现与核对证据 |
| --- | --- |
| S11.4 / D02：串行运行、固定模型、补充与队列恰好一次 | `AgentTimeline` 在互斥锁与事务内提交补充、排队和派发；`AgentTimelineTest` 覆盖补充安全边界、编辑排序、停止暂停、配置锁、授权及冲突等待后重建。 |
| S11.4 / D08：有限重试、停止、继续、未知提交不重放 | 16 次累计请求、10 分钟执行窗口、50 条队列；仅可重试且未输出的请求退避两次。时间线测试覆盖认证／配额不重试、部分输出保留、继续不重放及取消前提交；实际进程终止证据见后台恢复节。 |
| S11.4 / D09：离页、锁屏、通知拒绝、后台受限和系统超时 | `AgentBackgroundTest` 验证真实前台服务、锁屏、通知停止、预先拒绝通知及系统 `onTimeout`；Debug shell 验证实际后台启动拒绝，保存中断后由前台显式继续。 |
| S11.4 / D15：历史事实和运行控制 | `AgentFlowTest` 覆盖导航往返、新话题、抽屉、队列及工具详情停止／继续；复制使用文本选择，删除使整轮后续上下文失效，清除聊天先停止并清空队列。单篇审阅独立保留。 |
| S11.5 / P11、P19、D03–D04：权限等级、范围与授权 | `AgentPermissionPolicyTest`、`AgentNoteStoreTest`、`AgentLifecycleToolTest` 覆盖等级与范围独立、一次／始终授权、权限版本撤销、授权不扩大其他笔记以及创建目标归属；UI 覆盖权限设置与授权后继续。 |
| S11.5 / D10：发送快照、读取、检索与删除传播 | 快照按笔记及版本复用、不同版本分别保存；`AgentNoteStoreTest` 覆盖旧快照不替换、先过滤再分页、不泄露无权条目／数量、回收站与永久删除、来源派生历史及独立媒体引用。 |
| S11.5 / P12：工具详情与审计 | 参数、所属运行、权限与范围依据、状态、结果、开始／结束时间落库；`AgentFlowTest` 验证授权依据、继续及运行中停止入口，已提交工具不重做。 |
| S11.6 / P04、D05：累计改动与并行编辑 | `AgentReviewStoreTest`、`AgentContentMergeTest`、`EditorAgentSaveTest` 验证跨运行累计、来源、版本、独立修改合并、同处冲突、光标及撤销历史；正文、FTS、统计、改动与审阅在同一事务提交。 |
| S11.6 / D06：单篇接受、拒绝与撤回 | 接受仅确认已保存改动；整篇拒绝先规划全部逆操作，冲突整次不写入。审阅存储／UI 测试覆盖接受、拒绝、30 天撤回、用户后续编辑、数据库重开及永久删除不可恢复。 |
| S11.6：可读 Diff | `AgentDisplayDiffTest` 验证词级差异、等宽按行比较、Unicode 及空文本；详情显示段落位置，红色删除、绿色新增，手机及模拟平板宽度截图检查不遮挡审阅按钮。 |
| S11.7：创建、写入、删除和恢复 | `AgentLifecycleToolTest`、`AgentWriteToolTest`、两类工具 UI 测试覆盖低权限不写、一次创建归属授权、调用幂等、媒体保护、用户并行编辑、删除恢复、创建／删除整篇审阅及事务回滚；实际进程恢复核对五条已提交工具不重复。 |
| S11.7：编辑器携带、全文／选区润色 | `EditorAgentFlowTest` 覆盖最新输入落盘、保留草稿、卡片、全文／选区发送及审阅拒绝；`AgentEditPolicyTest` 覆盖文字和表格选区边界，写工具测试覆盖省略／伪造范围、其他写工具逃逸和数据库重开，时间线测试覆盖草稿恢复、排队与过期选区保留草稿。 |
| S11.7：数据升级与既有编辑流程 | Room 2→10 迁移链由 `NotebookMigrationTest` 与 `AgentFoundationTest` 检查；既有正文、文件、权限、工具、审阅与草稿保留，新字段有明确默认值。编辑器保存、格式、图片及返回重开另行回归。 |

S11.8–S11.12 的自动记忆、派生摘要、图片／文件输入输出和完整设备矩阵验收仍按原开发顺序推进，不计入本次四个切片。受控模型用例证明本地运行、工具及事务行为；真实服务覆盖范围以 S11.2–S11.3 的独立联调记录为准。

## S11.4–S11.7 最终验证（2026-09-28）

S11.4–S11.7 已完成。与已合入的 Agent 页面布局衔接，保留公共底部 Drawer、附件菜单和现有执行控制布局。

- `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug` 均通过。158 项 JVM 用例中 157 项通过，1 项真实服务用例未配置凭据而默认跳过；Lint 0 错误、19 条既有警告。Debug 辅助服务恢复补充后再次构建与 Lint 通过。
- Android 16 模拟器专项 122 项全部通过：Agent 事实源、权限与快照、运行与队列、创建／写入／删除、审阅、迁移、编辑器并行保存与携带、全文／选区润色、手机及模拟平板宽度流程，以及图片、格式、表格、输入法组合态与返回重开。日志：`app/build/s11-final-agent-tests.log`。
- 原生平台另外验证 4 项：通知停止（含回桌面与熄屏）、启动前已拒绝通知、实际系统 dataSync 超时、实际 SIGKILL 后恢复。进程准备 PID 7891 经核对后终止，恢复阶段通过，五条已提交读取／写入／创建／删除工具记录没有重复执行。日志分别为 `s11-final-background-notification.log`、`s11-final-background-denied.log`、`s11-final-background-timeout.log`、`s11-final-process-recover.log`，均位于 `app/build/`。
- Debug shell 独立复验后台启动限制：进程重要性 300，收到真实 `ForegroundServiceStartNotAllowedException`，运行 `Interrupted/background_unavailable`、模型请求 0。停止并重建辅助服务后，从独立数据库恢复同一运行；前台显式继续得到 `Complete`、模型请求 1、原输入一条，报告 `recreatedForContinuation=true`。测试库已清理；报告为 `app/build/s11-final-background-restricted.json`、`s11-final-background-continued.json`，平台日志为 `s11-final-background-platform.log`。辅助服务仅属于 Debug，受 DUMP 权限保护；测试不访问真实模型。
- 系统超时配置已恢复为未设置，通知权限已恢复。已查看手机全文／选区草稿与审阅截图，以及 900dp 模拟平板宽度的选区流程；卡片、选区提示和审阅按钮可见。截图位于 `app/build/s11-editor-agent-screenshots/` 及 `app/build/tablet-selection-polish-*.png`。S11.12 的手机／模拟平板及 OpenAI 兼容真实服务证据见下文。

S11.8–S11.12 的实现及真实服务覆盖证据见下文。

## S11.8 片段摘要与情景记忆（基础验收）

2026-09-28：新增固定消息范围、SHA-256 来源哈希、模型配置版本和提示词版本的摘要任务，片段关闭与任务创建同事务。新消息按上次最终交互的 30 分钟边界或容量边界切片，未解决运行不切片。启动补扫历史关闭片段，WorkManager 在联网时处理；前台运行或队列优先。任务使用片段主键、事务认领及 5 分钟租约避免重复发布，取消或进程退出后可补扫。

摘要函数不带工具，仅接受严格结构化结果；请求前及落库前重查固定来源和当前权限。工具仅提供名称和最终状态，不复制结果正文。摘要与 FTS5 独立持久化，检索先过滤权限，相关最多 3 项、近期最多 2 项并去重，在当前执行上下文之后分配剩余预算。无摘要时携带前一片段安全原文尾部，每条用户/回复最多 800 字符，显式标记摘要待生成，最终仍受模型总预算约束。删除来源使摘要失效；清理聊天删除摘要及索引。

集中参数见 `AgentMemoryLimits`：摘要输出最多 2048 Token（同时取 Profile 输出上限），每任务最多 3 次尝试，重试间隔至少 1 分钟，滚动 24 小时最多 24 次、20 万保守预留 Token。服务返回用量另存输入/输出 Token，不伪装估算为实际用量。配置失效或源输入超过预算显示任务阻塞状态，不改用另一模型。

验证：`AgentEpisodeTest` 2 项；Android 16 的 `AgentEpisodeStoreTest` 5 项、`AgentTimelineTest` 24 项、`NotebookMigrationTest` 1 项共 30 项通过。覆盖来源删除、过期快照授权、模型变更、重复调度、结构校验失败、有限重试、前台优先、中文 FTS 和 2→11 保留数据迁移。全体单元测试 160 项中 159 项通过、真实服务项默认跳过。`lintDebug` 0 错误、19 条既有依赖/资源/公共控件警告。画像候选与用量管理界面见下方 S11.9 验收；S11.10–S11.12 的验收见下文。

调度依据：[Android 数据层与 WorkManager 官方文档](https://developer.android.com/jetpack/guide/data-layer)，通过 Context7 核查 CoroutineWorker、网络约束和持久调度行为。

## S11.9 画像与自动记忆

Room 12 新增独立画像事实、替换链、遗忘键、自动记忆设置和消息画像来源关联；11→12 保留原始笔记、对话和摘要。片段摘要输出最多 8 条画像候选，候选必须引用本片段用户原话，不能以助手内容、附加笔记或凭据为来源。稳定键限定为用户回复偏好、个人偏好、位置/事实及 Agent 表达偏好，权限和固定身份不进入画像。

协调器按更正、陈述、重复行为、推断的优先级确定新增、替换、忽略或待确认；敏感、位置、个人事实和推断先待确认。替换保留 supersedesId 与旧值有效期。关闭自动记忆时旧画像仍可用；重新开启记录消息序列边界，旧任务不补记关闭期间内容。明确请求通过 `memory_remember` 或片段候选单独处理，仍核对用户原话。删除写入遗忘键，旧任务不能复活；后续明确请求先等待确认。清除画像另记录来源边界，连尚未生成候选的旧任务也不能恢复画像。

“我的”和 Agent 更多菜单均可进入“记忆与画像”，管理自动记忆、查看来源和时间、更正、删除及清除长期画像。后台用量按任务类型展示请求数、服务返回 Token、未返回完整用量次数和保守预留量。画像与引用它的摘要、后续回复传播来源关联；来源删除、更正或遗忘后，不再向模型重传旧值及其派生回复。

2026-09-28 验证：`AgentProfileMemoryTest` 5 项、`AgentEpisodeStoreTest` 8 项、`AgentTimelineTest` 24 项、`NotebookMigrationTest` 1 项、`AgentMemoryFlowTest` 1 项，共 39 项 Android 16 专项全部通过。覆盖关闭/重开时段、在途候选落库检查、明确记忆、敏感确认、优先级与替换链、遗忘复活防护、来源删除、摘要到回复的来源传播、认证失败不重试、提示词版本变化、2→12 迁移及界面交互。全体单元测试 160 项中 159 项通过、真实服务项默认跳过；Lint 0 错误、19 条既有警告。已检查 720×1280 手机画像页面截图，内容、开关和操作无裁切。真实服务验收范围为 OpenAI 兼容服务；Anthropic/Gemini 使用协议级自动化验证，最终证据在 S11.12 记录。


## S11.10 文档记忆与历史工具

笔记派生索引独立保存内容哈希、来源版本、原文、摘要／大纲／关键词和模型／提示词版本。1800 字符以内直接使用正文；长笔记在编辑稳定至少 60 秒后排队摘要，与片段摘要共用每天 24 次／200000 保守 Token 的上限，每版本最多尝试 3 次。网络约束、前台运行优先、固定模型、租约与提交前后的权限／版本校验继续生效。摘要失败仍可搜索当前正文。笔记写入事务立即撤销旧索引，回收站排除，永久删除级联清除；清除索引会阻止旧在途任务重新发布。

`memory_search` 搜索情景摘要及历史文字 FTS，先检查权限和来源再匹配、排序与分页；返回时间、标题、摘要、命中片段和来源。`memory_read` 按消息顺序返回角色、时间、状态及文字位置，游标绑定参数与当前可见来源。每运行搜索最多 6 次、读取最多 8 次；每页最多 12000 保守 Token，累计返回最多 24000，读取最多 6000 字符、20 条消息。中文与转义文字自动缩小页大小，重复请求不会无限回传。来源关联传递至工具缓存、摘要和后续回复；删除历史、遗忘或撤权后重新校验，保留当前删除操作的安全回执。

自动上下文按当前任务预算添加画像、去重的近期／相关情景和最多 3 篇相关笔记。笔记读取始终检查当前版本，附加快照仍保持发送版本。“记忆与画像”支持全聊天文字搜索、展开与复制；“存储与隐私”展示笔记索引数量、内容估算、未完成摘要和独立清除入口。Room 12→13 增加历史 FTS 与来源关联，13→14 增加笔记派生索引；既有笔记、草稿和记忆保留。

2026-09-28 验证：Android 16 上笔记记忆 5 项、历史记忆 5 项、时间线 24 项、记忆管理 UI 1 项、笔记权限 10 项及 2→14 迁移 1 项全部通过（分两轮执行，历史用例重复覆盖）。另有新增来源关联后的片段摘要 8 项通过。全体 JVM 单元测试通过（真实服务用例按默认跳过），Lint 0 错误、19 条既有警告。日志：`build/s11-note-tests.log`、`build/s11-memory-final.log`。覆盖原文回退、分页完整性及中文／emoji／转义预算、重复调用上限、来源删除、撤权、编辑与清除期间的旧结果丢弃、稳定期与请求去重、认证失败不重试、回收站和永久删除。协议参考及迁移事务通过 Context7 核查。


## S11.11 图片与文件消息

输入支持图片、PDF、UTF-8 TXT／Markdown，复制到应用私有目录并独立保存草稿／消息引用，不创建笔记。每条消息最多 4 个文件、合计 12 MiB；单文件最多 8 MiB，图片转换后最多 4 MiB、长边 2048 像素，动画取静态首帧；文本最多 128 KiB／32768 字符，PDF 最多 20 页且提取文本最多 32768 字符。损坏、编码错误、过量与能力不支持均保留已有草稿。取消系统选择不会改变草稿。

三种协议使用各自的图片／PDF 内容块。设置页分别执行图片与 PDF 读取测试，随机校验码不出现在文字提示中，成功后才保存对应能力；目录元数据不冒充验证。未验证 PDF 原生输入时，仅对每页均能提取文字的 PDF 使用文字回退，并标明图片和版式未提供。含扫描页且未通过原生能力验证时拒绝发送。二进制按每图 4096、每 PDF 页 4096 加提取文字的保守 Token 估算计入完整输入预算，不将 Base64 长度伪称为模型 Token；最终用量仍以服务返回为准。

`output_file` 每运行最多生成 4 个 TXT／Markdown 文件，内容最多 32768 字符／128 KiB，拒绝路径及其他扩展名；调用 ID 去重继续沿用工具提交记录。文件卡片支持文字／图片预览、系统保存和分享；PDF 展示文字预览并提示版式限制。P23 单列聊天文件占用。删除消息、队列或清除聊天解除引用并回收无引用文件；另存和分享副本独立保留。Room 14→15 增加文件元数据，复用附件表和引用表。

附件可提取文字与生成文件内容进入片段摘要、历史 FTS 和 `memory_read`，与所属消息共同失效。历史图片仅保留文件标识及已有回复，不凭空生成图片描述；后续文字任务不重发旧二进制附件。系统保存与分享通过真实 Android 内容 URI 验证 UTF-8 字节一致，分享 URI 仅授予读取权。

运行预算不足时，成对压缩已完整结束的工具调用和结果，保留调用标识、工具名、状态、错误及写入回执。当前目标、补充输入和未完成内容保持完整；最小请求仍超预算则暂停。后台摘要因前台任务让路时保留租约并重试，取消 WorkManager 本身仍正常传播取消。

协议与解析依据：[OpenAI 图片](https://developers.openai.com/api/docs/guides/images)、[OpenAI 文件输入](https://developers.openai.com/api/docs/guides/file-inputs)、[Anthropic PDF 内容块](https://github.com/anthropics/anthropic-sdk-python/blob/main/src/anthropic/types/document_block_param.py)、[Gemini GenerateContent](https://ai.google.dev/api/generate-content)、[PdfBox-Android](https://github.com/TomRoush/PdfBox-Android)。PDF 文字提取使用 PdfBox-Android 2.0.27.0，覆盖最低 Android 13；解析缓冲限制为 8 MiB 内存／64 MiB 总临时存储。

## S11.12 全链路验收与收尾

2026-09-28，真实服务使用本地既有 OpenAI Chat Completions 兼容配置（`api.liangrekui.com`、`gpt-5.6-terra`），验收 Profile 明确设置上下文 65536、输出 8192 Token；摘要仍按产品上限 2048 Token。`AgentLiveAcceptanceTest` 在 Android 16 上完整通过，耗时约 303 秒。实际覆盖文字流式、工具调用与结果回传、图片随机码读取、PDF 随机码读取、Markdown 附件转 `output_file`、严格结构化片段摘要、长笔记摘要、`memory_search` 和 `memory_read` 后回答原行程。两个历史工具均有 Committed 记录，历史读取后回复包含杭州目的地。凭据不进入源码、消息或日志；测试结束清除临时配置与测试资料。日志：`build/s11-live-acceptance.log`、`build/s11-live-stages.log`。Anthropic、Gemini 的请求、流式事件、错误与工具回传使用协议级自动化验证。

构建验证：`testDebugUnitTest` 共 162 项，161 项通过，1 项需显式配置的 JVM 真实服务用例默认跳过；真实服务另由上述 Android 用例执行。`assembleDebug`、`assembleDebugAndroidTest`、`assembleRelease` 和 `lintDebug` 成功。Lint 0 错误、22 条警告：19 条既有依赖／资源／公共控件警告，以及 PDFBox 间接引入 BouncyCastle 的 3 条信任管理器警告；应用请求继续使用默认 OkHttp TLS，不调用这些 PDF 依赖中的 TLS 实现。Release 的 R8 仅忽略 PDFBox 可选 JPEG 2000 解码器的缺失类型，PDF 文字提取不使用该解码器。日志：`build/s11-final-build.log`、`build/s11-final-ui-build.log`、`build/s11-release-final.log`。

最终 Android 回归覆盖 24 个测试类、155 项用例：事实源、2→15 迁移、模型配置、权限矩阵、快照、队列、实时写入、并行编辑、累计 Diff 与回退、片段／画像／笔记记忆、来源删除／遗忘、历史分页、文件导入与回收，以及公共控件和页面流程。154 项在集中回归通过，设置页的保存失败重试用例以语义点击复验通过；相关手机 UI 共 8 项全部通过。日志：`build/s11-final-android.log`、`build/s11-final-phone-ui.log`。最终 UI 收尾后的 Debug、测试 APK、Lint 和 Release 再次成功，见 `build/s11-final-polish-build.log`。

设备与界面验收使用 Android 16 模拟器：720×1280／280 dpi 手机，以及 1280×800／160 dpi 模拟平板。平板文件、画像、模型设置和 Agent 流程 13 项全部通过；已查看两种尺寸的文件预览、画像管理和模型设置截图，操作与文字无裁切。无障碍检查覆盖内容语义、点击动作、启用状态及公共控件尺寸。截图在 `build/s11-phone/`、`build/s11-tablet/`，平板日志为 `build/s11-final-tablet-ui.log`。测试后屏幕尺寸、密度已恢复。

最新实现再次通过实际进程终止恢复：核对准备标记和 PID 14792 后执行 SIGKILL，新进程恢复运行与部分回复，五条已提交工具记录不重放，队列保持暂停，用户并行编辑得到保留。恢复用例 1 项通过，日志为 `build/s11-process-prepare.log`、`build/s11-process-recover.log`。后台通知、锁屏、通知拒绝、系统超时与后台启动限制继续采用上文原生平台验收证据。

S11.1–S11.12 已完成，README、开发顺序、实施方案、功能清单和记忆架构已同步。

## Agent 工具与按需读取验收（2026-09-29）

连接测试仅记录一次文字流式请求的可用性，工具根据权限默认提供；新增连接状态展示。统一 `read` 覆盖授权目录、笔记本内笔记和固定版本正文。附加笔记初始只提供引用元信息，正文按工具调用返回。工具注册与执行独立为 `AgentBuiltInToolProvider`、`AgentToolRegistry` 和 `AgentToolExecutor`，支持扩展 Provider 的独立权限、审计、调用去重及外部结果核对。

Android 16 综合回归 102 项全部通过，包含笔记工具、时间线、创建／修改／删除、连接测试与模型设置、编辑器润色、记忆与来源传播。新增用例验证：未测试连接的模型能先读取快照再写入；目录不返回正文或未授权笔记本；空笔记本目录撤权后不能通过历史派生内容重传；附加正文只在 read 后返回且保持发送版本；外部 Provider 不持有数据库写事务等待服务，结果未知时只核对、不重复执行。日志：`build/agent-tools-acceptance.log`。显式 `note_id: null` 的目录撤权用例另行复验通过，日志：`build/agent-tools-null-directory-check.log`。

全体单元测试 164 项，其中 163 项通过、1 项真实服务测试按默认配置跳过；Lint 0 错误、22 条依赖版本／既有公共组件和资源警告。Debug APK 构建通过。当前新增工具流程使用受控模型及模拟外部 Provider 验证；真实服务覆盖范围继续以上文独立联调记录为准。
