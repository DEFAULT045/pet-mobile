# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

该项目的所有重大更改都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，
本项目遵循 [语义化版本控制](https://semver.org/spec/v2.0.0.html)。

---

## [1.2.1] - 2026-07-26

### Added

- **模型知道当前时间** — 每轮请求把设备本地时间、星期、时区与 UTC 偏移拼进请求（`TimeContext`），问「几点了 / 今天几号 / 星期几」不再瞎猜或回避。时间每轮都变，因此不写入会话历史。

### Fixed

- **自动摘要几乎从不触发** — 轮次计数器是内存字段，冷启动即归零；默认每 10 轮摘要一次，而一次会话往往聊不满 10 轮，实际等于永不触发、短期记忆只增不减。计数器改存 DataStore，跨进程延续。同时触发条件从取模改为「攒够即清零」——取模会让中途调小间隔的用户白等一个完整周期。
- **摘要可能反而丢信息** — 摘要模型若没返回 `keywords`，生成的长期记忆永远匹配不上检索（`getRelevant` 直接跳过无关键词条目），等于删掉 N 条短期记忆换来一条死记录。现在关键词为空则放弃本次摘要，短期记忆原样保留。关键词也统一做 trim / 去空 / 去重。

### Changed

- **记忆协议块回贴历史** — 此前存入会话历史的助手消息是剥离过协议块的可见正文，于是每轮请求发出去的历史里，模型自己过去的回复**全都没有这个块**。这份实证比 system prompt 里的要求更有分量：漏输出一次就会自我强化，越往后越不输出，只有清空上下文才恢复正常。现在协议块原文随消息保留（`ChatMessage.memoryOpsBlock`，不展示、不计入正文），组装请求时贴回最近 3 条助手消息（`AppConfig.memoryOpsEchoTurns`），让模型有自己写过的正例可循。
- **模型更愿意记短期记忆** — 记忆协议说明重写。原文「闲聊寒暄、临时性的一句话通常不需要记」直接压制了短期记忆的全部素材，且举例清一色是姓名生日这类事实。现在 SHORT_TERM 是默认档位并给出正例，明确「每轮 1~3 条，只有纯问候或用户整轮都在提问时才输出 []」，并显式豁免人设里的字数与语气约束（默认人设含「极简 20-40 字」，模型会把它一并套到协议块上）。请求末尾另加一句收尾提醒，紧邻生成点，避免模型写到最后忘掉协议。
- **请求按「是否每轮都变」分层，削减 token 开销** — 协议说明从 ~1245 字压到 ~706 字（保留 SHORT_TERM 默认档位、风格豁免、`[]` 是例外三处关键表述），收尾提醒与时间说明同步收紧。同时重排消息：稳定内容（人设 + 协议说明 + 用户人设条目）留在首条 system 消息，每轮都变的内容（当前时间 + 相关回忆 + 收尾提醒）压到对话历史之后的尾部 system 消息 —— 此前时间夹在中间，导致排在它后面的协议说明与全部历史都无法命中服务端的 prefix cache。固定开销约降 40%，缓存命中的部分通常另按 10~25% 计价。
- **【用户人设】注入封顶** — `FACTUAL` / `CORE_TRAIT` 永不自动淘汰又每轮全量注入，条数只增不减，用久了会无限撑大每次请求。现按重要性取前 30 条注入（`AppConfig.maxPersonaFacts`），输出顺序仍按创建时间以稳定缓存前缀。**只影响注入**，存储与「查看记忆」界面仍是全量。
- **历史裁剪改批量** — 到达窗口上限后原本每轮丢弃最老的 1 条，消息前缀每轮都往后挪一格，缓存全程不命中。改为一次裁掉 8 条（`AppConfig.historyTrimBatch`），中间 7 轮前缀完全相同。历史窗口同时由 40 调整为 35。
- **摘要设最小条数门槛** — 短期记忆不足 3 条时跳过本次摘要，攒着下次再合；只有一两条时「摘要」等于原样搬运，白花一次请求（`AppConfig.minSummaryItems`）。
- **批量删除记忆** — 新增 `MemoryRepository.deleteAll(ids)`，摘要收尾整批只落盘一次；此前逐条删除会把整个记忆库重写同样多次。
- **日志不再打印对话内容** — `MemoryOpsProtocol` 解析失败时原本会输出协议块原文片段与整个畸形 JSON，现只记字符数与异常信息。
- **版本号升至 `1.2.1`**（versionCode 6）。

### Notes

- `ChatMessage` 新增 `memoryOpsBlock` 字段。旧的会话文件没有该字段，加载后为 null，表现为升级前的历史消息不参与协议块回贴，其余不受影响。
- 请求现在可能包含两条 system 消息（首条与尾部）。OpenAI 兼容端点普遍支持，若你的中转要求 system 必须唯一或必须在首位，需自行把尾部块改为 `user` 角色。
- 新增 `ConversationManagerTest`、`MemoryManagerTest` 与 `TimeContextTest`，单测共 100 条。

---

## [1.2.0] - 2026-07-26

### Added

- **聊天记录持久化** — 新增 `ConversationStore`，会话历史随消息变更异步落盘（合并写 + 原子替换 + 损坏文件 `.corrupt` 备份），启动时恢复到界面。此前是纯内存的，强杀进程重开必然清空。
- **「查看记忆」界面** — 首页三点菜单新增入口，展示记忆统计与条目列表（内容、类型、重要性、关键词），支持单条删除与「清除全部」（二次确认）。
- **摘要轮次可调** — 设置页「记忆系统」新增滑杆，可设定每隔多少轮触发一次摘要（3~30，默认 10）；关闭「自动摘要」时置灰。此前硬编码 10 轮。

### Changed

- **记忆创建改由大模型自主决定** — 移除程序侧启发式提取（按字数阈值 + 关键词表打分，中文日常聊天几乎触发不到）。新增 `MemoryOpsProtocol`：模型在回复末尾附加 ` ```memory-ops ` 代码块，声明本轮 `create` / `update` / `delete` 哪些记忆及其类型、重要性、关键词；该块解析后从回复中剥离，用户不可见。全程容错，块缺失 / JSON 畸形 / 围栏未闭合一律静默跳过，不影响聊天回复。未采用 function calling——不少 OpenAI 兼容中转对其支持不稳定，且要多一次往返。
- **记忆检索改为匹配模型给出的关键词** — 移除程序侧切词 / CJK 二元组匹配。原实现按空白与标点切词，中文整句被当成单个关键词，`contains` 几乎永不命中，磁盘上的记忆从来没被注入过上下文。
- **事实与特质永不自动淘汰** — `FACTUAL` / `CORE_TRAIT` 排除出容量淘汰池（`maxItems` 仅约束短期 + 长期），并每轮全量注入 system prompt（带 id 供模型引用）；短期 / 长期仍按关键词匹配注入。手动删除不受影响。
- **摘要改为消费短期记忆** — 不再发送原始对话文本，改为把已攒下的短期记忆压缩成一条长期记忆并删除参与摘要的短期条目；失败时原样保留，下次再试。
- **`MemoryItem.tags` → `keywords`** — 标签字段替换为检索关键词，按标签分组的相似度去重（`consolidate`）一并移除——模型每轮能看到全部事实与特质，重复时会自己走 `update`。
- **记忆 id 改为短 id** — 完整 UUID 改为 `mem_` + 8 位随机字符，省 token 且模型抄写更不易出错。
- **记忆链路日志** — 各决策点补充 Logcat（是否注入协议、是否解析到块、失败原因、落库结果），`adb logcat -s MemoryOpsProtocol MemoryManager MemoryService` 即可排查；仅记长度与计数，对话内容不入日志。
- **死代码清理** — 移除 `ChatService` / `ConversationManager` 中已无调用方的 `getRecentExchanges()`、`getContextText()`，以及 `MemoryService` 的 `extractFromExchange` / `calculateImportance` / `extractTags` / `consolidate` / `isSimilar`。
- **版本号升至 `1.2.0`**（versionCode 5）。

### Fixed

- **启动加载与首次写入竞态导致记忆被整体覆盖** — 异步加载未完成前若先发生一次保存，会把只含新条目的内存列表写回文件，旧记忆全丢。改为惰性加载兜底，读盘必定先于首次写盘。这是「记忆大退就没了」中真正丢数据的一环。
- **ViewModel 早于异步加载完成导致界面空白** — `AppContainer` 暴露 `warmUpJob`，`ChatViewModel` 等待完成后刷新消息列表，并按 id 去重保留加载期间的新消息。
- **`ConversationManager` 线程安全** — 启动恢复在 IO 线程执行，与发送链路并发访问消息列表，所有读写方法改为加锁串行化。
- **`MemoryRepository` 可测试性** — 构造参数由 `Context` 改为 `filesDir: File`，可在纯 JVM 单测中验证持久化、淘汰与检索。

### Notes

- **老数据会有降级**：已存在的旧记忆没有 `keywords`（旧 `tags` 静默丢弃），因此旧的短期 / 长期记忆无法被检索到；事实类不受影响，仍全量注入。建议升级后在「查看记忆」中清空重来。
- 本版本含用户可见新功能与记忆系统行为重构，聊天与 API 配置流程向后兼容，按语义化版本升 **MINOR**。
- 新增测试依赖 `mockito-kotlin`（仅 `testImplementation`，不进入 APK）。

---

## [1.1.0] - 2026-07-25

### Added

- **检测新版本** — 启动时静默请求 GitHub `releases/latest`，有正式新版本时在聊天页底部 Snackbar 轻提示（可点「查看」打开发布页）；关于卡片「检查更新」绑定同一逻辑，手动检测会反馈有更新 / 已最新 / 失败。网络异常启动路径静默失败，不打扰。Snackbar 动作色跟随主题 `primary`。
- **获取模型列表** — 设置页模型输入框下增加「获取模型列表」：用当前 API Key / 地址请求 `/v1/models`，解析 id 列表后点选写回；兼容 `data[]` 与顶层数组两种响应格式，并补充解析单测。
- **关于页可点击外链** — 关于卡片内新增主题色（`primary`）下划线链接：Live2D 模型来源、GitHub 仓库、交流 QQ 群。
- **Live2D 模型来源** — 关于页补充模型来源入口（Bilibili）。

### Fixed

- **记忆链路** — 启动时加载持久化记忆；手写 JSON 改为 `kotlinx.serialization` + 原子写；记忆总开关真正关闭提取 / 摘要 / 注入；访问统计落盘。
- **聊天链路** — 清空会话时取消在途请求，避免回复回写；记忆后处理异步化，摘要不再卡住发送。
- **API 客户端** — `reloadClient` 可热重建；`HttpTimeout` 适配 LLM 长回复；`baseUrl` 规范化（用户可带或不带 `/v1`，客户端统一补齐后拼 `models` / `chat/completions`）；`max_tokens` 入请求；取消与空响应处理。
- **悬浮窗 / GL** — `START_NOT_STICKY`、失败自停、native 模型释放、捏合后拖动锚点与屏幕边界钳制；Activity 与 Service 的 GL 线程串行化，避免共享 shader 单例竞态；单例改用 application context，避免持有已销毁 Activity。
- **设置保存** — 输入框失焦保存、Slider 结束写盘；`SettingsManager` 增加内存快照缓存；DataStore 备份排除敏感项。
- **「变态」语音** — 触摸语音文件原先只有 “hen”，已更换为正确的 “hentai” 资源。

### Changed

- **关于卡片** — 从设置页挪到聊天页三点菜单入口；改为带动画的悬浮对话框，展示应用介绍、版本号与技术栈，系统返回键可关闭；设置页原关于模块移除。
- **包名迁移** — `com.llz121517.meapet` → `com.meapet.mobile`（namespace / applicationId 同步；注意：applicationId 变更后与 1.0.x 安装包不连续升级，需重新安装）。
- **targetSdk 36** — 补充 `POST_NOTIFICATIONS`、前台服务 `specialUse` 声明。
- **死代码清理** — 移除 `Live2dActivity`、`TouchManager` 等未使用组件。
- **版本号升至 `1.1.0`**（versionCode 4）。

### Notes

- 本版本相对 1.0.2 含用户可见新功能（更新检测 / 模型列表 / 关于外链）与多项修复，按语义化版本升 **MINOR**；未升 MAJOR，因 API 配置与聊天流程仍向后兼容。包名变更对旧安装是例外，见上。

---

## [1.0.2] - 2026-07-23

### Fixed

- **悬浮窗未加载完返回应用崩溃** — `SurfaceView` 的延迟绘制回调（`performDrawFinished` → `requestTransparentRegion`）在 View 已被 `removeView` 摘除后触发，`getParent()` 为 null 导致 NPE。将 `removeView` 通过 `Handler.post` 延迟到当前消息队列末尾执行，确保所有 pending 回调先完成。
- **主题模式切换框选择菜单不稳定** — `ExposedDropdownMenuBox` 内 `menuAnchor` 的触摸处理与 `OutlinedTextField` 内部手势产生冲突，偶发点击不展开。改为独立透明点击覆盖层 + 手动 `Popup`，彻底解决。

### Changed

- **颜色预设系统重构** — 从每套预设手工编写完整 `ColorScheme`（24 个对象），改为单 seed 主色 + 工具函数（`lighten`/`darken`/`desaturate`/`hueShift`）自动生成全套浅/深色方案。新增 `seed` 字段，预览色块直接使用主色。
- **首页菜单重做** — 从 `DropdownMenu` 改为 `Popup + Surface + Animatable`，宽度缩至 130dp，菜单项间添加分隔线，弹出位置固定在三点按钮正下方，增加淡入 + 右上角缩放入场/退场动画。
- **主题模式选择器动画** — 弹出菜单增加淡入 + 缩放动画，宽度与输入框精确对齐。
- **浅色模式滑动条底色** — 未选中区域底色从白色 60% 透明度改为 35% 透明度（`Color.White.copy(alpha = 0.35f)`），呈现更浅白的半透明效果。
- **动态颜色开关** — Android 12 以下设备开关置灰不可操作，提示文字更新为"当前系统不支持动态颜色"。
- **Switch 组件** — 统一设置页 Switch 颜色，与主题背景色协调。
- **版本号升至 `1.0.2`**（versionCode 3）。

### Added

- **关于部分** — 在设置页面的关于部分添加了累计Token消耗量显示

---

## [1.0.1] - 2026-07-22

### Fixed

- **LifecycleManager 递归栈溢出导致切后台崩溃** — 构造参数 `onTrimMemory` 与 override 方法同名，导致无限递归调用自身而非 lambda。重命名为 `trimMemoryCallback`。
- **悬浮窗关闭时 GL 上下文跨域崩溃** — 主 Activity 试图释放悬浮窗 GL 上下文的 shader 程序，跨上下文 GL 操作导致原生崩溃。跳过 `releaseInvalidShaderProgram()`，直接 `deleteInstance()` 重建。同时修复 service `onDestroy()` 未先暂停 GL 线程就直接 `removeView` 的竞态问题。

### Changed

- API Key 输入框键盘类型从 `Password` 改为 `Uri`，允许使用剪贴板粘贴。
- 版本号升至 `1.0.1`（versionCode 2）。
- 关于页版本号改为从 `PackageManager` 动态读取 `versionName`，不再硬编码。

### Added

- API 配置区提示文字："需要一个 OpenAI 兼容的 API 端点"。
- 设置页关于介绍更新。

---

## [1.0.0] - 2026-07-21

### Added

- **Live2D 模型渲染** — 基于 Live2D Cubism SDK 的主页模型展示与悬浮窗模式。
- **AI 聊天** — OpenAI 兼容 API 客户端，支持对话管理、System Prompt 与记忆上下文注入。
- **记忆系统** — 短期/长期记忆提取、AI 摘要、相关性检索与文件持久化。
- **多主题配色** — Material You 动态取色 + 12 套预设色板，支持浅色/深色模式。
- **触摸分区反馈** — 模型区域分三区，点击触发随机语音播放与气泡文字。
- **视角跟随** — 触摸时模型头部与视线跟随手指方向。
- **悬浮窗** — 前台 Service 浮窗模式，支持拖拽、缩放、双击关闭。
- **设置页** — API 配置、模型参数、System Prompt、记忆开关、主题选择。
- **全屏沉浸** — 隐藏系统状态栏/导航栏，GLSurfaceView + ComposeView 混合渲染。

### Fixed

- 修复 AndroidManifest 缺失 `INTERNET` 权限导致的网络请求 `EPERM` 崩溃。
