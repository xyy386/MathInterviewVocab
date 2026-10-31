# Android 1.2.0 发布审查

审查日期：2026-10-31。范围为当前原生 Android 源码及用户交付的正式 APK；本次只做审查、测试、需求／设计文档和发布准备，没有修改功能实现。没有运行模拟器或 PD，没有安装、卸载或清空用户设备数据。

## 结论与证据等级

在本次源码、自动化和成品检查范围内，未发现 P0/P1 级阻断问题；发现 3 项 P2 可靠性／交互风险，见下文，尚未在实体设备上由审查者复现。

项目所有者明确确认“全部都测试过了，公开正式 APK”。这属于**用户报告的实体手机／平板验收结果**，不是审查者亲自执行；用户未提供设备型号、Android 版本或逐项截图，本报告不补造这些信息。自动化通过不代表所有 Android 版本、尺寸或 OEM 安装器都已覆盖。

依据用户确认及正式发布授权，本次允许发布既有 APK，并公开保留以下已知风险。后续功能修复由项目所有者自行实现。

## 成品身份

| 项目 | 实测结果 |
| --- | --- |
| 文件 | `MathInterviewVocab-Android-universal-v1.2.0.apk` |
| 大小 | 100174552 bytes |
| SHA-256 | `597097c72af975a9893d888aafb6e6eabeb7b72830e00d199b76a04e904e3498` |
| 包名 | `com.xingyiyao.mathinterviewvocab` |
| 版本 | 1.2.0 / versionCode 1 |
| min / target API | 29 / 36 |
| 公开证书 SHA-256 | `24e92a08b1fe647bb6d1726a6208ad75436c1ac2a391651d58c68c067dce1069` |
| 构建类型 | Release，非 debuggable，签名有效 |
| ABI | arm64-v8a、armeabi-v7a、x86、x86_64 的 Compose graphics-path 库 |

本次签名重建成功；重建包与上述原 APK **所有 ZIP 条目及解压内容逐字节相同**，证书一致，但整体文件 SHA-256 不同，因此不宣称二进制逐字节可重现。正式发布保留用户确认过的原 APK，不用重建包替换。APK 本身的 VCS 元数据为 `NO_SUPPORTED_VCS_FOUND`；源码提交对应关系依赖本次重建的完整内容比较与随后固定的 `android-v1.2.0` 源码标签，不能只根据 DEX 类名认定。

## 自动化与静态检查

执行 `:app:testDebugUnitTest :app:testReleaseUnitTest :app:lintRelease`：

- Debug：107 项，0 失败、0 错误、0 跳过。
- Release：相同测试集 107 项，0 失败、0 错误、0 跳过；不是 214 个独立用例。
- Release Lint：0 错误、25 警告，主要为依赖新版本提示、KTX 写法、图标单色版本及持久化同步写入提示，未据此擅自升级依赖或改写代码。
- 独立正式签名构建成功；SDK XML schema 版本提示没有阻止本次构建。
- APK 签名、版本、权限、关闭备份／明文网络的 Manifest、ZIP CRC、重复路径、Universal ABI 和 ZIP 对齐检查通过。
- 对拟提交的 Android 源码做敏感凭据特征与文件清单检查，未发现私钥、令牌、用户记录或成品被纳入源码清单。签名恢复备份实际打开并导出证书，与公开指纹一致；不公开密钥、密码及私有路径。

APK `terms.json`、音频索引与规范源文件逐字节相同；326 条、130 条重点、稳定 ID 1–326、5 科目及两种 IPA 符合契约。2328 个 MP3 的文件集合、大小和 SHA-256 全部通过；4 份许可证／来源声明与源码对应文件相同。

静态检查包含：原子保存与读回、损坏数据保护、旧偏好回退、答题 UUID 去重、2 次清错／3 次掌握、收藏独立、严格 Release／APK 校验及显式 PackageInstaller 用户确认。未发现绕过包名、证书、版本、SHA-256 或系统用户确认的 P0/P1 路径。

## P2-01：取消后旧更新任务可能清理新状态

证据：[UpdateEngine.kt](../app/src/main/java/com/xingyiyao/mathinterviewvocab/updates/UpdateEngine.kt) 第 67–69、151–152、260–262 行。

检查／下载的工作体在首次网络操作前调用 `discardVerified()`，并可能清空 Release；这些清理没有校验该工作体的 generation。旧任务已开始但在线程切换处暂停，用户取消并完成新操作后，旧任务继续时可能清掉新 verified 引用／文件或新 Release，造成“就绪／可下载”的界面状态与实际引用不一致。

影响是更新流程需要重试或重启；未发现借此绕过安装校验或改写学习记录。属于静态并发路径分析，不声称已真机复现。

后续设计要求：共享状态的清空与删除也必须受操作代次约束；先在锁内验证所有权，再移除所拥有引用，锁外仅删除该引用的唯一私有目录。补充可控制调度的“旧任务暂停→取消→新任务完成→旧任务恢复”测试。

## P2-02：安装准备期间返回前台可能卡住确认流程

证据：[AndroidUpdater.kt](../app/src/main/java/com/xingyiyao/mathinterviewvocab/updates/AndroidUpdater.kt) 第 72–105、126–128、146–162、188–190 行。

点击安装后先设置 `installationActive=true`，再在后台重新校验 APK／创建 Session。若这段时间切后台并很快返回，前台检查可能因 Session 尚不存在而清掉 installationActive；原后台任务未取消，仍可能创建、提交 Session。随后收到 pending-user-action 虽显示继续按钮，却未恢复该布尔值，而 `continueInstall` 要求其为 true，可能出现按钮无效。

影响是特定时序下无法继续本次更新；不是静默安装或签名绕过，原有学习数据不应受影响。审查者未执行真机复现。

后续设计要求：区分“准备中”“Session 已创建”“等待系统确认”；前台检查不能把准备中当会话结束。为安装操作绑定代次／Session 所有权，取消时让 worker 同步失效。增加大 APK 复核期间快速切后台返回、授权页面往返和延迟回调测试。当前可先保持应用前台完成安装准备；发生卡住时重启应用并重新检查，不能通过卸载／清数据处理。

## P2-03：换题／切词条沿用阅读滚动位置

证据：[AcademicTheme.kt](../app/src/main/java/com/xingyiyao/mathinterviewvocab/ui/AcademicTheme.kt) 第 85–99 行、[Quiz.kt](../app/src/main/java/com/xingyiyao/mathinterviewvocab/ui/Quiz.kt) 的 ActiveQuiz 阅读容器，以及 [AppViewModel.kt](../app/src/main/java/com/xingyiyao/mathinterviewvocab/AppViewModel.kt) 第 303–309 行。

阅读容器内部保留同一 ScrollState，下一题仅改变 index，详情切换也未以词条身份重建滚动状态。若反馈页滚到末尾再下一题，旧 offset 可能沿用或被钳制，新题题干在屏幕上方，需要手动滚回顶部。

后续设计要求：按题目／词条身份触发顶部复位，同时避免仅旋转／改变窗口宽度时无条件重置；测试长反馈、大字体、双栏切词、下一题／上一题的差异。

## 未覆盖的测试边界

当前 6 项布局策略测试验证纯函数；真实 App 壳层另用 Material 3 Adaptive 的窗口分支，不能仅据纯函数断言整窗断点正确。2 项 Compose 测试是 360 dp 局部控件的长文本／200% Density 与阅读容器，不是完整 VocabularyApp 或原生 TextView 的系统字体、选区菜单、旋转、分屏、IME 与进程恢复验收。

用户已报告手机与平板测试全部通过；由于未提供型号／系统版本，不能给出具体设备覆盖清单，也不声称审查者进行了 USB 真机操作。新增回归建议详见 [需求与设计](REQUIREMENTS-AND-DESIGN.md) 和 [设备验收表](DEVICE-ACCEPTANCE.md)。

## 发布保护与交付

保留原 `v1.2.0` 标签及 3 个桌面附件；基线见 [DESKTOP-RELEASE-BASELINE.json](DESKTOP-RELEASE-BASELINE.json)。Android 源码另固定标签 `android-v1.2.0`，Release 链接该标签；仅上传原 APK 和独立 `SHA256SUMS-Android.txt`。发布成功的最终判断还必须包含公开下载回验和桌面附件基线复核，上传命令成功本身不代替这些检查。
