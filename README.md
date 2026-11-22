# 数学面试词汇 · MathInterviewVocab

面向数学专业面试的中英文词汇学习软件，提供词汇查阅、四选一检测、英美发音与 IPA、收藏和本地学习记录。内置 **326 条词汇、130 条重点词汇**，不需要账号或云同步。

## 下载

[历史版本与归档索引](docs/VERSION-ARCHIVE.md)提供源码标签、历史下载链接、文件大小与 SHA-256；可重建缓存不纳入版本历史。

| 平台 | 当前应用版本 | 系统要求 | 下载 |
| --- | --- | --- | --- |
| Windows | 1.5.1 / file build 1.5.1.8 | Windows 10/11，64 位 Intel/AMD（x64） | [安装版 Setup（推荐）](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.1/MathInterviewVocab-Windows-x64-Setup-v1.5.1.exe) · [便携 ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.1/MathInterviewVocab-Windows-x64-v1.5.1.zip) |
| macOS | 1.5.1 build 8 | macOS 14+，Apple Silicon / Intel Universal | [macOS ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.1/MathInterviewVocab-macOS-universal-v1.5.1.zip) |
| Android | 1.3.5 / code 7 | Android 10+，手机／平板，Universal APK | [Android APK](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.3.5/MathInterviewVocab-Android-universal-v1.3.5.apk) |

[桌面 1.5.1 发布页](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.5.1) · [桌面版 SHA-256 校验和](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.1/SHA256SUMS.txt) · [桌面更新说明](docs/RELEASE-NOTES-v1.5.1.md) · [Android 1.3.5 发布页](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.3.5) · [Android 独立校验和](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.3.5/SHA256SUMS-Android.txt) · [Android 更新说明](docs/RELEASE-NOTES-v1.3.5.md)

Android 当前版本为 **1.3.5／code 7**（新增多线路对冲竞速检查与三大加速镜像/CDN 回退、HTTP Range 跨线路断点续传、低速自动切换线路、设置页线路选择与实时网速显示，以及主界面非侵入式后台更新进度条），采用同一套词库与 Kokoro 离线音源，按窗口宽度适配手机、平板和分屏。[Android 工程／安装说明](Android/README.md) · [详细需求与设计](Android/docs/REQUIREMENTS-AND-DESIGN.md) · [审查与已知问题](Android/docs/REVIEW-v1.2.0.md)。历史标签与附件保留不变；Android 源码使用独立标签 [android-v1.3.5](https://github.com/xyy386/MathInterviewVocab/tree/android-v1.3.5)。

请下载对应平台 Setup／ZIP／APK，**不要把 GitHub 自动生成的 Source code 当作可运行软件**。桌面版为 **1.5.1**，Android 为 **1.3.5**。1.3.0 及更早的桌面版必须手动安装升级这一次，之后可在软件中确认安装并重启。

### 安装与首次打开

- Windows：推荐运行 Setup，正常退出旧便携版后按向导安装到当前用户目录，无需管理员权限。也可完整解压便携 ZIP，再双击 `数学面试词汇.exe`；保留 EXE 旁的 DLL、`resources` 等文件，不在压缩包内运行。无需安装 Node.js、Python、语音模型或浏览器。
- macOS：解压 ZIP，将 `数学面试词汇.app` 放到“应用程序”或其他本地目录，再打开。
- Android：核对 APK 来源及 Android 独立校验和，打开 APK，按系统正常提示允许当前来源安装并确认。无需 Android Studio、JDK 或语音模型；后续同签名覆盖升级保留记录，不要先卸载。不要关闭 Play Protect 等全局安全防护。
- Mac `.app` 不宜放在网盘自动同步目录内运行，防止同步程序添加临时文件而破坏签名；ZIP 可以正常保存在同步目录。
- 当前 Mac 成品采用 ad-hoc 临时签名，没有 Developer ID 签名或 Apple 公证；Windows 成品未使用商业代码签名证书。系统可能显示无法验证开发者或未知发布者提示。请核对下载来源和校验和，按系统正常提示决定是否允许打开；不要关闭 Gatekeeper、SmartScreen 等全局安全防护。

## 功能

- 中文、英文及释义搜索；按科目、重点、收藏和错题组合筛选。
- 词汇详情和答题反馈均提供英美 IPA、完整词组及所选单词朗读；桌面双击／拖动、Android 长按／拖动选择，不会因选字自动播放。
- 中译英、英译中、双向混合检测；重点、全部、指定科目、错题和收藏五种范围。
- 10 / 20 / 50 / 全部题量，可排除已掌握词汇；每题四个不同编号的选项。
- 答错自动进入错题，连续答对两次移出错题，三次标记已掌握；收藏与学习状态独立保存。
- 美式、英式默认口音及 0.75× / 1× / 1.25× 语速；独立设置窗口／页面。
- “词汇／检测／学习记录”导航与浅色学术卡片；桌面可折叠词汇索引，Android 按窗口切换底部／左侧导航及单／双栏。
- 设置中的“软件更新”：手动检查正式版、查看说明并验证本平台附件；桌面确认后安装并重新启动，Android 经系统授权与确认安装 APK。

三平台沿用相同的内容和交互层级，但使用各自系统字体、原生菜单及确认对话框，不承诺跨系统逐像素相同。

### 桌面公式显示（1.5.1）

词汇、释义、检测及学习记录中的公式使用内置矢量资源显示，涵盖 63 个不同表达式。显示 H₀，复制仍为 `\(H_0\)`；支持公式与中英文混合复制，搜索兼容 `H_0`／`H₀`／`H0`。超宽公式局部横向滚动，不缩小正文。普通英文选词朗读不变，公式源码不会送入播放器；资源异常会显示提示并保留源码复制。

MathJax 4.1.3 只在资源生成时使用，不作为应用运行时依赖；无需联网、数学 CDN 或远程字体。不是完整 LaTeX 文档编辑器，本轮不改变 Android 的显示实现。[资源与字体许可](Resources/Math/SOURCES.md)。

### 桌面快捷键（macOS／Windows 1.3.0）

macOS／Windows 1.3.0 支持 `←`／`↑` 上一个词条、`→`／`↓` 下一个词条；按当前筛选结果移动，首尾不循环，侧栏收起也可使用。检测可用 `A–D` 或 `1–4` 提交答案，答题后用 `Enter`／`→`／`↓` 继续。输入、选词、菜单和侧栏调宽优先，长按不会连续切换。详细规则及验证边界见 [桌面快捷键](docs/KEYBOARD-SHORTCUTS.md)。

## 发音与隐私

| 项目 | macOS | Windows | Android |
| --- | --- | --- | --- |
| 词库、IPA、检测、学习记录 | 离线 | 离线 | 离线 |
| 发音来源 | 内置 Kokoro，2328 个片段 | 同一套离线音频 | 同一套离线音频 |
| 发音联网 | 不联网，不使用 Google 音频或模型 | 不联网，不使用 Google 音频或模型 | 不联网，不使用 Google 音频或模型 |
| 本地记录 | 当前 Mac | 当前 Windows 设备 | 当前设备应用私有目录 |

三平台均使用 Kokoro-82M v1.0 预生成音频，美式为 `af_heart`，英式为 `bf_emma`。音频随软件提供，运行时不需要模型或网络，也不向 Google 或其他服务发送朗读文本。Mac 1.1.2 已移除旧版的 Google 在线请求及系统语音回退。

离线资源覆盖完整词条、完整单词及内置连续词组选区；不在索引中的任意字母片段会提示音频缺失，不会自动联网、切换系统声音或冒充另一口音。英美 IPA 和 0.75× / 1× / 1.25× 语速保持不变。

本项目没有账号、分析 SDK、远程学习数据库或自动同步。更多说明见 [Windows README](Windows/README.md) 和 [离线音源声明](Windows/resources/AUDIO-SOURCES.md)。

## 软件更新

**1.4.0 增加安装与重启能力。** 1.3.0 及更早版本只有 ZIP 下载功能，必须先手动升级一次，不能仅靠修改 GitHub 给旧客户端补上安装器。详见 [桌面安装与更新](docs/DESKTOP-UPDATES.md)。

打开 **设置 → 软件更新 → 检查更新**，只接受本仓库对应平台的正式版本，不接收预发布版，不建议降级。Mac 使用 Sparkle 原生窗口，验证签名更新源和归档，确认后安装并重启；设置与主窗口的“查看”可唤起已有更新窗口。Windows 下载 Setup，验证固定 Ed25519 公钥签名清单、文件大小及 SHA-256，再提供“安装并重启”。下载可取消，失败可重试。

桌面与 Android 分平台发布：桌面 1.5.1 是正式版，仓库 Latest 保留给 Android 1.3.5。两平台使用独立签名更新源，不会把仅有 Android APK 的版本误当作桌面更新；1.4.0 的 Windows 仍通过正式发布列表查找本平台安装器。

**1.5.0 增加多线路加速、持久断点和主窗口进度条。** 默认智能多线加速，也可选择镜像优先或纯 GitHub 直连；大安装包单线路传输，断流或持续低速时换线续传。暂停、断网或退出保留断点，重开后只读取本地缓存，下次手动继续；关闭设置或切页不停止下载，没有退出后常驻服务。设置可单独确认清除更新缓存，不影响学习记录。

桌面 1.5.0 使用两平台独立签名索引并保留 Android Latest；Mac 继续使用原生 Sparkle 确认安装，Windows 继续使用 NSIS。保留旧更新源和清单供 1.4.0 升级，新的加速能力从安装 1.5.0 后生效。

确认安装后，软件检查学习记录是否安全保存，正常退出，由安装器替换程序并重新打开，不需要自己解压覆盖。Windows 安装器先持有旧进程句柄再等待退出，不强制终止其他程序。保存或交接失败则停止操作，不清空记录；安装后的磁盘故障等场景不承诺自动回滚。旧便携目录不会自动删除。

Android 使用 `SHA256SUMS-Android.txt`，另核对 APK 包名、实际签名证书、较高 versionCode 与 Release 版本；支持 GitHub 直连与三大加速镜像/CDN 多线择优、HTTP Range 断点续传和低速自动切线；用户点击安装后通过系统来源授权与 PackageInstaller 确认。已安装旧版的 Android 设备可在应用内检查并升级至 1.3.5（versionCode 7），或从 1.3.5 发布页下载 APK 直接覆盖安装。历史审查记录见 [Android 审查记录](Android/docs/REVIEW-v1.2.0.md)。

**不未经确认安装，也不在启动时后台检查。** 没有账号、令牌或学习数据上传，仅主动检查／下载时连接更新资源。智能或镜像模式会请求第三方加速节点（CDN 只读小索引），可切换 GitHub 直连以禁用这些节点；不发送学习记录和朗读文本，断网不影响学习。Windows 保留 Internet Zone 标记，Mac 由 Sparkle 的已验证安装流程处理；Android 由系统安装器确认。更新签名和 SHA-256 不等于商业代码签名或 Apple 公证，不能代替系统对开发者身份的判断，不关闭系统安全防护。

## 词汇内容

| 科目 | 数量 |
| --- | ---: |
| 常用数学学科名称 | 11 |
| 数学分析 | 163 |
| 高等代数 | 110 |
| 概率论 | 27 |
| 数理统计 | 15 |
| 合计 | 326 |

每条词汇包含中文、规范英文、释义、科目、重点标记、朗读文本及美式／英式 IPA。审核文本见 [词汇汇总](docs/vocabulary.md)，内容与第三方来源说明见 [SOURCES](docs/SOURCES.md)。

## 本地数据

- macOS：`~/Library/Application Support/MathInterviewVocab/progress.json`；发音偏好存于应用的 `UserDefaults`。
- Windows：`%LOCALAPPDATA%\MathInterviewVocab\progress.json`，发音偏好为同目录 `settings.json`。
- Android：应用私有 `filesDir` 下的 `progress.json`／`settings.json`，关闭自动云备份与设备迁移备份；正常覆盖升级保留，卸载会删除。

更新程序、移动桌面解压目录不会自动清空记录。设置中的学习记录重置须确认，并保留检测和发音偏好。三平台各自保存，不隐式导入或合并记录。

## 从源码构建

### macOS

需要 macOS 14+、支持 Swift 6 的 Xcode 16+。直接打开仓库中的 Xcode 工程，选择 `MathInterviewVocab` scheme 与 `My Mac` 后运行：

```sh
open MathInterviewVocab.xcodeproj
```

工程构建时将仓库内 `Windows/resources` 的同一套音频、索引和许可复制到 Mac 应用资源中，不重复保存另一份音频源文件。首次构建需要联网恢复锁定的 Sparkle 2.10.1 二进制包；应用运行、学习和发音不依赖这一步联网。Mac 日常构建不需要 Node.js、Python、模型权重或重新生成语音。

仅在修改 `project.yml` 或增加工程文件后需要 XcodeGen：

```sh
xcodegen generate
```

测试命令：

```sh
xcodebuild -project MathInterviewVocab.xcodeproj \
  -scheme MathInterviewVocab -destination 'platform=macOS' test
```

构建并封装 Universal Release：

```sh
bash Scripts/package_macos.sh release-artifacts/macos-v1.5.1
```

脚本输出 `.app`、仅含应用的 macOS ZIP 及单平台校验文件，拒绝覆盖已有输出。许可和使用说明在应用的 `Contents/Resources/Notices`；保留完整 Sparkle 框架及原签名后，对宿主 ad-hoc 签名，不因此获得 Apple 公证。GitHub Release 的 `SHA256SUMS.txt` 包含两平台成品及签名元数据校验和。

### Windows

需要 Node.js 24+，在仓库根目录运行：

```sh
cd Windows
npm ci
npx install-electron
npm test
npm run build
npm run test:keyboard-ui
npm start
npm run package:win
```

Windows 构建采用 Electron、React 和 TypeScript。`install-electron` 是当前 Electron 依赖提供的开发运行时安装命令。源码已纳入离线音频及清单，不需要重新下载模型或生成语音；首次安装开发依赖和运行时需要网络。打包生成 x64 便携 ZIP 与当前用户 NSIS Setup。详见 [Windows 构建说明](Windows/README.md)。

### Android

原生 Kotlin／Compose，独立 JDK 17、Android SDK API 36、Gradle Wrapper；不必安装 Android Studio。构建、签名、安装与本地数据说明见 [Android README](Android/README.md)。后续 Android 功能由项目所有者自行开发，需求／设计与验收合同见 [交接文档](Android/docs/REQUIREMENTS-AND-DESIGN.md)。

### 更新词表

审核源文件为仓库内的 `docs/vocabulary.md`。在仓库根目录运行：

```sh
python3 Scripts/generate_terms.py
```

脚本校验科目顺序、连续编号、326 条总量及 130 条重点后生成 `Resources/terms.json`。IPA 复核数据位于 `Scripts/term_phonetics.json`。只有修改朗读文本、需要刷新 IPA 草稿时才需安装 eSpeak NG 并运行 `Scripts/generate_phonetics.py`；应用运行和日常构建不依赖它。

修改词表后还需更新和核验三平台共用的离线音频，不能仅修改文本就宣称音频覆盖完整。

公式资源由 `Scripts/generate_math_assets.mjs` 从显式 `\(...\)`／`\[...\]` 生成，失败会报告词条编号与字段。通常直接使用已提交资源；核验运行 `node Scripts/generate_math_assets.mjs --verify`。只有修改公式时，才需先在 `Windows` 安装锁定开发依赖，并设置独立 `MATH_VOCAB_VALIDATION_DIRECTORY` 后运行生成脚本；不修改原词库或音频。

## 验证与反馈

桌面 1.5.0：macOS 96 项逻辑测试／201 次执行、Windows 151 项测试、类型检查及生产构建通过。9 组 Electron 键盘事件和 2 组更新界面流程在 macOS 隔离窗口运行，不是 Windows 硬件测试。另在 GitHub 官方 Windows runner 验证原生首装、覆盖升级、自动重启和数据保留；不使用 PD，不将托管 runner 称为实体设备，不宣称所有系统版本、硬件、缩放或音频设备均已验证。Mac 已实际验证取消／暂停、退出重开不联网、断点续传、拒绝错误签名源、替换与自动重启，初载的 `NSTableView` 警告仍保留。详细证据与边界见 [发布检查说明](docs/RELEASE-CHECKS.md) 和 [快捷键验收](docs/KEYBOARD-SHORTCUTS.md)。

Android 1.2.0：Debug／Release 各 107 项自动化测试通过，Release Lint 为 0 错误；APK、签名、326／130 词库及 2328 段音频完成独立检查。实体手机／平板测试由用户报告全部通过并授权公开，未提供型号／系统版本，不冒充审查者真机实测；3 项 P2 已知问题与测试边界见 [完整审查记录](Android/docs/REVIEW-v1.2.0.md)。

问题反馈请使用 [GitHub Issues](https://github.com/xyy386/MathInterviewVocab/issues)，附上平台、应用版本、重现步骤及必要截图。不要公开上传自己的 `progress.json`、账号信息或包含个人内容的完整日志。

## 许可证

项目原创代码使用 [MIT License](LICENSE)。第三方软件、语音资源及参考文章的权利不因本项目的 MIT 许可发生改变；保留其原许可、版权和来源声明。详见 [来源与许可边界](docs/SOURCES.md)、[Windows 第三方说明](Windows/THIRD-PARTY-NOTICES.txt) 和 [音源声明](Windows/resources/AUDIO-SOURCES.md)。

Android 的依赖及随包声明见 [Android 第三方许可](Android/THIRD-PARTY-NOTICES.md)。
