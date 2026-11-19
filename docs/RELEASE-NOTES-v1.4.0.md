# 数学面试词汇 1.4.0 · 桌面安装与一键更新

macOS **1.4.0 build 6**，Windows **1.4.0 / file build 1.4.0.6**。Android 保留独立发布的 [1.3.4](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.3.4)，本 Release 不重建或替换 Android APK。

本版是独立桌面正式版，不标记为预发布；仓库的 Latest 标记继续保留给 Android 1.3.4，避免影响现有安卓客户端。Mac 使用独立更新源，Windows 从正式发布列表查找本平台版本。

## 下载与首次升级

- [Windows x64 安装版 Setup](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.4.0/MathInterviewVocab-Windows-x64-Setup-v1.4.0.exe)：推荐，正常退出旧便携版后按向导安装到当前用户目录。
- [macOS Universal ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.4.0/MathInterviewVocab-macOS-universal-v1.4.0.zip)：macOS 14+，Apple Silicon／Intel，解压后将 `.app` 放入本地应用程序目录。
- [Windows x64 便携 ZIP](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.4.0/MathInterviewVocab-Windows-x64-v1.4.0.zip)：保留免安装入口；完整解压，不单独移动 EXE。
- [SHA-256 校验和](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.4.0/SHA256SUMS.txt)。不要把 GitHub 自动生成的 Source code 当作安装包。

**1.3.0 及更早桌面版仍需手动升级这一次。** 旧客户端没有安装器，不能仅靠修改 GitHub 获得自动替换能力。安装 1.4.0 后，后续更新无需手动退出、解压和覆盖。

## 改进

- Mac 使用 Sparkle 2.10.1 原生更新窗口，主动检查、下载，确认后安装并重新启动；设置和菜单可唤起正在使用的更新窗口。
- Windows 增加当前用户安装向导；设置中下载完成后可“安装并重启”。安装器先确认持有旧进程句柄，等待正常退出，再替换和重新打开，不强制终止程序。
- 更新前检查记录和设置是否安全保存；保存失败、签名错误、损坏安装包、启动失败或交接超时均停止操作，不以应用退出冒充安装成功。
- Windows 正确处理临时目录的大小写／8.3 别名及目录联接，保留执行前的规范路径与 SHA-256 校验。
- 保留 326 条词汇、130 条重点、2328 段 Kokoro 离线音频、英美 IPA、逐词发音、检测及全部桌面快捷键。学习记录和发音设置的格式与位置不变。

## 安全和联网

Mac 验证固定公钥签名的 appcast 和更新归档；Windows 验证 Ed25519 签名清单及安装包大小、SHA-256，执行前再次验证。发布私钥不进入源码、安装包或 GitHub Actions。

只在用户主动检查、下载时联网，不启动后台更新、不未经确认安装、不上传学习记录。学习和发音继续完全离线。Mac 许可在 `.app/Contents/Resources/Notices`，Windows 许可随安装目录和便携包提供。

当前 Mac 仍采用 ad-hoc 签名，未 Developer ID 签名或 Apple 公证；Windows 没有商业代码签名。更新签名不等于系统对开发者身份的认证，首次打开仍可能出现系统安全提示。不要关闭 Gatekeeper、SmartScreen、Defender 或全局安全防护。

不承诺断电、磁盘故障、安装后启动崩溃等任意场景的自动回滚。安装失败可从官方 Release 重新运行安装包修复，不要卸载或清空学习数据；旧便携目录不由安装器自动删除。

## 验收范围

- Mac：75 项测试／168 次执行通过，含原生窗口键盘事件与更新保存门禁；独立 Bundle ID 和记录目录实际验证签名更新、取消、篡改源拒绝、替换、自动重启及记录保留。
- Windows：124 项逻辑测试、类型检查、生产构建、9 组隔离 Electron 键盘事件及更新界面事件验收通过；这些 Electron 事件在 macOS 上运行，不冒充 Windows 硬件测试。
- [原生 Windows 安装与升级工作流](https://github.com/xyy386/MathInterviewVocab/actions/runs/44447458879) 的 11 项安装链路检查通过，包括首装、异常拒绝、覆盖升级、自动重启及升级／卸载后的数据保留；使用 GitHub 官方 Windows runner，不是实体硬件。完整结果见 [发布检查](https://github.com/xyy386/MathInterviewVocab/blob/v1.4.0/docs/RELEASE-CHECKS.md)。
- 不使用 PD；不宣称所有 Windows 版本、硬件、显示缩放或物理键盘均已验证。Mac 日志中既有 NSTableView 初载警告仍保留，不宣称完全无警告。

依赖审查：运行时依赖无已报告告警；构建工具链仍有 glob／minimatch／picomatch 开发依赖告警，只对可信源码和固定构建路径运行工具，未为消除告警而强行降级框架或跨大版本覆盖依赖。详见 [安装与更新维护说明](https://github.com/xyy386/MathInterviewVocab/blob/v1.4.0/docs/DESKTOP-UPDATES.md)。
