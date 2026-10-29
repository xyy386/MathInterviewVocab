# 数学面试词汇 · Windows 离线版

Windows 1.1.1 面向 Windows 10/11 的 64 位 Intel/AMD（x64）电脑。它沿用 Mac 版的词汇、检测、学习记录、布局和设置层级，并使用独立的本地数据目录。

## 下载与使用

从 [正式发布页](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.1.1) 下载 [Windows 免安装 ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.1.1/MathInterviewVocab-Windows-x64-v1.1.1.zip)，完整解压后运行 `数学面试词汇.exe`。不要在 ZIP 内运行，不要单独移动 EXE；旁边的 DLL、`resources` 和其他配套文件必须保留。

不需要管理员权限，也不需要安装 Node.js、Python、浏览器、模型或运行时。GitHub 自动生成的 Source code 是开发源码，不是可直接运行的软件。

成品未使用商业代码签名证书，Windows 可能提示未知发布者。请核对官方发布页和 [SHA-256 校验和](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.1.1/SHA256SUMS.txt)，不要关闭系统安全防护。本版不提供 Windows 7/8、32 位或 ARM 原生构建。

## 功能与布局

- 326 条词汇、130 条重点，五科顺序与 Mac 相同；词条编号和数据格式保持一致。
- 中文、英文、释义搜索；科目与重点、收藏、错题组合筛选。
- 英美 IPA、完整词组及所选单词朗读；选中文字不会自动播放，右键支持复制与“朗读所选”。
- 中译英、英译中、混合检测，五种练习范围，10 / 20 / 50 / 全部题量，可排除已掌握词汇。
- 四个不同编号的选项与即时反馈；连续答对两次移出错题、三次标记已掌握，收藏状态独立。
- 默认美式／英式口音及 0.75× / 1× / 1.25× 语速；独立设置窗口即时保存并同步主窗。
- `Ctrl+1/2/3` 切换模块，`Ctrl+,` 打开设置；检测时 `1/2/3/4` 作答，反馈后 `Enter` 下一题。

1.1.1 统一了顶部三段导航、单词汇侧栏、欢迎页、详情首卡、纵向四选项、反馈与学习记录。默认窗口 1220×780，最小 1000×680，词汇索引可折叠并在 320–460px 之间拖动；阅读列最大 820px。界面使用本机字体和自绘图标，不捆绑 Apple 字体或 SF Symbols，系统菜单、字形与确认对话框不承诺跨平台逐像素相同。

## 内置离线发音

本版包含 2328 个预生成 MP3 音频片段，覆盖 1164 份文本的美式和英式发音。音源为 Kokoro-82M v1.0，美式 `af_heart`、英式 `bf_emma`；两套音频不是同一个声线改名。

音频、音标和索引随包提供；运行时不需要模型、首次下载或网络。不会使用 Google 在线服务、Google 音频或所谓“离线 Google 模型”。Mac 当前成品采用 Google 在线增强和系统语音回退，不能把两平台的音源混为一谈。

完整词组使用独立 `speechText`；资源还覆盖词条英文中的完整单词和内置连续词组选区。任意字母片段、跨数学符号或不在索引中的选区会提示缺少音频，不会自动联网或冒充另一口音。详细来源、固定 revision、哈希与许可证见 [AUDIO-SOURCES](resources/AUDIO-SOURCES.md)。

## 本地数据与兼容

Windows 数据位于 `%LOCALAPPDATA%\MathInterviewVocab`：学习记录为 `progress.json`，发音偏好为 `settings.json`。移动软件或更新解压位置不会自动清空记录；重置需在设置中确认，并保留检测和发音偏好。

记录保留 Mac 的字段名、稳定词条编号、UUID、ISO-8601 时间和枚举值，并兼容旧版检测偏好。两平台独立保存，没有账号、云同步或隐式合并。确需转移时，可在退出两端软件后手动复制同格式的 `progress.json`。

保存使用临时文件与原子替换；损坏记录不会被空数据悄悄覆盖。若无法保存，界面显示错误，不把失败显示为已保存。

## 开发与打包

需要 Node.js 24+。在本目录执行：

```sh
npm ci
npx install-electron
npm test
npm run build
npm start
npm run package:win
```

依赖固定于 `package-lock.json`。`install-electron` 是当前 Electron 依赖提供的开发运行时安装命令。首次安装开发依赖和运行时需要网络；离线音频已经纳入源码，日常构建不需要重新制作语音。`package:win` 运行测试与构建后生成 Windows x64 EXE 和 ZIP；本地构建输出位于 `.out/`，不会提交到源码历史。开发主机为 macOS 时可交叉打包，不需要 Windows SDK 或额外虚拟化环境。

主进程通过受限 IPC 处理持久化，渲染进程启用隔离和沙箱，不直接访问文件系统。运行时阻止 HTTP/HTTPS 请求，不包含分析、云同步或自动更新服务。离线音频不完整时，发布打包会失败。

仅在更新词表后需要重新制作音频时，在独立 Python 环境安装 `scripts/requirements-audio.txt`，然后执行：

```sh
node scripts/prepare-audio.mjs --inventory
python scripts/generate-audio.py --device cpu --threads 4
node scripts/prepare-audio.mjs --import .out/audio-generated
node scripts/prepare-audio.mjs --verify --require-complete
```

生成阶段固定模型及声线 revision，并验证哈希；模型权重和构建用 Python 环境不会放入用户成品。

开发预览使用独立数据目录；`MATH_VOCAB_TEST_DATA_DIR` 只在未打包的开发进程生效，正式包不会接受该开发覆盖项。

## 验证与许可

Windows 实机运行已由用户验证。项目包含 68 项自动化测试，覆盖词汇／检测逻辑、持久化、选区清理、音频完整性及界面结构契约。界面还在 macOS 上运行同一 Electron 代码进行流程检查；这些检查与 Windows 实机反馈分别记录，不宣称所有 Windows 系统、硬件、缩放和扬声器组合均已验证。详见 [发布检查说明](../docs/RELEASE-CHECKS.md)。

项目原创代码使用 [MIT](../LICENSE)；第三方组件和语音资源保留原许可。见 [第三方说明](THIRD-PARTY-NOTICES.txt)、[音源声明](resources/AUDIO-SOURCES.md) 和 [来源说明](../docs/SOURCES.md)。
