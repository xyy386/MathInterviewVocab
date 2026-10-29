# 发布检查说明

## 证据边界

Windows 实机运行已由用户验证。这是使用者提供的实机反馈，不等同于维护者已测遍全部 Windows 版本、CPU、显示缩放与音频设备。

Mac 的既有验证记录包含 25 项自动化测试和原生窗口检查；Windows 项目包含 68 项自动化测试，以及在 macOS 上运行同一 Electron 代码的界面流程检查。自动化测试、跨平台界面预览和用户 Windows 实机反馈分别说明，不相互替代。

2026-10-29 发布准备期间重新执行了 Windows 68 项测试、TypeScript 类型检查、生产构建及 Mac 工程编译，均通过。本次发布过程未启动 Windows 虚拟机，不把交叉构建当作新的 Windows 实机验证。

## 发布前复核

- 词库为 326 条，稳定编号连续，重点为 130 条，五科顺序不变；审核文本生成的 JSON 与当前词库一致。
- Windows 测试覆盖检测范围、实际题量、四个不同选项、错题／掌握／收藏状态、旧数据兼容、选区清理和本地持久化。
- 2328 个离线 MP3 均有规范索引、字节长度和 SHA-256；全部词条及完整单词有美式、英式音频，缺失资源不自动联网。
- 词汇、检测、反馈、总结、学习记录及独立设置的主要结构符合 Mac 参照。SSR／源码契约检查不冒充真实 Windows 窗口测试。
- Windows EXE 为 AMD64（x64）；Mac 可执行程序同时包含 `arm64` 和 `x86_64`，保留现有 ad-hoc 签名。
- ZIP 补充项目许可和使用说明，Mac 包排除 `__MACOSX` 元数据；可执行程序本身不改动。
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
- Mac 的 Google 网页朗读不是稳定的公开 TTS API；不可用时使用系统语音。
- Windows 仅保证内置音频索引中的选区，任意字母片段可能没有离线片段。
- 不声明全部 Windows 系统版本、显示缩放、声卡及扬声器组合都已完成验收。
