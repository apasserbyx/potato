# Windows 端共享 Kotlin 数据与同步核心

Windows 客户端使用 Kotlin Multiplatform 共享数据模型、同步协议和业务规则，界面使用 Compose Desktop；Android 继续使用现有 Compose UI，但逐步把平台无关代码迁入共享模块。选择它是因为两端需要长期对等同步，复用核心可以避免两套冲突规则分叉；代价是需要重构 Android 的数据库和平台服务边界。考虑过的替代方案：Web/PWA + Tauri、独立 Electron 或原生客户端。
