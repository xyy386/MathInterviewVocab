# 桌面安装与更新（1.4.0）

1.4.0 增加安装及重启能力；是否已正式发布，以 [GitHub Releases](https://github.com/xyy386/MathInterviewVocab/releases) 为准。Android 不在本轮改动范围内。

## 首次升级

1.3.0 及更早版本仅能下载 ZIP，必须先手动升级一次，无法远程给旧的可执行文件补上安装逻辑。

- Windows：运行 `MathInterviewVocab-Windows-x64-Setup-v1.4.0.exe`，正常退出旧的便携版，按向导安装。默认是当前用户安装，不需要预装 Node.js。不删除旧便携目录；确认新版运行正常后再自行移除旧程序文件，不要删除用户数据。
- macOS：解压 ZIP，退出旧版，把 `.app` 放入本地“应用程序”等可写目录。更新 ZIP 只包含应用；使用说明和许可在 `Contents/Resources/Notices`。

Windows 仍提供同版本便携 ZIP，兼容旧下载入口及免安装使用。便携程序中确认安装更新后，转为固定目录的安装版；不承诺自动清理任意旧解压目录。直接双击 Setup 的首次安装使用向导。

## 以后的更新

两端都必须由用户主动检查、下载并确认安装；不启动自动联网，不上传学习记录。

- macOS：设置中的检查按钮打开 Sparkle 原生窗口，由它处理下载、取消、签名验证、替换和重新启动。
- Windows：设置检查 GitHub 最新正式版，下载本平台 Setup；验证成功后点击“安装并重新启动”。先保存记录并启动等待器，安装器确认就绪后应用才退出。安装器只等待指定进程，不强制终止其他程序。

记录无法保存、签名无效、文件不完整、下载被取消、权限不足或等待旧进程退出超时，都不得误报安装成功。应用退出后若安装器失败，按系统错误提示处理；可以重新运行已验证的安装包。不会为修复安装而卸载或清空记录。

## 兼容与安全

- Bundle ID、Windows App ID 和学习数据位置保持不变，正常覆盖升级保留收藏、错题、检测记录及发音设置。
- macOS 使用 Sparkle 2.10.1，固定更新公钥，验证签名 appcast 和归档；Windows 使用固定 Ed25519 公钥验证精确清单字节，再绑定正式版、文件名、字节数和 SHA-256。下载和执行前均验证，不能仅凭可编辑校验文件执行任意 EXE。
- Windows 清单为 `Windows-update.json`、分离签名为 `Windows-update.json.sig`；正式 Setup、便携 ZIP、macOS ZIP 和元数据的摘要统一放入 `SHA256SUMS.txt`。
- Windows 保留 Internet Zone 标记；macOS 交由 Sparkle 已验证安装流程处理。不得关闭 Gatekeeper、SmartScreen、Defender 或全局安全设置。
- Windows 拒绝 `/allusers` 与 `/D` 自定义安装目录。NSIS 预初始化可能为 `/D` 建立一个空目录，但随后拒绝安装，不写入程序文件，也不删除该目录中的已有内容。
- 当前 Mac 仍是 ad-hoc 签名、未公证；Windows 未商业代码签名。更新认证不是操作系统对开发者身份的认证，不保证系统不会显示安全提示。
- 没有验证过的断电、磁盘故障或部分安装场景不宣称自动回滚；历史正式包保留在 Releases，必要时用原安装器修复，不删除学习目录。

## 发布维护

只采用稳定版依赖。Sparkle、NSIS 和其他第三方组件保留各自许可，不使用未发布框架的安全能力来描述当前实现。

私钥和恢复备份留在仓库外的私有位置，不能提交、上传 CI 或公开为 Release 附件。Mac 签名由项目专用 Keychain 账号读取；Windows 签名脚本使用显式私钥路径。不在命令行中传递私钥内容，也不把密钥打印到日志。

完成两端构建后，使用 `Scripts/prepare_desktop_release.mjs` 生成签名源和校验和：

```sh
WINDOWS_UPDATE_SIGNING_KEY=/private/windows-update-key.pem \
  node Scripts/prepare_desktop_release.mjs \
  release-artifacts/macos-v1.4.0 release-artifacts/windows-v1.4.0 \
  release-artifacts/metadata-v1.4.0 /path/to/Sparkle/bin
```

脚本拒绝覆盖已存在的元数据目录，并验证签名。macOS 公钥必须与构建一致。先上传到草稿、回读验证，正式附件公开后才能将完全一致的签名 `appcast.xml` 放到 `updates/macos/appcast.xml`；XML 一旦签名不可手工改字、格式化或替换链接，修改后必须重签。不得让公开更新源先指向不可下载的草稿附件。

发布前必须有 macOS 实际替换／重启证据及原生 Windows 安装／升级进程证据。GitHub Windows runner 可验证原生链路，但不能称为实体 Windows 硬件或全套交互验收。不使用 PD。

依据：[Sparkle 集成与签名](https://sparkle-project.org/documentation/)、[Sparkle 更新设置](https://sparkle-project.org/documentation/customization/)、[稳定版 NSIS 配置](https://www.electron.build/v26/docs/nsis/)。
