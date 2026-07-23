# MeaPet —— 梅尔桌宠


**一只基于 Live2D 的 AI 桌宠**

本项目由 [suan-11/mea-pet-public](https://github.com/suan-11/mea-pet-public) 衍生

---

## 功能

- **Live2D 模型** — 基于 Live2D Cubism SDK 的主页模型展示，支持触摸交互与视角跟随
- **AI 聊天** — OpenAI 兼容 API 客户端，支持多轮对话、System Prompt 与记忆上下文注入
- **多主题配色** — Material You 动态取色 + 12 套预设色板（紫罗兰、海洋、森林、日落、玫瑰……），支持浅色/深色模式
- **悬浮窗模式** — 前台 Service 浮窗运行，支持拖拽、捏合缩放、双击关闭


## 开始使用

### 前置要求

- Android 8.0（API 26）+
- 一个 OpenAI 兼容的 API 端点（可自部署或使用第三方服务）

### 构建

```bash
git clone https://github.com/llz121517/MeaPet.git
cd MeaPet
./gradlew assembleDebug
```

安装生成的 APK：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 配置

在设置页填入：

| 字段 | 说明 |
|------|------|
| **API Key** | API 密钥 |
| **API 地址** | OpenAI 兼容的 API 基础 URL |
| **模型** | 使用的模型名称（如 `gpt-4o-mini`） |
| **Temperature** | 生成温度 (0.0–2.0) |
| **最大 Token** | 单次响应最大 Token 数 |

## 技术栈

```
Live2D Cubism  ·  Jetpack Compose  ·  Ktor  ·  Coroutines  ·  GLSurfaceView
```

## 许可证

本项目基于 [MIT](LICENSE) 许可证开源。
