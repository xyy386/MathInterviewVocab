# 数学面试词汇 v1.3.1 · Android 交互与发音体验升级

本次更新将 Android 原生版升级至 **1.3.1（versionCode 3）**；Windows 1.3.0 与 macOS 1.3.0（build 5）保持不变，桌面用户继续使用 [v1.3.0 发布页](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.3.0) 的桌面安装包。

## Android 1.3.1（versionCode 3）更新内容

1. **详情页直接切换“上一个 / 下一个”单词**：在单词详情页新增按当前搜索与筛选列表顺序翻页的按钮及 `第 X / Y 个` 位置提示，无需反复退回列表即可连续背词。
2. **返回列表时保留滚动位置并标记“刚刚浏览”**：从详情页返回词汇列表时保持原有滚动位置，并为刚刚浏览的词条提供浅蓝底色与“刚刚浏览”标签；若在详情页连续翻页或跨页跳入详情再返回，列表会自动滚动对齐到最后浏览的词条。
3. **科目分组吸顶（Sticky Header）与“回到顶部”快捷按钮**：列表中各学科标题在滚动时自动吸顶固定；向下滑动后右下角显示“回到顶部”快捷按钮。
4. **自动收起软键盘与清除搜索框焦点**：点击词条、切换科目／筛选或按键盘搜索键时自动收起软键盘并清除输入框焦点。
5. **长词组“单词快捷点读”胶囊（Word Chips）**：多词数学术语提供一键点选并朗读对应单词的胶囊按钮，全部单词均由内置 Kokoro 英美离线音频覆盖。
6. **英文词条原生 `TextView` 跟随系统字体缩放动态刷新**：运行期间调整系统字体大小时自动刷新英文词条字号。
7. **检测答题反馈页新增“收藏 / 取消收藏”按钮**：作答后可在反馈卡片内直接收藏或取消收藏当前词汇。
8. **学习记录页统计指标与学科进度条点击跳转筛选**：点击“已检测词汇”、“已掌握”、“待复习”、“我的收藏”或任意学科掌握进度条，可直接跳转到词汇页并套用对应筛选。
9. **更新下载与安装准备可靠性加固**：切后台仅停止朗读而不中断正在进行的 APK 下载；为取消更新清理绑定操作代次（修复 `P2-01`）；区分安装准备阶段与已提交系统 Session（修复 `P2-02`）。
10. **Android 13+ 主题单色图标支持**：自适应应用图标补充 `<monochrome>` 层，支持系统壁纸主题色图标。

## 下载与升级

- **Android Universal APK**：[MathInterviewVocab-Android-universal-v1.3.1.apk](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.3.1/MathInterviewVocab-Android-universal-v1.3.1.apk)（`100,223,744` 字节，SHA-256：`5adcb09549afafd37f2d14d94254e8e5a19b75d61e11324e7d84c11dac8205c3`）
- **Android 独立校验文件**：[SHA256SUMS-Android.txt](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.3.1/SHA256SUMS-Android.txt)
- **公开签名证书 SHA-256**：`24e92a08b1fe647bb6d1726a6208ad75436c1ac2a391651d58c68c067dce1069`

已安装 Android 1.2.0 或 1.3.0 的设备可在应用内打开 **设置 → 软件更新 → 检查更新** 直接在线检测并升级至 1.3.1（`versionCode 3`），或下载上述 APK 直接覆盖安装，本机学习记录会自动保留。
