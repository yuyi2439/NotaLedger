# NotaLedger

Android 本地记账应用，使用 Kotlin、Jetpack Compose 和 Room。支持收支记录、两级分类、月度预算、统计，以及需要用户确认的支付通知草稿。

## 开发与验证

在 `local.properties` 中配置 Android SDK 路径，使用 Gradle Wrapper：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
# 连接测试设备或启动模拟器后执行：
.\gradlew.bat connectedDebugAndroidTest
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。设备测试使用独立命名的测试数据库和偏好设置，不读写正式账本。

## 数据约定

- 数据库 v3 以 `Long` 类型的 `amountCents` 存储金额，单位为分；输入转换使用十进制四舍五入。界面金额仍以元显示。
- v1 两级分类开发版和 v2 单层分类版通过显式迁移升级到 v3，保留记录 ID、分类关系、备注和时间。迁移失败时不自动清空数据库。
- Room schema 位于 `app/schemas`。后续结构变更需提高版本号、添加迁移并补充设备测试，不能恢复破坏性迁移兜底。
- 新预算以整数分保存，兼容读取旧版浮点预算。
- 默认分类只初始化一次；用户主动删空后不会重新生成。

当前尚未提供手动备份恢复或 CSV 导出功能。
