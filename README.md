# Potato

**一个极简、安静、可自定义的 Android 专注计时与习惯养成应用。**

土豆钟 + 正计时 + 习惯打卡 + 时间记录，本地优先，无需账号。

[下载测试版 APK](https://github.com/apasserbyx/potato/releases) ·
[习惯模块设计](docs/HABITS.md) ·
[改进计划](docs/IMPROVEMENT_PLAN.md) ·
[产品设计](docs/DESIGN.md)

![Potato 浅色模式](docs/screenshots/focus-light.png)
![Potato 深色模式](docs/screenshots/focus-dark.png)

---

## 功能

### 专注

- 土豆钟：专注 / 短休 / 长休，支持自动开始休息
- 专注完成提示音与休息结束提示音，均可自定义
- 正计时：不设终点，结束后自动写入专注记录
- 专注页内直接按 5 分钟增减本次专注时长
- 暂停、继续、提前结束，暂停时间不计入专注分钟
- 绝对时间戳计时，锁屏、后台、App 被系统杀掉后仍可恢复
- 前台服务 + AlarmManager 双重保障
- 结束提醒与锁屏常驻通知

### 习惯

- 计时型习惯：目标分钟数，开始后进入专注页倒计时，完成后自动打卡
- 次数型习惯：例如喝水 3 杯，支持 +1 / −1
- 每天 / 自选星期重复
- 两种提醒方式：
  - 早 / 中 / 晚汇总提醒
  - 每个习惯独立提醒时间
- 自定义习惯提示音：
  - 3 个内置音效
  - 系统默认提示音
  - 系统铃声选择器
  - 支持试听和测试提醒
- 默认由 App 直接播放提醒音，通知渠道本身保持静音：
  - 避免 OPPO / 一加等机型拦截通知渠道声音
  - 到点后 App 主动播放，尊重静音、媒体音量和免打扰
  - 可在设置中切回系统通知声音
- 支持导入本地音频作为提示音（全局、单个独立习惯和计时提示音都可用，会请求媒体读取权限）
- 自定义习惯图标：
  - 12 个内置矢量图标
  - Emoji
  - 从相册上传图片（自动裁剪压缩）
- 提醒强度：轻柔 / 标准 / 醒目
- 通知操作：完成、+1、稍后 10 分钟、跳过
- 稍后提醒会持久化，并在重启后恢复
- 每个习惯独立连续天数
- 手动补完成、今天跳过、编辑、移除
- 习惯详情页：
  - 当前连续 / 最佳连续 / 完成率 / 累计投入
  - 30 天与 90 天完成热力图
  - 最近 12 条记录
  - 详情内直接编辑、跳过或移除
- 点击习惯卡片进入详情；点击提醒通知打开习惯页
- 专注页的「今天想专注做什么」抽屉支持切换到习惯：
  - 计时型习惯直接开始
  - 次数型习惯 +1
  - 手动标记完成

### 任务

- 快速添加、选择、完成、删除任务
- 任务标签与预估土豆数
- 任务完成后自动从待办列表隐藏

### 数据

- 今日专注分钟、土豆数、自由专注次数
- 最近 7 天 / 30 天趋势
- 标签时间分布
- 记录明细
- 完整备份导出 / 导入（zip）：
  - 专注记录、任务、节奏、习惯与打卡
  - 设置与提醒时间
  - 自定义习惯图标与导入的提示音
  - 导入前预览数量，导入时覆盖当前数据

### 体验

- 一次性完成的完整 Android 原生体验
- 土豆配色与手绘土豆图标
- 浅色 / 深色模式
- 本地优先，无需注册

---

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- SQLite（`SQLiteOpenHelper`，数据库版本迁移）
- 前台服务 + AlarmManager + BroadcastReceiver
- 普通通知 + 通知操作 + 深链
- Gradle + Android SDK 35

最低支持 Android 8.0（API 26），目标 SDK 35。

---

## 构建

需要：

- JDK 21
- Android SDK 35
- Android SDK Build Tools 35.x

在项目根目录创建 `local.properties`，指向本机 Android SDK：

```properties
sdk.dir=/path/to/Android/Sdk
```

Windows 示例：

```properties
sdk.dir=D\:\\android sdk
```

构建 Debug APK：

```bash
./gradlew :app:assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

运行单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

---

## 项目结构

```text
app/src/main/java/com/potato/focus/
  data/        数据库、模型、DAO、统计与习惯计算
  timer/       计时状态机、前台服务、闹钟、通知
  habits/      习惯提醒排程、接收器与通知
  ui/
    focus/     专注页
    habits/    习惯页
    stats/     数据页
    settings/  设置页
    components/通用组件
    theme/     土豆配色与主题
  util/        时间格式化、数据备份、自定义图标与音频存储
```

设计文档：

- [产品设计](docs/DESIGN.md)
- [习惯模块设计](docs/HABITS.md)
- [改进计划书](docs/IMPROVEMENT_PLAN.md)

---

## 权限说明

- `POST_NOTIFICATIONS`：计时结束和习惯提醒
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`：后台计时
- `SCHEDULE_EXACT_ALARM`：精确提醒
- `RECEIVE_BOOT_COMPLETED`：重启后恢复提醒排程
- `VIBRATE`：完成与提醒触感
- `READ_MEDIA_IMAGES` / `READ_MEDIA_AUDIO`：选择习惯图标和导入提示音
- `READ_EXTERNAL_STORAGE`（旧版 Android）：兼容旧系统媒体读取
- `WRITE_EXTERNAL_STORAGE`（Android 9 及以下）：保存导入到通知音目录
- `MODIFY_AUDIO_SETTINGS`：声音试听与诊断（普通权限，无弹窗）
- `ACCESS_NOTIFICATION_POLICY`：可选的免打扰状态查看权限

Android 没有单独的「声音权限」。提醒音由 App 直接播放，并遵循系统静音、媒体音量和免打扰设置。

---

## 隐私

- 默认完全本地存储，不需要账号，不上传专注记录
- 备份只在用户主动导出时离开设备
- 导入备份会覆盖当前数据，请先确认文件来源
- 当前版本没有云同步

---

## 当前限制

- Release APK 为 GitHub Actions 构建的测试版 Debug 签名，适合个人安装与体验，不适合应用商店分发
- CI 构建的 Debug 签名与本机 debug 包可能不同；如果手机上已安装本地调试包，覆盖安装可能失败。请先导出完整备份，卸载旧包后再安装并导入
- 备份导入目前只支持「覆盖恢复」，暂不支持合并；导入前会显示记录数量
- 备份文件不包含录音权限、通知权限等系统授权，恢复后需要重新授权
- 未完成追加提醒开关尚未实现（已按需求跳过首版）
- 归档习惯的恢复入口和拖拽排序尚未实现
- 国产 ROM 的后台限制仍需要真机验证

---

## License

当前项目未添加开源许可证。
