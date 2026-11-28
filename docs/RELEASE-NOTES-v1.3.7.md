# 数学面试词汇 v1.3.7 · Android 学习筛选、闪卡流畅度与防误触撤销优化

本次更新将 Android 原生版升级至 **1.3.7（versionCode 9）**，聚焦学习记录精准筛选跳转、界面偏好持久化、闪卡与检测设置顺滑交互、手势防误触锁定以及错题/收藏滑动移出后的撤销提示；桌面端 [`v1.5.1`](https://github.com/xyy386/MathInterviewVocab/releases/tag/v1.5.1) 及历史发布保持不变。

## Android 1.3.7（versionCode 9）更新内容

1. **学习记录跳转精准筛选与分布图可点选**：修复点击学习记录中“已检测词汇”“已掌握”跳转后显示未筛选全量词表的问题；“学习分布”四项图例（已掌握、学习中、待复习错题、未检测）全部支持点击直达对应词条列表，并可与重点/收藏、科目和搜索条件组合筛选。
2. **界面偏好冷启动持久化**：深色模式、科目展示顺序与检测模式（四选一/闪卡）持久化保存于独立的应用私有 `SharedPreferences`（与 `progress.json` / `settings.json` 隔离），重启应用后自动恢复。
3. **闪卡流程简化与快捷收藏**：闪卡自评（认识/模糊/忘了）保存后直接进入下一张卡片或检测总结，减少多余确认步骤；闪卡背面新增“收藏 / 已收藏”按钮；检测设置中的“包含已掌握词汇”支持整行点击切换。
4. **跨页词条详情返回闭环**：从“学习记录”或“检测总结”点开词条详情后按返回，回到原来的标签页，不再跳回“词汇”页。
5. **词表筛选态精简与统计修正**：有搜索词或筛选条件激活时隐藏顶部学习概览卡；“已浏览”计数按当前可见词条计算；错题筛选状态下支持右滑移出；设置页重置学习进度时同步清理失效状态筛选。
6. **手势防误触与滑动撤销提示**：修复词汇卡片上下滑动列表时容易误触左右滑动的问题（采用角度锁定手势，仅水平右滑且超过卡片宽度 35% 才触发移出，竖滑、斜滑与左滑交由列表滚动处理；滑块控件同步改用水平拖拽手势）；右滑移出错题或取消收藏后，顶部弹出可手动关闭的 5 秒撤销横幅，支持一键恢复。

## 下载与升级

- **Android Universal APK**：[MathInterviewVocab-Android-universal-v1.3.7.apk](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.3.7/MathInterviewVocab-Android-universal-v1.3.7.apk)（`100,917,084` 字节，SHA-256：`3402b61e5199fcf194797772901014ca46620a975fcfb6b3cea0685fc5215ce1`）
- **Android 独立校验文件**：[SHA256SUMS-Android.txt](https://github.com/xyy386/MathInterviewVocab/releases/download/v1.3.7/SHA256SUMS-Android.txt)（`114` 字节，SHA-256：`0ec17186ac13824cfbf67e0304fad1293bb0a16c641ab59a362ba77ed11c7e09`）
- **公开签名证书 SHA-256**：`24e92a08b1fe647bb6d1726a6208ad75436c1ac2a391651d58c68c067dce1069`

已安装旧版（`1.2.0`–`1.3.6`）的 Android 设备可在应用内打开 **设置 → 软件更新 → 检查更新** 升级至 `1.3.7`（`versionCode 9`），或从上方链接下载 APK 直接覆盖安装，本机学习记录自动保留。

## 验证与源码

- [Android 固定源码标签](https://github.com/xyy386/MathInterviewVocab/tree/android-v1.3.7) 与同名 [`v1.3.7` 标签](https://github.com/xyy386/MathInterviewVocab/tree/v1.3.7) 均指向本次发布源码提交；发布直接使用仓库外长期正式密钥签名编译并通过独立校验的 APK，不更换签名证书。
- 本地自动化校验结果：`logic-tests` 全套 102 项 JVM 测试全部通过，`:app:testDebugUnitTest`（141 项）与 `:app:testReleaseUnitTest`（141 项）全部通过，0 失败、0 跳过；Debug／Release Lint 均为 0 错误（26 条既有非阻断建议）。`verify-apk.py` 独立核对正式签名证书、包名与版本 `1.3.7`（`versionCode 9`）、API 29／36、326／130 词库、2328 段离线音频、63 个离线公式 SVG、9 份许可、原有 3 项权限与 4 个 Universal ABI 全部通过。
- 证据边界：本次 UI、筛选、手势与撤销横幅验证基于 Robolectric 单元测试与 APK 静态核验，**未进行 Android 实体手机或平板真机实测**。
- [公式与字体许可](https://github.com/xyy386/MathInterviewVocab/blob/android-v1.3.7/Resources/Math/SOURCES.md) 与 [离线音源声明](https://github.com/xyy386/MathInterviewVocab/blob/android-v1.3.7/Windows/resources/AUDIO-SOURCES.md) 随 APK 提供。升级时请勿先卸载旧版或清空应用数据，也不要关闭系统安全防护。
