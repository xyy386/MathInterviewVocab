# 数学面试词汇 · MathInterviewVocab

面向数学专业面试的中英文词汇学习软件，提供词汇查阅、四选一检测、英美发音与 IPA、收藏和本地学习记录。内置 **326 条词汇、130 条重点词汇**，不需要账号或云同步。

## 下载

| 平台 | 当前应用版本 | 系统要求 | 下载 |
| --- | --- | --- | --- |
| Windows | 1.1.1 | Windows 10/11，64 位 Intel/AMD（x64） | [Windows 免安装 ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.1.1/MathInterviewVocab-Windows-x64-v1.1.1.zip) |
| macOS | 1.1 build 2 | macOS 14+，Apple Silicon / Intel Universal | [macOS ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.1.1/MathInterviewVocab-macOS-universal-v1.1.zip) |

[正式发布页](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.1.1) · [SHA-256 校验和](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.1.1/SHA256SUMS.txt) · [更新说明](docs/RELEASE-NOTES-v1.1.1.md)

请下载上述平台 ZIP，**不要把 GitHub 自动生成的 Source code 当作可运行软件**。仓库标签 `v1.1.1` 汇集两平台的当前成品，不代表 Mac 应用版本已改为 1.1.1。

### 安装与首次打开

- Windows：完整解压 ZIP，再双击 `数学面试词汇.exe`。保留 EXE 旁的 DLL、`resources` 等配套文件，不在压缩包内直接运行。无需安装 Node.js、Python、语音模型或浏览器。
- macOS：解压 ZIP，将 `数学面试词汇.app` 放到“应用程序”或其他本地目录，再打开。
- 当前 Mac 成品采用 ad-hoc 临时签名，没有 Developer ID 签名或 Apple 公证；Windows 成品未使用商业代码签名证书。系统可能显示无法验证开发者或未知发布者提示。请核对下载来源和校验和，按系统正常提示决定是否允许打开；不要关闭 Gatekeeper、SmartScreen 等全局安全防护。

## 功能

- 中文、英文及释义搜索；按科目、重点、收藏和错题组合筛选。
- 词汇详情和答题反馈均提供英美 IPA、完整词组及所选单词朗读；双击或拖动选择文字不会自动播放。
- 中译英、英译中、双向混合检测；重点、全部、指定科目、错题和收藏五种范围。
- 10 / 20 / 50 / 全部题量，可排除已掌握词汇；每题四个不同编号的选项。
- 答错自动进入错题，连续答对两次移出错题，三次标记已掌握；收藏与学习状态独立保存。
- 美式、英式默认口音及 0.75× / 1× / 1.25× 语速；独立设置窗口，修改立即生效。
- 顶部“词汇／检测／学习记录”导航，可折叠、拖动的词汇索引和浅色学术卡片排版。

两平台沿用相同的内容和交互层级，但使用各自系统字体、原生菜单及确认对话框，不承诺跨系统逐像素相同。

## 发音与隐私

| 项目 | macOS | Windows |
| --- | --- | --- |
| 词库、IPA、检测、学习记录 | 离线 | 离线 |
| 发音来源 | Google 在线增强；失败后使用同口音 Mac 系统语音 | 内置 Kokoro 预生成自然语音，2328 个音频片段 |
| 发音联网 | 主动朗读时会向 Google 发送对应英文 | 不联网，不首次下载，不使用 Google 音频或模型 |
| 本地记录 | 仅保存于当前 Mac | 仅保存于当前 Windows 设备 |

Google 翻译网页朗读不是稳定的公开第三方 TTS API，可能受网络、限流或服务调整影响；Mac 版保留系统语音回退。Windows 的离线资源覆盖完整词条、完整单词及内置连续词组选区；不在索引中的任意字母片段会提示音频缺失，不会自动联网。

本项目没有账号、分析 SDK、远程学习数据库或自动同步。更多说明见 [Windows README](Windows/README.md) 和 [离线音源声明](Windows/resources/AUDIO-SOURCES.md)。

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

仅在修改 `project.yml` 或增加工程文件后需要 XcodeGen：

```sh
xcodegen generate
```

测试命令：

```sh
xcodebuild -project MathInterviewVocab.xcodeproj \
  -scheme MathInterviewVocab -destination 'platform=macOS' test
```

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

修改词表后还需更新和核验 Windows 离线音频，不能仅修改文本就宣称音频覆盖完整。

## 验证与反馈

Windows 实机运行已由用户验证。项目包含 Mac 25 项、Windows 68 项自动化测试；用户实机反馈与自动化检查属于不同证据，不代表所有 Windows 版本、硬件、系统缩放或音频设备组合均已验证。验收范围与复核命令见 [发布检查说明](docs/RELEASE-CHECKS.md)。

问题反馈请使用 [GitHub Issues](https://github.com/xyy386/MathInterviewVocab/issues)，附上平台、应用版本、重现步骤及必要截图。不要公开上传自己的 `progress.json`、账号信息或包含个人内容的完整日志。

## 许可证

项目原创代码使用 [MIT License](LICENSE)。第三方软件、语音资源及参考文章的权利不因本项目的 MIT 许可发生改变；保留其原许可、版权和来源声明。详见 [来源与许可边界](docs/SOURCES.md)、[Windows 第三方说明](Windows/THIRD-PARTY-NOTICES.txt) 和 [音源声明](Windows/resources/AUDIO-SOURCES.md)。
