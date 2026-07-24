# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

该项目的所有重大更改都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，
本项目遵循 [语义化版本控制](https://semver.org/spec/v2.0.0.html)。

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
