# 数学面试词汇 v1.2.0 · Windows + macOS

两平台应用版本统一为 **1.2.0**：Windows x64 与 macOS Universal（build 4）。历史 Release 保留原版本号和附件。

## 新增软件更新

- 在“设置 → 软件更新”查看当前版本并手动检查 GitHub 最新正式版；不提示降级或预发布版。
- 发现新版本后可查看发布说明，再确认下载当前平台的完整 ZIP。
- 下载支持取消；校验文件大小和 SHA-256 后，才显示完成并允许打开下载位置。
- 为下载 ZIP 保留系统互联网来源标记；无法添加时明确提示改用浏览器下载，不把失败显示为已完成。
- 校验后请退出旧版，解压并替换程序。不会自动安装、执行文件或覆盖正在运行的应用；学习记录和发音设置保留。
- 没有启动时后台检查、账号登录或学习数据上传；仅在你检查／下载更新时联网。词库、检测、IPA 与 Kokoro 英美发音继续离线。

**旧版需先从本发布页手动下载 1.2.0**，然后才能在软件内检查后续版本。Windows 请完整替换程序目录，不要只移动 EXE；macOS 替换 `.app`。

## 内容与系统要求

- 保留 326 条词汇、130 条重点词汇及 2328 个 Kokoro 英美语音片段。
- Windows：Windows 10/11，普通 Intel/AMD 64 位（x64），免安装 ZIP。
- macOS：macOS 14+，Apple Silicon 与 Intel Universal。
- 不增加账号、云同步、分析服务，也不改变学习记录的位置或格式。

## 下载与信任说明

下载 `MathInterviewVocab-Windows-x64-v1.2.0.zip` 或 `MathInterviewVocab-macOS-universal-v1.2.0.zip`；`SHA256SUMS.txt` 对应上述两个附件。GitHub 自动生成的 Source code 不是可运行软件。

Mac 为 ad-hoc 临时签名，未做 Developer ID 签名或 Apple 公证；Windows 未使用商业代码签名证书。SHA-256 只核对下载完整性，不代替发布者签名。请核对项目来源并遵循系统安全提示，不要关闭 Gatekeeper 或 SmartScreen。

此前 Windows 实机运行已由用户验证；本轮未使用 PD 或 Windows 虚拟机，不将交叉打包和自动化测试宣称为新增 Windows 实机验证。详细范围见仓库 `docs/RELEASE-CHECKS.md`。
