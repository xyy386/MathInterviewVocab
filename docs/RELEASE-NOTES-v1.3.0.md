# 数学面试词汇 v1.3.0 · Windows + macOS

桌面版新增统一的键盘学习操作：Windows x64 **1.3.0**、macOS Universal **1.3.0（build 5）**。Android 保持 **1.2.0／code 1**，本次不重建或替换 APK；历史标签、Release 和附件保留不变。

## 新增快捷键

- 词汇页：`←`／`↑` 上一个，`→`／`↓` 下一个。按当前搜索、科目和筛选结果的实际列表顺序移动，首尾不循环；没有选中项时，前向从第一项开始，后向从最后一项开始。
- 收起词汇侧栏后仍可切词；切换词条停止旧发音、清空旧选区并让详情回到顶部，展开的列表显示当前词条。
- 检测：`A/B/C/D` 或 `1/2/3/4` 对应当前屏幕从上到下的选项，支持大小写与数字小键盘。
- 答题后 `Enter`／`→`／`↓` 继续，最后一题进入结果；不会跳过未答题，也不提供回退修改已提交答案。
- 搜索、输入法组合、文本选择、菜单、弹窗和原生控件操作优先；保留 Shift＋方向键选词及原模块切换快捷键。长按不连续触发，快速键鼠混用不会重复计分或多跳一题。

## 升级与兼容

保留原有界面层级、326 条词汇、130 条重点词汇及 2328 段 Kokoro 英美离线音频。学习记录、收藏、错题、已掌握状态及发音设置的位置和格式不变，不增加账号、网络发音或同步。

1.2.0 桌面用户可在“设置 → 软件更新”手动检查，或从本发布页下载：

- `MathInterviewVocab-Windows-x64-v1.3.0.zip`：Windows 10/11，Intel／AMD x64。退出旧版后完整解压并替换整套程序目录，不要只替换 EXE，也不要删除用户数据目录。
- `MathInterviewVocab-macOS-universal-v1.3.0.zip`：macOS 14+，Apple Silicon 与 Intel。退出旧版后解压并替换 `.app`，不要在网盘自动同步目录直接运行。
- `SHA256SUMS.txt`：对应上述两个桌面 ZIP。GitHub 自动生成的 Source code 不是可直接运行的软件。

不静默安装或自动覆盖程序；没有更新入口的更早版本请手动下载。正常替换程序不会清空本地学习记录。

Android 仍从 [v1.2.0 发布页](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.2.0) 下载 [正式 APK](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.2.0/MathInterviewVocab-Android-universal-v1.2.0.apk)，并使用该版本的 [Android 独立校验文件](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.2.0/SHA256SUMS-Android.txt)。1.3.0 没有 Android 附件，Android 内检查更新会提示暂无对应安装包，不会下载桌面文件。

## 验证与信任说明

快捷键实现阶段：macOS 63 项逻辑测试／133 次含参数化执行通过，含 7 项真实原生窗口事件测试；Windows 112 项测试、类型检查、生产构建和 9 组 Electron 页面事件验收通过。Electron 检查运行于 macOS 隔离窗口，不是 Windows 真机验收。此前用户报告的 Windows 实机通过不覆盖本次新增功能；本轮不使用 PD、虚拟机或模拟器，也不宣称所有支持系统、缩放、输入法或硬件组合已测试。

在 macOS 27.2／Xcode 27.2 中，词汇页初载出现 `NSTableView` 委托重入警告，早于测试发键；未见崩溃、重复移动或验收失败，尚无同环境旧版对照来确定原因。完整操作、测试方法与边界见 [桌面快捷键验收](https://github.com/xyy386/MathInterviewVocab/blob/v1.3.0/docs/KEYBOARD-SHORTCUTS.md)。

Mac 使用 ad-hoc 临时签名，未做 Developer ID 签名或 Apple 公证；Windows 未使用商业代码签名证书。请核对项目来源与 SHA-256，并遵循系统正常安全提示，不要关闭 Gatekeeper、SmartScreen 等安全防护。SHA-256 验证完整性，不代替发布者签名。
