# 发布检查说明

## Android 1.3.6 离线公式验收

2026-11-22：Android `1.3.6`（`versionCode 8`）。仅同步离线 LaTeX 矢量公式渲染、原始 LaTeX 复制与公式别名检索能力至 Android；桌面 [`v1.5.1`](VERSION-ARCHIVE.md#v151-桌面附件)、既有历史 Release、326／130 规范词库、2328 段离线音频及本地学习记录格式保持不变。

- `logic-tests` 全套 100 项纯 JVM 测试全部通过（0 失败、0 跳过），含新增 7 项 [`MathTextTest`](../Android/app/src/test/java/com/xingyiyao/mathinterviewvocab/core/MathTextTest.kt)：验证 `\(...\)`／`\[...\]` 分段、普通下划线不被误识、`H_0`／`H₀`／`H0` 检索命中、`Resources/Math/manifest.json`（63 个 SVG 均核对 `svgSHA256` 与 `termsSHA256`，重点覆盖含未转义 `<` 的 `data-latex="f(a)f(b)<0"`）、`Tests/Fixtures/Math/manifest.json`（6 个嵌套分式含 `<rect>`、根式、上下标、矩阵、求和与积分夹具）、恶意 DOCTYPE/未知标签拒收，以及公式朗读拦截逻辑。
- Android `:app:testDebugUnitTest`（136 项，0 失败、0 跳过）、`:app:testReleaseUnitTest`（136 项，0 失败、0 跳过）与 `:app:lintDebug`／`:app:lintRelease`（0 错误）全部通过；新增 [`MathUiTest`](../Android/app/src/test/java/com/xingyiyao/mathinterviewvocab/ui/MathUiTest.kt) 使用 Robolectric 覆盖打包 `assets/math/` 加载与 SHA-256 篡改拒绝、全部 44 个公式词条在深浅主题与 100%／200% `fontScale` 下的渲染、超宽维数公式 `\dim(U+W)=\dim U+\dim W-\dim(U\cap W)` 在阅读区进入独立横向滚动块并在卡片中按容器宽度等比自适应、原生 `MathTextView`／`PronunciationTextView` 系统 `copy` 剪贴板准确写入原始 LaTeX、英文单词与公式选区分离朗读提示，以及缺失资产回退展示 `[公式显示异常：...]`。
- 构建与 APK 核验：使用仓库外长期正式密钥签名编译 `:app:assembleRelease`，通过 [`verify-apk.py`](../Android/scripts/verify-apk.py) 独立核验正式发布证书（`24e92a08b1fe647bb6d1726a6208ad75436c1ac2a391651d58c68c067dce1069`）、`com.xingyiyao.mathinterviewvocab` / `1.3.6` / `versionCode 8`、`326` 词库 / `130` 重点、`2328` 段 Kokoro 音频、`63` 个公式 SVG 与清单摘要、全部 9 份随包许可与来源文件（含 MathJax 4.1.3 Apache-2.0、GUST Font License、LPPL 1.3c 与 New Computer Modern 说明）、原有 3 项权限及 4 个 Universal ABI 既有 `libandroidx.graphics.path.so` 原生库未新增任何依赖或权限。
- 证据边界：Android 1.3.6 的 UI 与交互验证全部基于 Robolectric 单元测试、构建校验与 APK 静态核验；**本轮未进行 Android 实体手机或平板真机验收**，且 Robolectric 图形画布不作为像素级渲染截图证据。

## v1.5.1 离线公式排版验收

2026-11-22：macOS 1.5.1 build 8、Windows 1.5.1（file build 1.5.1.8）。仅修复桌面公式显示，不修改 Android、规范词库、离线音频或学习数据格式。

- Mac 104 项逻辑测试／213 次执行通过，0 失败、0 跳过；Windows 158 项自动化测试、类型检查及生产构建通过。在 1.5.0 的 96／201 和 151 项基线上增加分段、搜索别名、完整矢量资源、原始复制、异常回退和复杂公式夹具检查。
- 44 个词条、45 个字段、63 个不同表达式与原词库逐一绑定，所有 SVG／PDF 均核对 SHA-256；另验证嵌套分式、根式、多字符上下标和矩阵等 6 个测试夹具。修正 MathJax 默认将一个行内表达式拆成多个 SVG 导致的截断；生成阶段要求每式恰好一个完整 SVG，积分宽度另有回归断言。
- Mac 原生 NSTextView 与矢量 PDF 混排实际检查 H₀、H₁／Hₐ、p 值、p 级数、完整积分、转置、逆矩阵及维数公式；侧栏展开与收起均检查。进程禁止网络的隔离 DEBUG 应用冷启动正常，实际选择 `unbounded` 后点击朗读、美式选词和英式完整词组均显示内置 Kokoro 来源；切词后旧选区清除。没有修改电脑的网络设置或真实学习数据。
- Mac 真实 TextKit 布局覆盖 1000／1220／1440 pt 对应阅读列、两种侧栏占宽及 16／32 pt 字号；超宽矢量保留自然尺寸并使用局部 NSScrollView，矩阵不被缩小。复制通过原生 `copy` 方法写入独立测试 Pasteboard，单公式及中英文混合段落准确保留原 LaTeX，不触碰系统常用剪贴板。
- Windows 同源 Electron 页面实际渲染全部 44 个公式词条与 63 种公式；单公式、整段标题和释义的 ClipboardEvent 复制与原始 LaTeX 完全相同。验证公式选区不触发箭头切词，`unbounded` 可进入离线查找、公式源码不进入音频查找。1000／1220／1440 宽度、两种侧栏状态、释义 200% 字号和超宽公式横向滚动通过；四题检测的随机屏幕选项保持不变，每题仅记录一次。
- 9 组 Electron 键盘事件、2 组更新界面流程通过，包括后台更新进度、暂停／恢复、切页和关闭设置后继续更新。上述页面测试运行于 macOS 隔离窗口，不冒充实体 Windows 设备；公式学习测试阻止所有 HTTP／HTTPS，实际联网尝试为 0。
- [Windows 官方 runner 验收](https://github.com/xyy386/MathInterviewVocab/actions/runs/45006177539)（源码 `ec70b1e44b11c171e9ed10056696b7c05918bc11`，Windows NT 10.0.26100.0）通过 158 项测试及 11 项原生安装场景：首装、异常交接／目标／仍运行进程拒绝、退出超时、覆盖升级、自动重启和升级／卸载后的数据保留。升级使用不公开的 1.5.2 夹具，不访问正式签名私钥，不把托管 runner 描述为实体硬件。
- 最终 Mac Universal（arm64／x86_64）、最低 macOS 14、宿主及原版 Sparkle 严格签名、Windows AMD64／1.5.1.8、ZIP 完整性、两端 326／130 词库、2328 段音频逐文件字节数和 SHA-256、126 个公式 SVG／PDF 摘要及字体许可均核对通过。规范词表从审核 Markdown 重新解析后字节完全相同，未改写源文件。
- 旧 Windows 更新清单、Mac 签名 appcast 及两平台新索引均沿用既有密钥验证通过。最终源码标签 `v1.5.1` 固定于 `573e9e44dc6fe713405c6bc00222a6ac56b13a75`；与该提交对应的[第二轮官方 Windows runner](https://github.com/xyy386/MathInterviewVocab/actions/runs/45007351489) 同样通过 158 项测试及 11 项原生安装场景。
- 发布按草稿上传、11 个附件完整回读、正式公开、匿名完整回读、最后激活更新源的顺序完成。所有字节数和 SHA-256 与本地成品一致；原 11 个 Release、36 个历史标签引用及 Android 1.3.5 Latest 保持不变，不覆盖历史成品。归档摘要见 [版本索引](VERSION-ARCHIVE.md#v151-桌面附件)。
- 两平台公开静态索引及 Mac 公共 appcast 与发布附件逐字节一致。实际联网验证旧 Windows 1.4.0 流程、新索引的 1.4.0／1.5.0 客户端能独立发现桌面 1.5.1，1.5.1 客户端报告当前版；Mac 新索引及 appcast 签名有效。此项只读取元数据，不执行公开安装器或触碰用户学习记录。

使用现有浅色学术卡片、字体层级与导航，没有新增数学 CDN、远程字体、网页运行环境、同步或启动自动检查。资源随源码提供，Mac 日常构建不要求 Node.js。公式及字形来源和许可见 [公式来源](../Resources/Math/SOURCES.md)。

本次未使用 PD，未进行实体 Intel Mac／Windows 新硬件、全屏、全部显示缩放或任意故障的全覆盖人工验收。Mac 原有 NSTableView 与系统服务日志警告仍保留，不宣称日志完全无警告。历史 Release、标签及 Android Latest 在公开前后分别核对。

## v1.5.0 多线路、持久断点与后台进度验收

2026-11-22：macOS 1.5.0 build 7、Windows 1.5.0（file build 1.5.0.7）；不修改 Android、词库、音频、快捷键或学习数据格式，保留 Android Latest。

- Mac 全套 96 项逻辑测试／201 次执行通过，0 失败、0 跳过。新增签名索引、错误平台／字段、三模式 URL 白名单、对冲及慢请求取消、旧索引拒绝、严格 Range、200 安全重下、断流跨线、缓存篡改、退出后的离线恢复、完整哈希和隔离标记等检查；三个宽度的原生进度组件渲染测试通过。
- 独立 DEBUG Bundle ID 与数据目录实际完成 Sparkle 原生流程：在 43,253,760 字节处取消后退出、重新打开显示原进度且未请求网络；手动继续以对应 Range 获取 206，完成签名验证、替换与自动重启。旧 PID 消失后，在未再次手动启动前观察到新 PID 和 1.5.0 build 7，原收藏、错题、检测偏好保持一致。
- 索引摘要有效但 Sparkle appcast 自身签名被篡改的负例，被原生窗口明确拒绝；不进入安装。最终源码另复测应用内暂停、可更改线路、继续入口与升级后的线路偏好显示，修复原生会话结束未触发界面刷新的问题。未使用私有 API、通用文件代理或修改后的 Sparkle 框架。
- Mac 下载期间实际搜索 `unbounded function`、选择 `unbounded` 并点击朗读，使用 A 作答、Enter 继续和 Command+3 切换学习记录，下载进度持续增加、一次答题只记录一次，最后暂停成功；只写入隔离夹具数据。原生组件在 1000／1220／1440 pt 宽度无超出，原有键盘、选词和离线资源回归通过。Windows Electron 实际页面事件覆盖三个宽度及两种侧栏状态、线路忙碌保护、速度、设置关闭后进度、暂停恢复。上述页面／组件测试不等于所有系统版本、全屏和显示缩放的人工验收。
- Windows 151 项测试、类型检查、生产构建、9 组键盘事件和 2 组更新界面流程通过。新下载器覆盖线路竞速、取消、白名单、陈旧索引、无效 Range、忽略 Range、断流／低速换线、持久暂停恢复、完整哈希、标记失败、安装前篡改和旧缓存替换确认。缓存错误不触碰学习记录。
- [Windows 最终官方 runner 验收](https://github.com/xyy386/MathInterviewVocab/actions/runs/44981409540)，固定源码 `37a8a4d13c3796b6c0b83da6a6e5631d8fc10604`，Windows NT 10.0.26100.0：151 项测试及 11 项原生首装、异常交接拒绝、覆盖安装、自动重启与数据保留场景通过。使用未发布 1.5.1 测试升级包，不公开测试安装包，不读取正式签名密钥；此前候选 [44978902234](https://github.com/xyy386/MathInterviewVocab/actions/runs/44978902234) 同样通过。托管 runner 不是实体硬件验收，不使用 PD。
- 两平台新索引沿用现有 Ed25519 密钥，分别绑定 Mac ZIP／不可变 appcast 和 Windows Setup／旧清单／签名。保留原公共 appcast 和旧 Windows 清单，1.4.0 可沿用原更新流程；不将旧客户端描述为已具有新断点能力。
- 最终成品检查包含 Universal arm64／x86_64、macOS 14 最低版本、宿主及原版 Sparkle 严格签名、AMD64 Windows EXE 版本、ZIP 完整性、326／130 词库、2328 段音频逐文件字节数和摘要，以及两平台索引与旧清单签名。

发布顺序为草稿上传、完整回读、公开匿名回读，再启用原样签名索引与公共 appcast；不覆盖历史成品或标签。摘要见 [版本索引](VERSION-ARCHIVE.md)。未进行实体 Intel Mac／Windows 新硬件、系统磁盘耗尽、强制断电或任意安装故障的全覆盖验收，不宣称可自动回滚。原生 Mac 的 NSTableView 与系统服务日志警告不影响已通过场景，但不宣称日志完全无警告。

发布后复核完成：11 个公开附件与本地 SHA-256、字节数一致，原 10 个 Release 和 34 个标签引用未变，Android 1.3.5 保持 Latest。两平台静态索引和 Mac 公共 appcast 与附件字节完全相同；实际联网检查确认旧 Windows 1.4.0 流程可发现 1.5.0，新索引也能独立发现桌面版，1.5.0 报告当前正式版。Mac 公共 appcast 和新索引签名验证通过。此项只检查元数据，不执行公开下载的安装器或接触用户学习记录。

## v1.4.0 桌面安装与一键更新验收

2026-11-18 至 2026-11-19：macOS 1.4.0 build 6、Windows 1.4.0（file build 1.4.0.6）。Android 1.3.4 code 6 及其 Latest 标记保留，不重建或替换 APK。

- Mac 全套 75 项逻辑测试／168 次执行通过，0 失败、0 跳过，包含原生窗口键盘事件、更新配置、递增版本与保存门禁。Release 不接受 DEBUG 测试目录或回环更新源覆盖。
- Mac 使用独立 Bundle ID、独立学习记录与签名测试源，实际完成 1.3.9 build 5 → 1.4.0 build 6 的 Sparkle 下载、替换和自动重启；取消保留旧版，篡改更新源被拒绝，升级前后收藏、错题和记录内容一致。测试构建不公开为正式版，也不操作真实学习数据。
- Windows 124 项测试、类型检查、生产构建、9 组 Electron 键盘事件及更新界面事件验收通过。Electron 事件在 macOS 隔离窗口执行，不是 Windows 硬件测试；分平台查找另覆盖跳过 Android／草稿／预发布、数值版本排序、分页、请求上限与拒绝降级。
- [原生 Windows 最终工作流](https://github.com/xyy386/MathInterviewVocab/actions/runs/44447458879) 在 GitHub 官方 Windows 2025 runner（Windows NT 10.0.26100.0）运行，源码检查点为 `0188a6e1d694c8b88f9de21b85ef0e3fd6e4a315`。原生逻辑测试与 11 项安装链路通过：首装向导、缺失父进程、非法交接路径、仍在运行的应用、机器级安装、自定义安装目录、已有文件保留、父进程退出超时、确认覆盖升级、自动重启与记录／设置保留、卸载后数据保留。
- 原生升级采用一次性未发布 1.4.1 测试包，不改正式版本、用户电脑或签名私钥。该托管 runner 不是实体 Windows 设备；不使用 PD，不宣称完整物理键盘、全部 Windows 版本、显示缩放、音频设备或断电故障场景均已验证。
- 最终 Mac 为 Universal arm64／x86_64、最低 macOS 14，宿主 ad-hoc 与内嵌原版 Sparkle 框架严格签名检查通过；Windows 主程序为 AMD64，Setup 压缩完整性与便携 ZIP 内容一致性通过。两端 326 条词汇、130 条重点、2328 段音频的字节数、SHA-256、索引与许可均通过核对。
- Mac 的 appcast 与归档、Windows 的精确更新清单分别验证 Ed25519 签名；安装前再次验证 Windows 可执行文件。未商业签名／未 Apple 公证的限制仍存在，保留系统安全防护，不以更新签名代替系统发布者认证。
- 发布采用独立桌面正式版，不修改 Android Latest；附件先上传草稿并回读，公开后匿名完整回读字节数和 SHA-256，再启用完全相同的 Mac 签名源。历史 8 个 Release 和 28 个标签引用在发布前保护检查中保持不变。最终公开摘要见 [版本归档索引](VERSION-ARCHIVE.md)。

1.3.0 及更早客户端仍需手动安装 1.4.0 这一次；旧客户端不可能仅因 GitHub 内容改变而获得安装能力。Mac 初载的既有 NSTableView 警告仍保留，不宣称日志完全无警告。详情见 [安装与更新维护说明](DESKTOP-UPDATES.md)。

## v1.3.0 桌面快捷键验收

2026-10-31：Windows 1.3.0（file build 1.3.0.5）、macOS 1.3.0 build 5；Android 保留原 1.2.0 code 1，不重建 APK。

- macOS 63 项逻辑测试、133 次含参数化执行通过，0 失败、0 跳过；其中 7 项实际原生窗口事件覆盖按键、窗口与控件保护、真实词汇列表和十题检测流程。
- Windows 112 项测试、类型检查、生产构建及 9 组隔离 Electron 页面事件检查通过；测试运行于 macOS，不冒充 Windows 实机测试，不使用 PD、虚拟机或模拟器。
- Universal Release 同时编译 arm64／x86_64，最低 macOS 14，ad-hoc 签名严格校验通过；Windows EXE 为 x64、版本为 1.3.0，ZIP 包含完整配套运行文件。
- 两端 ZIP 完整性、规范词库 326／130、2328 段离线音频及原许可检查通过；不修改学习记录、设置格式或位置。
- 下载附件只包含两个桌面 ZIP 与 `SHA256SUMS.txt`；先草稿回读，再公开为正式 Latest 并匿名回读 SHA-256，不覆盖历史附件或标签。摘要见 [版本归档索引](VERSION-ARCHIVE.md)。
- macOS 27.2／Xcode 27.2 初载仍有 NSTableView 委托重入警告，原因尚无旧版对照确定；没有按键重复移动、崩溃或验收失败。完整边界见 [快捷键验收](KEYBOARD-SHORTCUTS.md)。

## v1.2.0 软件更新验收

v1.2.0 发布时两平台版本统一为 1.2.0：Mac build 4、Windows file build 1.2.0.4。2026-10-30 复核范围如下，历史记录保留在后文。

- Mac 20 项既有 XCTest 及 26 项 Swift Testing 测试声明（含参数化用例）通过；Windows 105 项测试、TypeScript 类型检查及生产构建通过。
- 两端测试覆盖数字版本比较、拒绝预发布／降级、官方仓库及资产名称绑定、重复／缺失附件、HTTPS 主机与重定向边界、文件大小上限、校验和格式与 SHA-256、失败和取消清理、无隐式安装及不启动自动联网。
- Mac 下载采用有界流式写盘；实际生产传输代码对 220 字节响应在 1 字节上限时拒绝，在足够上限时完成；跨非白名单主机的重定向在跳转前被拒绝。公开的旧 Mac ZIP（48,001,244 字节）通过真实流式下载及既有 SHA-256 校验，不只依赖伪造响应测试。
- Mac quarantine 来源标记在临时文件上原生写入并读取验证，原文件字节不变、已有标记不覆盖；写标记失败不进入完成状态。Windows Internet Zone 3 标记包含格式、调用顺序、失败撤回及非 Windows 不写的自动化测试；本轮没有 Windows NTFS 原生实机验证。
- Mac 原生设置与 macOS 上运行的同源 Electron 设置，已实际检查“软件更新”顺序、1.2.0 当前版本、按钮禁用／恢复、联网检查完成及窗口排版。跨平台预览不是 Windows 实机验证；没有启动 PD 或 Windows 虚拟机。
- 词库重新生成后与原 JSON 无差异，仍为 326 条、130 条重点；共用 2328 个 Kokoro 音频和学习数据格式不变。

最终发布还需检查 Universal／x64 架构、签名、ZIP 完整性，并从公开 Release 链接重新下载对照 `SHA256SUMS.txt`。不把来源标记或 SHA-256 称作独立代码签名或发布者认证。

## 证据边界

Windows 实机运行已由用户验证。这是使用者提供的实机反馈，不等同于维护者已测遍全部 Windows 版本、CPU、显示缩放与音频设备。

Mac 的既有验证记录包含 25 项自动化测试和原生窗口检查；Windows 项目包含 68 项自动化测试，以及在 macOS 上运行同一 Electron 代码的界面流程检查。自动化测试、跨平台界面预览和用户 Windows 实机反馈分别说明，不相互替代。

2026-10-29 的 `v1.1.1` 发布准备期间重新执行了 Windows 68 项测试、TypeScript 类型检查、生产构建及 Mac 工程编译，均通过。该次发布过程未启动 Windows 虚拟机，不把交叉构建当作新的 Windows 实机验证。

`v1.1.2` 仅更新 Mac 应用至 1.1.2 build 3，Windows 沿用 `v1.1.1` 发布中的原有 ZIP。上述旧版测试结果不自动视为新 Mac 离线实现的验收结果；新增检查范围如下。

## v1.1.2 离线发音验收范围

2026-10-29 已完成以下本机复核：

- Mac 20 项既有 XCTest 和 10 项 Swift Testing 测试全部通过，新增测试含参数化用例；Windows 68 项测试、类型检查和生产构建也通过。
- 在明确禁止网络访问的进程沙箱中，实际播放 `unbounded`、`unbounded function` 的美式／英式音频及三档语速，共 12 组全部正常结束；不修改电脑的网络设置。
- 最终 Universal Release 已实际打开并读取详情无障碍信息；验证完整词组、单词选择、右键朗读、英美切换、缺失片段提示、独立设置和侧栏显隐。测试后恢复原发音偏好，既有学习记录文件未改变。
- 详情的可复制文本使用独立无障碍表示，已验证不再触发标签解析递归，原有文字选择与复制保留。
- 两个架构均完成编译；本轮运行验证在 Apple Silicon Mac 上进行，不冒充 Intel Mac 实机验证，也未运行 Windows 虚拟机。

发布前须复核以下项目，并以实际测试和打包日志为准：

- Mac 包内 2328 个 MP3、索引和许可齐全，音频字节长度及 SHA-256 与 Windows 规范资源一致；不引入第二份不同音源。
- 所有词条的完整 `speechText`、所选完整单词和索引内词组选区可分别匹配美式、英式资源；首尾标点、空白、Unicode 撇号与连字符规范化后匹配正确。
- 索引外的任意字母片段明确提示缺少音频；不请求网络、不使用 Google 端点、不回退为系统语音或另一口音。
- 逐词朗读、切换词条停止播放、两种口音及 0.75× / 1× / 1.25× 语速可用，设置与本地学习记录保持兼容。
- Mac Release 同时包含 `arm64`、`x86_64`，最低 macOS 14，ad-hoc 签名及 ZIP 完整性有效，包内无 `__MACOSX` 或网盘临时文件。
- Windows ZIP 的字节与既有正式附件相同；不将此次 Mac 修改描述成新的 Windows 实机测试。
- 新附件上传后从公开链接重新下载，对比两平台 ZIP 与 `SHA256SUMS.txt`，保留 `v1.1.1` 历史发布。

## 发布前复核

- 词库为 326 条，稳定编号连续，重点为 130 条，五科顺序不变；审核文本生成的 JSON 与当前词库一致。
- Windows 测试覆盖检测范围、实际题量、四个不同选项、错题／掌握／收藏状态、旧数据兼容、选区清理和本地持久化。
- 2328 个离线 MP3 均有规范索引、字节长度和 SHA-256；全部词条及完整单词有美式、英式音频，缺失资源不自动联网。
- 词汇、检测、反馈、总结、学习记录及独立设置的主要结构符合 Mac 参照。SSR／源码契约检查不冒充真实 Windows 窗口测试。
- Windows EXE 为 AMD64（x64）；Mac 可执行程序同时包含 `arm64` 和 `x86_64`，采用 ad-hoc 签名。
- ZIP 包含项目许可、音源声明与使用说明，Mac 包排除 `__MACOSX` 元数据；`v1.1.1` 只重新封装而不改动程序，`v1.1.2` 重新构建 Mac 离线发音程序，Windows 成品不改动。
- 发布验收要求：附件通过 ZIP 完整性及 SHA-256 校验，并在上传后重新下载比对。
- 源码提交不包含依赖目录、构建产物、个人学习记录、账号凭据、历史截图或内部验收资料。

## 可复核命令

Mac 项目测试，在仓库根目录执行：

```sh
xcodebuild -project MathInterviewVocab.xcodeproj \
  -scheme MathInterviewVocab -destination 'platform=macOS' test
```

Windows 项目测试与构建：

```sh
cd Windows
npm ci
npm test
npm run typecheck
npm run build
```

下载校验文件中的名称对应本次 Release 附件。在两个 ZIP 所在目录，macOS 可执行 `shasum -a 256`，Windows PowerShell 可执行 `Get-FileHash -Algorithm SHA256`，与 `SHA256SUMS.txt` 比对。

## 已知限制

- Mac 没有 Developer ID 签名或 Apple 公证，Windows 没有商业代码签名；校验和用于核对下载完整性，不代替操作系统的发布者信任判断。
- 不承诺系统字体和原生控件跨平台逐像素相同。
- 两平台仅保证内置音频索引中的选区，任意字母片段可能没有离线片段；缺失时不自动联网或切换系统语音。
- 不声明全部 Windows 系统版本、显示缩放、声卡及扬声器组合都已完成验收。
