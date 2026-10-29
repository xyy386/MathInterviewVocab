# 内容来源与许可边界

## 项目代码

本项目原创代码采用仓库根目录的 [MIT License](../LICENSE)。该许可不替代第三方组件许可，也不将参考文章、第三方模型或声线重新授权为 MIT。

## 词汇资料

词汇整理参考了以下三篇微信文章，并对学科顺序、中英文术语、简短释义、重点标记和学习结构进行了整理：

1. [参考文章一](https://mp.weixin.qq.com/s/2hs2hXfFMdWhfKWPPad4DQ)
2. [参考文章二](https://mp.weixin.qq.com/s/gcxZbmaZrf9lwvZrRfEpcg)
3. [参考文章三](https://mp.weixin.qq.com/s/xtoRjpFrxWvVL9J-X-7n2A)

仓库保存的是审核后的 [词汇汇总](vocabulary.md) 和应用数据，不提供上述文章全文转载。原文章及其作者享有的权利不因本项目发布而改变；项目 MIT 许可不能被理解为对原文章内容另行授予复制、修改或再分发权利。

IPA 为学习辅助资料，包含构建辅助工具生成的草稿与数学专名、记号的复核覆盖；不宣称是某一商业词典的原样转录，也不代替目标院校的官方课程译名或专业词典。

## Windows 离线音频

Windows 内置音频由 Kokoro-82M v1.0 提前生成，美式使用 `af_heart`，英式使用 `bf_emma`。模型与推理代码的来源、固定 revision、模型／声线哈希及生成参数见 [AUDIO-SOURCES](../Windows/resources/AUDIO-SOURCES.md)。

保留随仓库及成品分发的 [Apache License 2.0 全文](../Windows/resources/KOKORO-APACHE-2.0.txt)。不捆绑模型权重、Python 环境或 Google 语音模型，不暗示 Google 或 Kokoro 作者为本应用提供背书。

## 软件依赖与字体

Windows 使用 Electron、Chromium、React 等组件，保留各自许可与版权声明，见 [第三方说明](../Windows/THIRD-PARTY-NOTICES.txt) 及发行包中的 Electron／Chromium 许可文件。

Mac 使用系统框架和本机字体，Windows 使用本机字体与自绘图标。源码中出现系统字体名称不代表分发对应字体文件；项目不捆绑 Apple 专有字体或 SF Symbols 字体资源。
