# 数学面试词汇 v1.1.2

Mac 发音同步为内置 Kokoro 自然语音。两平台均可离线朗读，无需联网、首次下载或额外安装模型。

## 下载附件

| 文件 | 内容 |
| --- | --- |
| `MathInterviewVocab-macOS-universal-v1.1.2.zip` | macOS 14+ Universal，应用 1.1.2 build 3 |
| `MathInterviewVocab-Windows-x64-v1.1.1.zip` | Windows 10/11 x64，应用 1.1.1；与上一版附件相同 |
| `SHA256SUMS.txt` | 两个 ZIP 的 SHA-256 校验和 |

请下载对应平台 ZIP 并完整解压，不要将 GitHub 自动生成的 Source code 当作应用。完整入口见 [正式发布页](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.1.2)。

## Mac 本次更新

- 内置与 Windows 相同的 2328 个 Kokoro-82M v1.0 音频片段，美式使用 `af_heart`，英式使用 `bf_emma`。
- 移除 Google 在线发音请求及系统语音回退，不再将朗读文本发送给在线服务。
- 保留完整词组、所选完整单词及内置连续词组选区朗读；选择文字本身不会自动播放。
- 保留英美 IPA、两种口音及 0.75× / 1× / 1.25× 语速。索引外的任意字母片段会明确提示音频缺失，不联网或冒充另一口音。
- 保留 326 条词汇、130 条重点、检测、收藏、错题、掌握状态及现有本地学习记录，界面和设置层级不变。
- 修复词条详情可复制文本的无障碍标签解析递归，保留文本选择、复制及英文右键朗读。
- 随 Mac 应用保留音源声明与 Apache 2.0 许可；Kokoro 音频不是 Google 音频，也不捆绑模型权重或 Python 环境。

## 兼容与限制

本次仅更新 Mac；Windows 应用和 ZIP 保持 1.1.1 原有成品，未重新打包或修改。历史 [v1.1.1 发布](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.1.1) 保持不变。

Mac 支持 macOS 14+ 的 Apple Silicon 与 Intel 设备，当前采用 ad-hoc 临时签名，没有 Developer ID 签名或 Apple 公证；Windows 未使用商业代码签名。请核对下载来源及校验和，不要关闭系统全局安全防护。

两平台独立保存学习记录，无账号或云同步；系统字体、原生菜单和对话框仍有平台差异。Windows 实机运行已由用户验证，这不代表本次重新完成了全部 Windows 版本、硬件或缩放测试。公开验收范围见 [发布检查说明](https://github.com/xyy386/MathInterviewVocab/blob/v1.1.2/docs/RELEASE-CHECKS.md)。

项目原创代码按 MIT 发布；第三方音源、组件与参考文章保留原有许可及权利边界。
