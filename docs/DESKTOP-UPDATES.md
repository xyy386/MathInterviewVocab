# 桌面安装与更新（1.5.0）

1.5.0 在 1.4.0 安装及重启能力上增加多线路下载、持久断点和后台进度；是否已正式发布，以 [GitHub Releases](https://github.com/xyy386/MathInterviewVocab/releases) 为准。Android 不在本轮改动范围内。

## 首次升级

1.3.0 及更早版本仅能下载 ZIP，必须先手动升级一次，无法远程给旧的可执行文件补上安装逻辑。

- Windows：运行 `MathInterviewVocab-Windows-x64-Setup-v1.5.0.exe`，正常退出旧的便携版，按向导安装。默认是当前用户安装，不需要预装 Node.js。不删除旧便携目录；确认新版运行正常后再自行移除旧程序文件，不要删除用户数据。
- macOS：解压 ZIP，退出旧版，把 `.app` 放入本地“应用程序”等可写目录。更新 ZIP 只包含应用；使用说明和许可在 `Contents/Resources/Notices`。

Windows 仍提供同版本便携 ZIP，兼容旧下载入口及免安装使用。便携程序中确认安装更新后，转为固定目录的安装版；不承诺自动清理任意旧解压目录。直接双击 Setup 的首次安装使用向导。

## 以后的更新

两端都必须由用户主动检查、下载并确认安装；不启动自动联网，不上传学习记录。

- macOS：设置中的检查按钮打开 Sparkle 原生窗口。下载器通过仅绑定 `127.0.0.1` 的随机会话交接层提供当前已绑定的 appcast 和 ZIP；Sparkle 继续验证原始签名源、归档并负责替换、重启。只使用 Sparkle 公开接口，不修改框架，不提供目录、任意 URL 或通用代理。
- Windows：设置从独立签名索引查找本平台 Setup，不依赖仓库全局 Latest；验证成功后点击“安装并重启”。先保存记录并启动等待器，安装器确认就绪后应用才退出。安装器只等待指定进程，不强制终止其他程序。

桌面与 Android 分平台发布：桌面 1.5.0 是非预发布的正式版，但不占用 Android 的 Latest 标记。新客户端使用 `updates/macos/latest.json` 和 `updates/windows/latest.json` 及分离 Ed25519 签名，只接受本平台索引。保留原公共 appcast 和旧 Windows 清单，1.4.0 可按原有流程升级；1.4.0 的 Windows 仍在最多四页、每页 50 个正式版本中查找本平台安装器。新加速能力从安装 1.5.0 后生效。

## 线路、暂停和缓存

| 模式 | 行为 |
| --- | --- |
| 智能多线加速（默认） | 官方源优先，1.2 秒未完成或立即失败时加入备用候选 |
| 加速镜像优先 | 镜像优先，400 毫秒后加入其他候选 |
| GitHub 直连 | 不请求任何第三方代理或 CDN |

候选使用 gh-proxy.com、ghfast.top、ghproxy.net；testingcf.jsdelivr.net 和 fastly.jsdelivr.net 仅读取小型签名索引。最多同时探测三条线路，取消未获选请求；已知更高版本不会被旧 CDN 响应覆盖。检查整体上限 45 秒。

安装包一次只从一条线路流式下载。首包等待 6 秒、读取空闲 12 秒；预热 3 秒后持续 8 秒低于 40 KiB/s 且有备用线路则换线。续传严格核对 `206`、完整 `Content-Range`、声明及实际字节数；`200` 不作为续传拼接。Mac 已交给原生下载器的前缀不能撤回，遇到忽略 Range 的后继节点时改试其他节点；新会话可安全从零重下。

- Mac 缓存：`~/Library/Caches/com.xingyiyao.MathInterviewVocab/Updates`。
- Windows 缓存：`%LOCALAPPDATA%\MathInterviewVocab\UpdatesCache`。
- 一个待更新包，上限 500 MiB。任务信息原子写入；更换版本或摘要须确认清除旧断点，缓存与安装目录、学习数据分离。
- 暂停、断网、原生窗口取消下载或退出保留断点。重开应用只读取本地缓存，不自动联网；点击继续重新验证签名、平台、版本及真实文件状态，完成后重新计算完整 SHA-256。
- “清除下载缓存”单独确认，只移除固定更新文件，不删除记录、收藏或设置。操作系统也可能回收用户缓存，届时重新下载即可。
- 关闭设置、切换模块或最小化不停止下载；主窗口进度条提供查看、暂停、继续和就绪后的安装入口。没有退出后常驻下载服务。

线路偏好独立保存，检查、下载、校验和安装期间不可更改；暂停后可选择另一模式再继续。镜像会接收公开更新请求，不发送学习记录、账号令牌或朗读文本。

记录无法保存、签名无效、文件不完整、下载被取消、权限不足或等待旧进程退出超时，都不得误报安装成功。应用退出后若安装器失败，按系统错误提示处理；可以重新运行已验证的安装包。不会为修复安装而卸载或清空记录。

## 兼容与安全

- Bundle ID、Windows App ID 和学习数据位置保持不变，正常覆盖升级保留收藏、错题、检测记录及发音设置。
- macOS 使用 Sparkle 2.10.1，固定更新公钥，验证签名 appcast 和归档；Windows 使用固定 Ed25519 公钥验证精确清单字节，再绑定正式版、文件名、字节数和 SHA-256。独立索引沿用各平台既有密钥，不引入新信任根。下载和执行前均验证，不能仅凭可编辑校验文件执行任意 EXE。
- Windows 清单为 `Windows-update.json`、分离签名为 `Windows-update.json.sig`；正式 Setup、便携 ZIP、macOS ZIP 和元数据的摘要统一放入 `SHA256SUMS.txt`。
- Windows 保留 Internet Zone 标记；macOS 交由 Sparkle 已验证安装流程处理。不得关闭 Gatekeeper、SmartScreen、Defender 或全局安全设置。
- Windows 拒绝 `/allusers` 与 `/D` 自定义安装目录。NSIS 预初始化可能为 `/D` 建立一个空目录，但随后拒绝安装，不写入程序文件，也不删除该目录中的已有内容。
- 当前 Mac 仍是 ad-hoc 签名、未公证；Windows 未商业代码签名。更新认证不是操作系统对开发者身份的认证，不保证系统不会显示安全提示。
- 没有验证过的断电、磁盘故障或部分安装场景不宣称自动回滚；历史正式包保留在 Releases，必要时用原安装器修复，不删除学习目录。

## 发布维护

只采用稳定版依赖。Sparkle、NSIS 和其他第三方组件保留各自许可，不使用未发布框架的安全能力来描述当前实现。

以后每次发版必须同时提高桌面语义版本和 Mac 的 `CFBundleVersion`，同步修改工程、打包脚本与 Windows 元数据；不能只修改 GitHub 标题或附件名称。Mac 同时要求版本与构建号递增，Windows 不允许降级。

私钥和恢复备份留在仓库外的私有位置，不能提交、上传 CI 或公开为 Release 附件。Mac 签名由项目专用 Keychain 账号读取；Windows 签名脚本使用显式私钥路径。不在命令行中传递私钥内容，也不把密钥打印到日志。

完成两端构建后，使用 `Scripts/prepare_desktop_release.mjs` 生成签名源和校验和：

```sh
WINDOWS_UPDATE_SIGNING_KEY=/private/windows-update-key.pem \
  node Scripts/prepare_desktop_release.mjs \
  release-artifacts/macos-v1.5.0 release-artifacts/windows-v1.5.0 \
  release-artifacts/metadata-v1.5.0 /path/to/Sparkle/bin
```

脚本拒绝覆盖已存在的元数据目录，并验证签名。macOS 公钥必须与构建一致。新增 `macOS-update-index.json{,.sig}`、`Windows-update-index.json{,.sig}` 四个附件，Mac 绑定 ZIP 和不可变 Release appcast，Windows 绑定 Setup、旧格式清单和签名；URL 只能由固定仓库、版本与允许的附件名推导。索引不包含校验文件自身摘要，避免循环依赖。

先上传到草稿、完整回读验证，再公开并匿名回读；之后才能把原样签名 appcast 放到 `updates/macos/appcast.xml`，将两平台索引及签名分别放到 `updates/<platform>/latest.json{,.sig}`。签名字节不可手工改字或格式化，修改后必须重签。不得让公开更新源先指向不可下载的草稿附件，发布须显式 `--latest=false` 保留 Android Latest。

发布前必须有 macOS 实际替换／重启证据及原生 Windows 安装／升级进程证据。GitHub Windows runner 可验证原生链路，但不能称为实体 Windows 硬件或全套交互验收。不使用 PD。

依据：[Sparkle 集成与签名](https://sparkle-project.org/documentation/)、[Sparkle 更新设置](https://sparkle-project.org/documentation/customization/)、[稳定版 NSIS 配置](https://www.electron.build/v26/docs/nsis/)。
