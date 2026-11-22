# 数学面试词汇 · Windows 离线版

Windows 1.5.1（file build 1.5.1.8）面向 Windows 10/11 的 64 位 Intel/AMD（x64）电脑。它沿用 Mac 版的词汇、检测、学习记录、布局和设置层级，并使用独立的本地数据目录。

## 下载与使用

从 [发布页](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.5.1) 下载 [Windows 安装版 Setup（推荐）](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.1/MathInterviewVocab-Windows-x64-Setup-v1.5.1.exe)，正常退出旧版后按向导安装。也可下载 [便携 ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.1/MathInterviewVocab-Windows-x64-v1.5.1.zip)，完整解压后运行 `数学面试词汇.exe`；不要在 ZIP 内运行或单独移动 EXE。本轮不修改或替换 Android，历史 Release 保留。

不需要管理员权限，也不需要安装 Node.js、Python、浏览器、模型或运行时。GitHub 自动生成的 Source code 是开发源码，不是可直接运行的软件。

成品未使用商业代码签名证书，Windows 可能提示未知发布者。请核对官方发布页和 [SHA-256 校验和](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.5.1/SHA256SUMS.txt)，不要关闭系统安全防护。更新清单另有 Ed25519 签名，但这不等于 Authenticode 开发者身份认证。本版不提供 Windows 7/8、32 位或 ARM 原生构建。

## 功能与布局

- 326 条词汇、130 条重点，五科顺序与 Mac 相同；词条编号和数据格式保持一致。
- 中文、英文、释义搜索；科目与重点、收藏、错题组合筛选。
- 英美 IPA、完整词组及所选单词朗读；选中文字不会自动播放，右键支持复制与“朗读所选”。
- 中译英、英译中、混合检测，五种练习范围，10 / 20 / 50 / 全部题量，可排除已掌握词汇。
- 四个不同编号的选项与即时反馈；连续答对两次移出错题、三次标记已掌握，收藏状态独立。
- 默认美式／英式口音及 0.75× / 1× / 1.25× 语速；独立设置窗口即时保存并同步主窗。
- `Ctrl+1/2/3` 切换模块，`Ctrl+,` 打开设置；四方向键前后切词；检测时 `A–D`／`1–4` 作答，反馈后 `Enter`／`→`／`↓` 下一题。
- 设置中的“软件更新”可手动检查正式版本，下载并校验安装包，确认后正常退出、安装并重新启动，无需自己解压覆盖。

1.1.1 统一了顶部三段导航、单词汇侧栏、欢迎页、详情首卡、纵向四选项、反馈与学习记录。默认窗口 1220×780，最小 1000×680，词汇索引可折叠并在 320–460px 之间拖动；阅读列最大 820px。界面使用本机字体和自绘图标，不捆绑 Apple 字体或 SF Symbols，系统菜单、字形与确认对话框不承诺跨平台逐像素相同。

1.3.0 新增的快捷键按当前筛选及实际列表顺序移动，首尾不循环；侧栏折叠仍可切词，重新展开显示当前词条。输入、选词、菜单与侧栏调宽优先，长按不连续操作。检测不跳过未答题，也不能回退改答案。完整规则见 [桌面快捷键](../docs/KEYBOARD-SHORTCUTS.md)。

## 内置离线发音

1.5.1 修复词汇、释义、检测及学习记录的 LaTeX 显示：63 个内置 SVG 公式与正文混排，复制保留原始源码（如 `\(H_0\)`），超宽公式局部滚动。搜索支持 `H_0`／`H₀`／`H0`。选中公式不会误导航或把源码交给播放器；英文单词选择及发音不变。资源在开发阶段由 MathJax 4.1.3 生成，运行时不加载数学 CDN、远程字体或 MathJax。

本版包含 2328 个预生成 MP3 音频片段，覆盖 1164 份文本的美式和英式发音。音源为 Kokoro-82M v1.0，美式 `af_heart`、英式 `bf_emma`；两套音频不是同一个声线改名。

音频、音标和索引随包提供；运行时不需要模型、首次下载或网络。不会使用 Google 在线服务、Google 音频或所谓“离线 Google 模型”。Mac 自 1.1.2 起也内置同一套 Kokoro 音频，不再使用旧版 Google 在线增强和系统语音回退。

完整词组使用独立 `speechText`；资源还覆盖词条英文中的完整单词和内置连续词组选区。任意字母片段、跨数学符号或不在索引中的选区会提示缺少音频，不会自动联网或冒充另一口音。详细来源、固定 revision、哈希与许可证见 [AUDIO-SOURCES](resources/AUDIO-SOURCES.md)。

## 本地数据与兼容

Windows 数据位于 `%LOCALAPPDATA%\MathInterviewVocab`：学习记录为 `progress.json`，发音偏好为 `settings.json`。移动软件或更新解压位置不会自动清空记录；重置需在设置中确认，并保留检测和发音偏好。

记录保留 Mac 的字段名、稳定词条编号、UUID、ISO-8601 时间和枚举值，并兼容旧版检测偏好。两平台独立保存，没有账号、云同步或隐式合并。确需转移时，可在退出两端软件后手动复制同格式的 `progress.json`。

保存使用临时文件与原子替换；损坏记录不会被空数据悄悄覆盖。若无法保存，界面显示错误，不把失败显示为已保存。

## 软件更新与联网边界

1.4.0 改用当前用户 NSIS 安装版。**1.3.0 及更早版本仍需手动升级这一次**，旧客户端不能仅靠 GitHub 更新补上安装逻辑。详见 [桌面安装与更新](../docs/DESKTOP-UPDATES.md)。

在 **设置 → 软件更新** 查看当前版本，主动检查本项目对应平台的 GitHub 正式版。只提示更高版本，不接收预发布版或降级。确认下载后，主进程验证固定 Ed25519 公钥签名清单、x64 Setup 大小与 SHA-256，再提供“安装并重启”；取消或校验失败不执行下载文件。

1.5.0 改读独立签名索引 `updates/windows/latest.json{,.sig}`，固定平台、版本、构建号和三个允许附件的大小及摘要，不依赖仓库全局 Latest。桌面独立正式发布，Android 继续保留 Latest。旧版清单仍提供给 1.4.0，允许按原有更新流程升级。

线路可选默认智能多线加速、镜像优先或纯 GitHub 直连。最多三条元数据线路竞速，首选延迟 1.2 秒（镜像优先 400 毫秒）后对冲；大安装包只用一个连接，断流或持续低速则跨线路 Range 续传，重新验证完整 SHA-256。CDN 只读取签名小索引；镜像不能改变附件身份。

下载期间可切页或关闭设置，顶部进度条继续显示。暂停、断网和退出保留断点，重开只读取本地缓存，点击继续才联网。缓存位于 `%LOCALAPPDATA%\MathInterviewVocab\UpdatesCache`，单包上限 500 MiB；更换任务或清除缓存需确认，不影响记录。暂停后可以更换线路，工作期间禁用线路选择。

确认安装后，先检查记录和设置是否安全保存，再启动安装器。安装器先打开旧进程句柄并回执，应用才正常退出；安装器等待其结束后替换并重新启动，不强制终止程序。读取／保存错误、签名错误、篡改文件、交接失败或超时均停止操作，不把退出应用当作安装成功。

学习、发音及渲染进程继续离线。没有启动后台检查或未经确认的安装，不使用账号、令牌或本机 GitHub 登录凭据，不上传学习记录。Windows Internet Zone 标记和系统防护不被主动关闭；首次运行仍可能出现系统安全提示。旧便携目录不自动删除，覆盖更新不清空 `%LOCALAPPDATA%\MathInterviewVocab`。安装失败可重新运行官方包修复，不承诺断电／部分复制故障的自动回滚。

## 开发与打包

需要 Node.js 24+。在本目录执行：

```sh
npm ci
npx install-electron
npm test
npm run build
npm run test:keyboard-ui
npm start
npm run package:win
```

依赖固定于 `package-lock.json`。`install-electron` 是当前 Electron 依赖提供的开发运行时安装命令。首次安装开发依赖和运行时需要网络；离线音频已经纳入源码，日常构建不需要重新制作语音。`package:win` 运行测试与构建后生成 Windows x64 便携 ZIP 与 NSIS Setup；本地构建输出位于 `.out/`，不会提交到源码历史。开发主机为 macOS 时可交叉打包，不需要 Windows SDK 或额外虚拟化环境，但交叉构建不代替原生 Windows 安装验证。

更新界面事件验收运行 `npm run test:update-ui`。原生安装／升级验收工作流见 `.github/workflows/windows-installer-smoke.yml`；测试包不公开为正式版本，CI 不读取正式签名私钥。签名清单由 `scripts/sign-update.mjs` 离线生成，私钥路径只用于本机签名，不随成品打包。

`test:keyboard-ui` 使用隔离 Electron 渲染窗口派发实际 DOM 键盘事件，学习数据只在测试内存中保存。隐藏窗口显式设置焦点／可见性测试条件，同时验证条件不满足时不触发快捷键；它不是 Windows 实机、原生系统菜单或物理键盘验收。

主进程通过受限 IPC 处理持久化及手动更新，渲染进程启用隔离和沙箱，不直接访问文件系统，HTTP/HTTPS 仍被阻止。仅主进程更新通道可访问白名单内的 GitHub、三家代理及两家索引 CDN；选择直连会禁用第三方节点。不包含分析、云同步或退出后下载服务。离线音频不完整时，发布打包会失败。

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

1.5.0 的 151 项自动化测试、类型检查、生产构建及 9 组 Electron 键盘事件、2 组更新界面流程验收通过；页面事件在 macOS 隔离隐藏窗口执行，不是 Windows 硬件验收。GitHub 官方 Windows runner 另验证原生首装向导、拒绝异常安装、覆盖升级、自动重启、升级与卸载后的数据保留。未使用 PD，不将托管 runner 或此前用户反馈冒充本轮实体 Windows 硬件验收，不宣称所有 Windows 系统、硬件、缩放和扬声器组合均已验证。详见 [快捷键验收与边界](../docs/KEYBOARD-SHORTCUTS.md) 和 [发布检查说明](../docs/RELEASE-CHECKS.md)。

项目原创代码使用 [MIT](../LICENSE)；第三方组件和语音资源保留原许可。见 [第三方说明](THIRD-PARTY-NOTICES.txt)、[音源声明](resources/AUDIO-SOURCES.md) 和 [来源说明](../docs/SOURCES.md)。
