# 数学面试词汇 · MathInterviewVocab

面向数学专业面试的中英文词汇学习软件，提供词汇查阅、四选一检测、英美发音与 IPA、收藏和本地学习记录。内置 **326 条词汇、130 条重点词汇**，不需要账号或云同步。

## 下载

| 平台 | 当前应用版本 | 系统要求 | 下载 |
| --- | --- | --- | --- |
| Windows | 1.2.0 | Windows 10/11，64 位 Intel/AMD（x64） | [Windows 免安装 ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.2.0/MathInterviewVocab-Windows-x64-v1.2.0.zip) |
| macOS | 1.2.0 build 4 | macOS 14+，Apple Silicon / Intel Universal | [macOS ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.2.0/MathInterviewVocab-macOS-universal-v1.2.0.zip) |

[正式发布页](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.2.0) · [SHA-256 校验和](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.2.0/SHA256SUMS.txt) · [更新说明](docs/RELEASE-NOTES-v1.2.0.md)

请下载上述平台 ZIP，**不要把 GitHub 自动生成的 Source code 当作可运行软件**。两平台应用与最新 Release 统一为 **1.2.0**；历史 Release 保持不变。旧版没有检查更新入口，需要先手动下载 1.2.0。

### 安装与首次打开

- Windows：完整解压 ZIP，再双击 `数学面试词汇.exe`。保留 EXE 旁的 DLL、`resources` 等配套文件，不在压缩包内直接运行。无需安装 Node.js、Python、语音模型或浏览器。
- macOS：解压 ZIP，将 `数学面试词汇.app` 放到“应用程序”或其他本地目录，再打开。
- Mac `.app` 不宜放在网盘自动同步目录内运行，防止同步程序添加临时文件而破坏签名；ZIP 可以正常保存在同步目录。
- 当前 Mac 成品采用 ad-hoc 临时签名，没有 Developer ID 签名或 Apple 公证；Windows 成品未使用商业代码签名证书。系统可能显示无法验证开发者或未知发布者提示。请核对下载来源和校验和，按系统正常提示决定是否允许打开；不要关闭 Gatekeeper、SmartScreen 等全局安全防护。

## 功能

- 中文、英文及释义搜索；按科目、重点、收藏和错题组合筛选。
- 词汇详情和答题反馈均提供英美 IPA、完整词组及所选单词朗读；双击或拖动选择文字不会自动播放。
- 中译英、英译中、双向混合检测；重点、全部、指定科目、错题和收藏五种范围。
- 10 / 20 / 50 / 全部题量，可排除已掌握词汇；每题四个不同编号的选项。
- 答错自动进入错题，连续答对两次移出错题，三次标记已掌握；收藏与学习状态独立保存。
- 美式、英式默认口音及 0.75× / 1× / 1.25× 语速；独立设置窗口，修改立即生效。
- 顶部“词汇／检测／学习记录”导航，可折叠、拖动的词汇索引和浅色学术卡片排版。
- 设置中的“软件更新”：手动检查正式版、查看发布说明、下载并校验本平台 ZIP，再打开下载位置。

两平台沿用相同的内容和交互层级，但使用各自系统字体、原生菜单及确认对话框，不承诺跨系统逐像素相同。

## 发音与隐私

| 项目 | macOS | Windows |
| --- | --- | --- |
| 词库、IPA、检测、学习记录 | 离线 | 离线 |
| 发音来源 | 内置 Kokoro 预生成自然语音，2328 个音频片段 | 同一套 Kokoro 离线音频 |
| 发音联网 | 不联网，不首次下载，不使用 Google 音频或模型 | 不联网，不首次下载，不使用 Google 音频或模型 |
| 本地记录 | 仅保存于当前 Mac | 仅保存于当前 Windows 设备 |

两平台均使用 Kokoro-82M v1.0 预生成音频，美式为 `af_heart`，英式为 `bf_emma`。音频随软件提供，运行时不需要模型或网络，也不向 Google 或其他服务发送朗读文本。Mac 1.1.2 已移除旧版的 Google 在线请求及系统语音回退。

离线资源覆盖完整词条、完整单词及内置连续词组选区；不在索引中的任意字母片段会提示音频缺失，不会自动联网、切换系统声音或冒充另一口音。英美 IPA 和 0.75× / 1× / 1.25× 语速保持不变。

本项目没有账号、分析 SDK、远程学习数据库或自动同步。更多说明见 [Windows README](Windows/README.md) 和 [离线音源声明](Windows/resources/AUDIO-SOURCES.md)。

## 软件更新

打开 **设置 → 软件更新 → 检查更新**。软件只查询本仓库的 GitHub 最新正式 Release，不接收预发布版，也不会建议降级。发现新版本后，点击“下载更新”；软件核对对应平台的文件名、大小及 `SHA256SUMS.txt`，校验通过才保留最终 ZIP。下载可以取消，失败后可重试。

下载完成后点击“在文件夹中显示”，退出旧版，完整解压后用新版替换原程序。Windows 必须替换整套程序目录，不能只替换 EXE；macOS 替换 `.app`。学习记录和发音设置在用户目录中，正常替换程序不会清空它们。

**不会自动安装或执行下载文件，不会静默覆盖正在运行的应用。** 没有启动时后台检查、账号、令牌或学习数据上传。仅在你检查／下载更新时连接 GitHub 及其官方资源主机；无法联网时显示提示，不影响离线学习。校验后的 ZIP 会添加系统互联网来源标记（macOS quarantine／Windows Internet Zone），无法添加时不报告成功，并提示改用浏览器下载。SHA-256 校验用于检查下载完整性，不是独立发布签名，仍须核对项目来源与系统安全提示。

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

更新程序、移动解压目录不会自动清空记录。需要重置时，在设置中选择“清空本机学习记录”并确认；该操作保留检测和发音偏好。两平台默认各自保存，不会隐式导入或合并记录。

## 从源码构建

### macOS

需要 macOS 14+、支持 Swift 6 的 Xcode 16+。直接打开仓库中的 Xcode 工程，选择 `MathInterviewVocab` scheme 与 `My Mac` 后运行：

```sh
open MathInterviewVocab.xcodeproj
```

工程构建时将仓库内 `Windows/resources` 的同一套音频、索引和许可复制到 Mac 应用资源中，不重复保存另一份音频源文件。Mac 日常构建不需要 Node.js、Python、模型权重或重新生成语音。

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
bash Scripts/package_macos.sh release-artifacts/macOS-1.2.0
```

脚本输出 `.app`、macOS ZIP 及单平台校验文件，拒绝覆盖已有输出；应用使用 ad-hoc 签名，不因此获得 Apple 公证。GitHub Release 的 `SHA256SUMS.txt` 另包含两平台附件校验和。

### Windows

需要 Node.js 24+，在仓库根目录运行：

```sh
cd Windows
npm ci
npx install-electron
npm test
npm run build
npm start
npm run package:win
```

Windows 构建采用 Electron、React 和 TypeScript。`install-electron` 是当前 Electron 依赖提供的开发运行时安装命令。源码已纳入离线音频及清单，不需要重新下载模型或生成语音；首次安装开发依赖和运行时需要网络。打包生成 x64 免安装程序。详见 [Windows 构建说明](Windows/README.md)。

### 更新词表

审核源文件为仓库内的 `docs/vocabulary.md`。在仓库根目录运行：

```sh
python3 Scripts/generate_terms.py
```

脚本校验科目顺序、连续编号、326 条总量及 130 条重点后生成 `Resources/terms.json`。IPA 复核数据位于 `Scripts/term_phonetics.json`。只有修改朗读文本、需要刷新 IPA 草稿时才需安装 eSpeak NG 并运行 `Scripts/generate_phonetics.py`；应用运行和日常构建不依赖它。

修改词表后还需更新和核验两平台共用的离线音频，不能仅修改文本就宣称音频覆盖完整。

## 验证与反馈

此前 Windows 实机运行已由用户验证；该反馈不冒充 1.2.0 新增功能的 Windows 实机测试。项目包含两平台逻辑、离线资源和更新协议测试；自动化测试、macOS 上的运行检查与用户实机反馈分别说明，不代表所有系统版本、硬件、系统缩放或音频设备组合均已验证。具体版本的验收范围与复核命令见 [发布检查说明](docs/RELEASE-CHECKS.md)。

问题反馈请使用 [GitHub Issues](https://github.com/xyy386/MathInterviewVocab/issues)，附上平台、应用版本、重现步骤及必要截图。不要公开上传自己的 `progress.json`、账号信息或包含个人内容的完整日志。

## 许可证

项目原创代码使用 [MIT License](LICENSE)。第三方软件、语音资源及参考文章的权利不因本项目的 MIT 许可发生改变；保留其原许可、版权和来源声明。详见 [来源与许可边界](docs/SOURCES.md)、[Windows 第三方说明](Windows/THIRD-PARTY-NOTICES.txt) 和 [音源声明](Windows/resources/AUDIO-SOURCES.md)。
