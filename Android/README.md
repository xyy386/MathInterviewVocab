# 数学面试词汇 · Android 1.2.0

Kotlin + Jetpack Compose 原生工程，最低 Android 10（API 29），compile/target 36。包名 `com.xingyiyao.mathinterviewvocab`，首发版本 `1.2.0` / code `1`。本工程不使用 WebView、账号或跨端同步。

**Android 1.2.0 正式签名版**：[下载 Universal APK](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.2.0/MathInterviewVocab-Android-universal-v1.2.0.apk) · [SHA-256](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.2.0/SHA256SUMS-Android.txt) · [Android 源码标签](https://github.com/xyy386/MathInterviewVocab/tree/android-v1.2.0)。桌面 v1.2.0 与已有附件不变。

Debug／Release 各 107 项自动化测试通过，Release Lint 0 错误，APK 签名及全部词库／音频复核通过。实体手机／平板测试由用户报告全部通过并授权公开，未提供型号／系统版本，不冒充审查者真机实测。当前保留 3 项 P2 已知风险，详见 [发布审查](docs/REVIEW-v1.2.0.md)；后续开发交接见 [详细需求与设计](docs/REQUIREMENTS-AND-DESIGN.md)。

安装：核对下载地址与校验和，打开 APK，按系统正常提示为当前下载来源授权并确认；无需 Android Studio 或语音模型，不关闭系统安全防护。后续同包名、同签名覆盖升级保留学习记录，勿先卸载旧版。

## 布局与内容

按实际应用窗口宽度（不是设备型号）切换：小于 600 dp 底部导航与列表／详情单页；600–839 dp 左导航栏与单页；840 dp 起左导航栏与 320 dp 词汇列表／详情双栏。阅读列最大 820 dp，设置最大 560 dp。支持旋转、分屏、系统栏／键盘避让和系统大字体；验收要求见 [真机验收表](docs/DEVICE-ACCEPTANCE.md)。

直接复用仓库内 `Resources/terms.json` 及 `Windows/resources/audio*`。构建前核验 326 条词汇、130 条重点、2328 段音频的数量、大小和 SHA-256，再复制到生成目录；Android 不维护另一份音源。构建排除隐藏文件及网盘临时文件，随包保留项目 MIT、Kokoro Apache-2.0 与来源说明。

英美 IPA、完整词组、完整单词及收录词组选区可离线朗读，语速 0.75× / 1× / 1.25×。长按／拖动选择后再按“朗读所选”，不会因选择而自动播放。未收录片段提示缺失，不使用 Google、不联网补音、不回退系统 TTS。

## 独立工具链与构建

使用 JDK 17、Gradle Wrapper 8.13、AGP 8.13.2、Kotlin 2.3.20。Android Studio 可选，不需要安装，也不使用模拟器或 PD。

1. 准备项目专用 JDK 17 和 Android SDK（建议放在仓库外的本机缓存目录，不修改全局 Java）。从 [Android 官方工具页](https://developer.android.com/studio#command-tools) 阅读并亲自确认 SDK 许可；命令行出现许可时，也由使用者亲自阅读确认，脚本不自动接受。
2. 用 SDK Manager 安装 `platform-tools`、`platforms;android-36` 和 `build-tools;36.0.0`。不需要系统镜像或 emulator 包。
3. 设置本次构建环境。`scripts/with-env.sh` 默认使用本机缓存中的独立工具；其他路径通过 `MATH_ANDROID_JAVA_HOME`、`MATH_ANDROID_SDK_ROOT` 指定。脚本只修改子进程环境，不修改 shell 配置、系统 Java 或 Android Studio。

在本目录执行：

```sh
./scripts/with-env.sh ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --write-locks
```

纯逻辑测试无需 Android SDK，可先单独运行；它引用应用本身的核心、音频与更新策略源码，并不替代 Android 编译或真机验收：

```sh
./scripts/with-env.sh ./gradlew -p logic-tests test --write-locks
```

依赖版本明确固定，锁文件由实际依赖解析生成并提交。Wrapper 附带 Gradle 分发 SHA-256 校验。默认生成目录和 Gradle 缓存放在仓库外，避免同步工具污染成品；不要把私钥或学习记录放入项目目录。

正式构建使用仓库外的长期密钥配置：

```sh
export MATH_ANDROID_SIGNING_PROPERTIES='/private/path/signing.properties'
./scripts/with-env.sh ./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
python3 scripts/verify-apk.py /path/to/app-release.apk --sdk /path/to/android-sdk
```

没有有效签名配置时正式构建拒绝继续，不能把调试密钥包装成正式版。密钥创建工具 `scripts/create-signing-key.mjs` 要求两个独立的仓库外私有目录，不覆盖已有材料；密码不传入命令行，不输出私钥或密码，并实际验证备份证书。公开证书指纹见 [release-certificate.sha256](release-certificate.sha256)。同一硬盘上的本地备份不防硬盘故障，发布前应另存受控离线介质。

## 本地记录与隐私

学习记录 `progress.json` 和发音设置 `settings.json` 仅存应用私有目录，原子保存；失败明确提示，不以默认值覆盖损坏／无法读取的数据。字段与稳定词条编号沿用桌面契约，不隐式导入或合并其他设备的记录。

关闭自动云备份和设备迁移备份。不申请存储、麦克风或通知权限，不接入分析 SDK。正常同包名、同签名覆盖升级保留数据；卸载会由 Android 删除私有数据。不要为排错卸载或清空现有数据。

## 检查更新与安装

设置中手动查询本仓库 GitHub 最新正式 Release，无启动自动检查。只接受 `MathInterviewVocab-Android-universal-v版本.apk` 和独立 `SHA256SUMS-Android.txt`，缺附件时友好提示，不下载 Windows/macOS 文件。资源 URL 限官方 HTTPS 主机；不上传词汇、选区或学习数据。

下载到应用私有缓存，提供进度、取消及重试。校验大小、SHA-256、包名、实际签名证书、更高 versionCode、Release 与 APK 版本一致后才允许安装。用户手动点击，按系统“安装未知应用”和 PackageInstaller 确认；拒绝／取消／失败不报告安装成功。不静默安装，不要求关闭 Play Protect。

首发计划向现有 v1.2.0 Release **仅新增**：

- `MathInterviewVocab-Android-universal-v1.2.0.apk`
- `SHA256SUMS-Android.txt`

原 v1.2.0 标签、桌面 ZIP 与 `SHA256SUMS.txt` 不变。Android 源码另建 `android-v1.2.0` 标签并在 Release 明确链接；原 v1.2.0 自动生成源码包不会自动增加 Android。
